package com.banking.auth.service;

import com.banking.auth.dto.request.LoginRequest;
import com.banking.auth.dto.response.AuthResponse;
import com.banking.auth.entity.RefreshToken;
import com.banking.auth.entity.User;
import com.banking.auth.exception.AccountLockedException;
import com.banking.auth.exception.InvalidCredentialsException;
import com.banking.auth.exception.InvalidTokenException;
import com.banking.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        User user = userService.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException());

        if (user.getAccountLocked()) {
            throw new AccountLockedException();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            userService.incrementFailedLoginAttempts(user);
            throw new InvalidCredentialsException();
        }

        userService.recordSuccessfulLogin(user);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenValue)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshTokenExpiration() / 1000))
                .build();

        refreshTokenRepository.save(refreshToken);

        log.info("User logged in successfully: {}", user.getEmail());

        return AuthResponse.of(
                accessToken,
                refreshTokenValue,
                jwtService.getAccessTokenExpiration() / 1000
        );
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        log.info("Token refresh attempt");

        RefreshToken refreshToken = refreshTokenRepository
                .findValidToken(refreshTokenValue, LocalDateTime.now())
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired refresh token"));

        User user = refreshToken.getUser();

        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshTokenValue = UUID.randomUUID().toString();

        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .token(newRefreshTokenValue)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshTokenExpiration() / 1000))
                .build();

        refreshTokenRepository.save(newRefreshToken);

        log.info("Token refreshed successfully for user: {}", user.getEmail());

        return AuthResponse.of(
                newAccessToken,
                newRefreshTokenValue,
                jwtService.getAccessTokenExpiration() / 1000
        );
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        log.info("Logout attempt");

        refreshTokenRepository.findByToken(refreshTokenValue)
                .ifPresent(token -> {
                    token.revoke();
                    refreshTokenRepository.save(token);
                    log.info("User logged out successfully");
                });
    }

    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        refreshTokenRepository.revokeAllByUser(user);
        log.info("All tokens revoked for user: {}", user.getEmail());
    }
}
