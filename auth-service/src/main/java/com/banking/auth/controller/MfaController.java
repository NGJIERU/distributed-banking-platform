package com.banking.auth.controller;

import com.banking.auth.dto.request.MfaSetupRequest;
import com.banking.auth.dto.response.MfaSetupResponse;
import com.banking.auth.entity.User;
import com.banking.auth.exception.InvalidCredentialsException;
import com.banking.auth.service.MfaService;
import com.banking.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/mfa")
@RequiredArgsConstructor
@Tag(name = "MFA", description = "Multi-Factor Authentication endpoints")
public class MfaController {

    private final MfaService mfaService;
    private final UserService userService;

    @Operation(summary = "Setup MFA", description = "Generate MFA secret and QR code for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MFA setup initiated"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "400", description = "MFA already enabled")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/setup")
    public ResponseEntity<MfaSetupResponse> setupMfa(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("MFA setup request for user: {}", userDetails.getUsername());

        User user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException());

        if (mfaService.isMfaEnabled(user)) {
            return ResponseEntity.badRequest().build();
        }

        String secret = mfaService.setupMfa(user);
        String qrCodeDataUri = mfaService.generateQrCodeDataUri(secret, user.getEmail());

        return ResponseEntity.ok(MfaSetupResponse.of(secret, qrCodeDataUri));
    }

    @Operation(summary = "Verify and enable MFA", description = "Verify TOTP code and enable MFA for the user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MFA enabled successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "400", description = "Invalid TOTP code or MFA not set up")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyAndEnableMfa(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MfaSetupRequest request) {

        log.info("MFA verification request for user: {}", userDetails.getUsername());

        User user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException());

        if (user.getMfaSecret() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "MFA not set up. Call /setup first."));
        }

        if (!mfaService.verifyCode(user.getMfaSecret(), request.getCode())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid TOTP code"));
        }

        mfaService.enableMfa(user);
        List<String> backupCodes = mfaService.generateAndStoreBackupCodes(user);

        return ResponseEntity.ok(Map.of(
                "message", "MFA enabled successfully",
                "backupCodes", String.join(",", backupCodes),
                "warning", "Save these backup codes securely. They will not be shown again."
        ));
    }

    @Operation(summary = "Disable MFA", description = "Disable MFA for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MFA disabled successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "400", description = "Invalid TOTP code")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/disable")
    public ResponseEntity<Map<String, String>> disableMfa(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MfaSetupRequest request) {

        log.info("MFA disable request for user: {}", userDetails.getUsername());

        User user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException());

        if (!mfaService.isMfaEnabled(user)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "MFA is not enabled"));
        }

        if (!mfaService.verifyCode(user.getMfaSecret(), request.getCode())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid TOTP code"));
        }

        mfaService.disableMfa(user);

        return ResponseEntity.ok(Map.of("message", "MFA disabled successfully"));
    }

    @Operation(summary = "Get MFA status", description = "Check if MFA is enabled for the authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "MFA status retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getMfaStatus(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException());

        return ResponseEntity.ok(Map.of(
                "mfaEnabled", mfaService.isMfaEnabled(user),
                "backupCodesRemaining", mfaService.getRemainingBackupCodes(user)
        ));
    }

    @Operation(summary = "Regenerate backup codes", description = "Generate new backup codes (invalidates old ones)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Backup codes regenerated"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "400", description = "MFA not enabled or invalid TOTP code")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/backup-codes/regenerate")
    public ResponseEntity<Map<String, Object>> regenerateBackupCodes(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MfaSetupRequest request) {

        log.info("Backup codes regeneration request for user: {}", userDetails.getUsername());

        User user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException());

        if (!mfaService.isMfaEnabled(user)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "MFA is not enabled"));
        }

        if (!mfaService.verifyCode(user.getMfaSecret(), request.getCode())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid TOTP code"));
        }

        List<String> backupCodes = mfaService.generateAndStoreBackupCodes(user);

        return ResponseEntity.ok(Map.of(
                "message", "Backup codes regenerated successfully",
                "backupCodes", backupCodes,
                "warning", "Save these backup codes securely. They will not be shown again."
        ));
    }
}
