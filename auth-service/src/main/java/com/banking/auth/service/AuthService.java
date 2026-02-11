package com.banking.auth.service;

import com.banking.auth.dto.request.LoginRequest;
import com.banking.auth.dto.request.MfaVerifyRequest;
import com.banking.auth.dto.response.AuthResponse;
import com.banking.auth.dto.response.LoginResponse;
import com.banking.auth.entity.RefreshToken;
import com.banking.auth.entity.User;
import com.banking.auth.exception.AccountLockedException;
import com.banking.auth.exception.InvalidCredentialsException;
import com.banking.auth.exception.InvalidTokenException;
import com.banking.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String MFA_TOKEN_PREFIX = "mfa:";
    private static final Duration MFA_TOKEN_TTL = Duration.ofMinutes(5);

    private final UserService userService;
    private final JwtService jwtService;
    private final MfaService mfaService;
    private final SessionService sessionService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public LoginResponse login(LoginRequest request) {
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

        if (mfaService.isMfaEnabled(user)) {
            String mfaToken = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(MFA_TOKEN_PREFIX + mfaToken, user.getEmail(), MFA_TOKEN_TTL);
            log.info("MFA required for user: {}", user.getEmail());
            return LoginResponse.requireMfa(mfaToken);
        }

        return LoginResponse.success(completeLogin(user));
    }

    @Transactional
    public AuthResponse verifyMfaAndLogin(MfaVerifyRequest request) {
        log.info("MFA verification attempt");

        String email = redisTemplate.opsForValue().get(MFA_TOKEN_PREFIX + request.getMfaToken());
        if (email == null) {
            throw new InvalidTokenException("MFA token expired or invalid");
        }

        User user = userService.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException());

        boolean codeValid = mfaService.verifyCode(user.getMfaSecret(), request.getCode());
        boolean backupCodeValid = false;
        
        if (!codeValid) {
            backupCodeValid = mfaService.verifyBackupCode(user, request.getCode());
        }

        if (!codeValid && !backupCodeValid) {
            throw new InvalidCredentialsException("Invalid MFA code or backup code");
        }

        if (backupCodeValid) {
            log.info("User {} logged in using backup code", user.getEmail());
        }

        redisTemplate.delete(MFA_TOKEN_PREFIX + request.getMfaToken());

        return completeLogin(user);
    }

    private AuthResponse completeLogin(User user) {
        return completeLogin(user, null, null);
    }

    private AuthResponse completeLogin(User user, String ipAddress, String userAgent) {
        userService.recordSuccessfulLogin(user);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenValue)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshTokenExpiration() / 1000))
                .build();

        refreshTokenRepository.save(refreshToken);

        String sessionId = sessionService.createSession(user.getId(), refreshTokenValue, ipAddress, userAgent);

        log.info("User logged in successfully: {}, sessionId: {}", user.getEmail(), sessionId);

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
