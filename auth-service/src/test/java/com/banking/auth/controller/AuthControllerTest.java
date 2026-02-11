package com.banking.auth.controller;

import com.banking.auth.dto.request.LoginRequest;
import com.banking.auth.dto.request.RegisterRequest;
import com.banking.auth.dto.response.AuthResponse;
import com.banking.auth.dto.response.LoginResponse;
import com.banking.auth.dto.response.UserResponse;
import com.banking.auth.exception.InvalidCredentialsException;
import com.banking.auth.exception.UserAlreadyExistsException;
import com.banking.auth.service.AuthService;
import com.banking.auth.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthService authService;

    @Test
    @DisplayName("POST /api/v1/auth/register - Success")
    void testRegister_Success() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("alice@example.com")
                .password("SecureP@ssw0rd")
                .firstName("Alice")
                .lastName("Johnson")
                .build();

        UserResponse response = UserResponse.builder()
                .userId(UUID.randomUUID())
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Johnson")
                .mfaEnabled(false)
                .roles(Set.of("USER"))
                .createdAt(LocalDateTime.now())
                .build();

        when(userService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.firstName").value("Alice"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Duplicate Email")
    void testRegister_DuplicateEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("alice@example.com")
                .password("SecureP@ssw0rd")
                .firstName("Alice")
                .lastName("Johnson")
                .build();

        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new UserAlreadyExistsException("User with email alice@example.com already exists"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("User with email alice@example.com already exists"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - Validation Error")
    void testRegister_ValidationError() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("invalid-email")
                .password("weak")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Success (no MFA)")
    void testLogin_Success() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("SecureP@ssw0rd")
                .build();

        AuthResponse authResponse = AuthResponse.of("access-token", "refresh-token", 900L);
        LoginResponse response = LoginResponse.success(authResponse);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(false))
                .andExpect(jsonPath("$.authResponse.accessToken").value("access-token"))
                .andExpect(jsonPath("$.authResponse.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - MFA Required")
    void testLogin_MfaRequired() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("SecureP@ssw0rd")
                .build();

        LoginResponse response = LoginResponse.requireMfa("mfa-token-123");

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaToken").value("mfa-token-123"))
                .andExpect(jsonPath("$.authResponse").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - Invalid Credentials")
    void testLogin_InvalidCredentials() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("alice@example.com")
                .password("WrongPassword")
                .build();

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }
}
