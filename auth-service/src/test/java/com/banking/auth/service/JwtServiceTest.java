package com.banking.auth.service;

import com.banking.auth.entity.Role;
import com.banking.auth.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", 
                "mySecretKeyForDevelopmentOnlyMustBeAtLeast256BitsLongForHS256Algorithm");
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 900000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 604800000L);

        Role userRole = Role.builder()
                .id(UUID.randomUUID())
                .name("USER")
                .build();

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .passwordHash("hashedPassword")
                .roles(roles)
                .build();
    }

    @Test
    @DisplayName("Should generate valid access token")
    void testGenerateAccessToken() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Should generate valid refresh token")
    void testGenerateRefreshToken() {
        String token = jwtService.generateRefreshToken(testUser);

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Should validate token successfully")
    void testIsTokenValid_ValidToken() {
        String token = jwtService.generateAccessToken(testUser);

        boolean isValid = jwtService.isTokenValid(token);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should return false for invalid token")
    void testIsTokenValid_InvalidToken() {
        boolean isValid = jwtService.isTokenValid("invalid.token.here");

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should return false for malformed token")
    void testIsTokenValid_MalformedToken() {
        boolean isValid = jwtService.isTokenValid("not-a-jwt");

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should extract user ID from token")
    void testExtractUserId() {
        String token = jwtService.generateAccessToken(testUser);

        UUID userId = jwtService.extractUserId(token);

        assertThat(userId).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("Should extract email from token")
    void testExtractEmail() {
        String token = jwtService.generateAccessToken(testUser);

        String email = jwtService.extractEmail(token);

        assertThat(email).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("Should return false for non-expired token")
    void testIsTokenExpired_NotExpired() {
        String token = jwtService.generateAccessToken(testUser);

        boolean isExpired = jwtService.isTokenExpired(token);

        assertThat(isExpired).isFalse();
    }

    @Test
    @DisplayName("Should return correct expiration values")
    void testGetExpirationValues() {
        assertThat(jwtService.getAccessTokenExpiration()).isEqualTo(900000L);
        assertThat(jwtService.getRefreshTokenExpiration()).isEqualTo(604800000L);
    }
}
