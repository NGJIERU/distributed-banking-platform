package com.banking.auth.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    @Test
    @DisplayName("Should return true when token is expired")
    void testIsExpired_True() {
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("test-token")
                .expiresAt(LocalDateTime.now().minusHours(1))
                .revoked(false)
                .build();

        assertThat(token.isExpired()).isTrue();
    }

    @Test
    @DisplayName("Should return false when token is not expired")
    void testIsExpired_False() {
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("test-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .revoked(false)
                .build();

        assertThat(token.isExpired()).isFalse();
    }

    @Test
    @DisplayName("Should return true when token is valid")
    void testIsValid_True() {
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("test-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .revoked(false)
                .build();

        assertThat(token.isValid()).isTrue();
    }

    @Test
    @DisplayName("Should return false when token is revoked")
    void testIsValid_Revoked() {
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("test-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .revoked(true)
                .build();

        assertThat(token.isValid()).isFalse();
    }

    @Test
    @DisplayName("Should return false when token is expired")
    void testIsValid_Expired() {
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("test-token")
                .expiresAt(LocalDateTime.now().minusHours(1))
                .revoked(false)
                .build();

        assertThat(token.isValid()).isFalse();
    }

    @Test
    @DisplayName("Should revoke token")
    void testRevoke() {
        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .token("test-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .revoked(false)
                .build();

        token.revoke();

        assertThat(token.getRevoked()).isTrue();
    }
}
