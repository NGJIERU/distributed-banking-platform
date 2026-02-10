package com.banking.auth.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private User user;
    private Role userRole;

    @BeforeEach
    void setUp() {
        userRole = Role.builder()
                .id(UUID.randomUUID())
                .name("USER")
                .build();

        user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .firstName("Test")
                .lastName("User")
                .build();
    }

    @Test
    @DisplayName("Should add role to user")
    void testAddRole() {
        user.addRole(userRole);

        assertThat(user.getRoles()).contains(userRole);
    }

    @Test
    @DisplayName("Should remove role from user")
    void testRemoveRole() {
        user.addRole(userRole);
        user.removeRole(userRole);

        assertThat(user.getRoles()).doesNotContain(userRole);
    }

    @Test
    @DisplayName("Should increment failed login attempts")
    void testIncrementFailedLoginAttempts() {
        assertThat(user.getFailedLoginAttempts()).isEqualTo(0);

        user.incrementFailedLoginAttempts();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);

        user.incrementFailedLoginAttempts();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should lock account after 5 failed attempts")
    void testAccountLockAfterFailedAttempts() {
        for (int i = 0; i < 5; i++) {
            user.incrementFailedLoginAttempts();
        }

        assertThat(user.getAccountLocked()).isTrue();
        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should reset failed login attempts")
    void testResetFailedLoginAttempts() {
        user.incrementFailedLoginAttempts();
        user.incrementFailedLoginAttempts();
        user.setAccountLocked(true);

        user.resetFailedLoginAttempts();

        assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(user.getAccountLocked()).isFalse();
    }

    @Test
    @DisplayName("Should record successful login")
    void testRecordSuccessfulLogin() {
        user.incrementFailedLoginAttempts();
        user.setAccountLocked(true);

        user.recordSuccessfulLogin();

        assertThat(user.getFailedLoginAttempts()).isEqualTo(0);
        assertThat(user.getAccountLocked()).isFalse();
        assertThat(user.getLastLoginAt()).isNotNull();
    }
}
