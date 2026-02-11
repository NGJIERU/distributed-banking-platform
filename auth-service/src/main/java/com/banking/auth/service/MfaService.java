package com.banking.auth.service;

import com.banking.auth.entity.User;
import com.banking.auth.repository.UserRepository;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    private static final String ISSUER = "BankingApp";
    private static final int SECRET_LENGTH = 32;
    private static final int BACKUP_CODE_COUNT = 10;
    private static final int BACKUP_CODE_LENGTH = 8;
    private static final String BACKUP_CODE_SEPARATOR = ",";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(SECRET_LENGTH);
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String generateQrCodeDataUri(String secret, String email) {
        QrData qrData = new QrData.Builder()
                .label(email)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        try {
            byte[] imageData = qrGenerator.generate(qrData);
            return getDataUriForImage(imageData, qrGenerator.getImageMimeType());
        } catch (Exception e) {
            log.error("Failed to generate QR code for user: {}", email, e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null) {
            return false;
        }
        return codeVerifier.isValidCode(secret, code);
    }

    @Transactional
    public String setupMfa(User user) {
        String secret = generateSecret();
        user.setMfaSecret(secret);
        userRepository.save(user);
        log.info("MFA setup initiated for user: {}", user.getEmail());
        return secret;
    }

    @Transactional
    public void enableMfa(User user) {
        user.setMfaEnabled(true);
        userRepository.save(user);
        log.info("MFA enabled for user: {}", user.getEmail());
    }

    @Transactional
    public void disableMfa(User user) {
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaBackupCodes(null);
        userRepository.save(user);
        log.info("MFA disabled for user: {}", user.getEmail());
    }

    public boolean isMfaEnabled(User user) {
        return user.getMfaEnabled() != null && user.getMfaEnabled();
    }

    public List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            codes.add(generateBackupCode());
        }
        return codes;
    }

    private String generateBackupCode() {
        StringBuilder code = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < BACKUP_CODE_LENGTH; i++) {
            code.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return code.toString();
    }

    @Transactional
    public List<String> generateAndStoreBackupCodes(User user) {
        List<String> plainCodes = generateBackupCodes();
        String hashedCodes = plainCodes.stream()
                .map(passwordEncoder::encode)
                .collect(Collectors.joining(BACKUP_CODE_SEPARATOR));
        user.setMfaBackupCodes(hashedCodes);
        userRepository.save(user);
        log.info("Generated {} backup codes for user: {}", plainCodes.size(), user.getEmail());
        return plainCodes;
    }

    @Transactional
    public boolean verifyBackupCode(User user, String code) {
        if (user.getMfaBackupCodes() == null || user.getMfaBackupCodes().isEmpty()) {
            return false;
        }

        List<String> hashedCodes = new ArrayList<>(
                Arrays.asList(user.getMfaBackupCodes().split(BACKUP_CODE_SEPARATOR))
        );

        for (int i = 0; i < hashedCodes.size(); i++) {
            if (passwordEncoder.matches(code.toUpperCase(), hashedCodes.get(i))) {
                hashedCodes.remove(i);
                user.setMfaBackupCodes(String.join(BACKUP_CODE_SEPARATOR, hashedCodes));
                userRepository.save(user);
                log.info("Backup code used for user: {}, {} codes remaining", 
                        user.getEmail(), hashedCodes.size());
                return true;
            }
        }
        return false;
    }

    public int getRemainingBackupCodes(User user) {
        if (user.getMfaBackupCodes() == null || user.getMfaBackupCodes().isEmpty()) {
            return 0;
        }
        return user.getMfaBackupCodes().split(BACKUP_CODE_SEPARATOR).length;
    }
}
