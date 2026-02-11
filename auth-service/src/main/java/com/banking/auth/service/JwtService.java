package com.banking.auth.service;

import com.banking.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JwtService {

    @Value("${jwt.private-key:}")
    private String privateKeyPem;

    @Value("${jwt.public-key:}")
    private String publicKeyPem;

    @Value("${jwt.access-token-expiration:900000}")
    private Long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private Long refreshTokenExpiration;

    @Value("${jwt.issuer:banking-auth-service}")
    private String issuer;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        initRsaKeys();
    }

    private void initRsaKeys() {
        try {
            if (privateKeyPem != null && !privateKeyPem.isEmpty()) {
                privateKey = loadPrivateKey(privateKeyPem);
                log.info("Loaded RSA private key from configuration");
            }
            if (publicKeyPem != null && !publicKeyPem.isEmpty()) {
                publicKey = loadPublicKey(publicKeyPem);
                log.info("Loaded RSA public key from configuration");
            }
            if (privateKey == null || publicKey == null) {
                log.warn("RSA keys not configured - generating ephemeral 2048-bit key pair for DEVELOPMENT ONLY");
                log.warn("For production, set JWT_PRIVATE_KEY and JWT_PUBLIC_KEY environment variables");
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair keyPair = keyGen.generateKeyPair();
                privateKey = keyPair.getPrivate();
                publicKey = keyPair.getPublic();
            }
            log.info("JWT signing initialized with RS256 (asymmetric)");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize RSA keys for JWT signing", e);
        }
    }

    private PrivateKey loadPrivateKey(String pem) throws Exception {
        log.info("Loading private key, input length: {}, first 50 chars: {}", 
                pem.length(), pem.substring(0, Math.min(50, pem.length())));
        String privateKeyContent = pem
                .replace("\\n", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "")
                .replaceAll("[^A-Za-z0-9+/=]", "");
        log.info("Cleaned private key length: {}, first 50 chars: {}", 
                privateKeyContent.length(), privateKeyContent.substring(0, Math.min(50, privateKeyContent.length())));
        if (privateKeyContent.isEmpty()) {
            throw new IllegalArgumentException("Private key is empty after cleaning");
        }
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    private PublicKey loadPublicKey(String pem) throws Exception {
        String publicKeyContent = pem
                .replace("\\n", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "")
                .replaceAll("[^A-Za-z0-9+/=]", "");
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyContent);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("roles", user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toList()));

        return buildToken(claims, user.getId().toString(), accessTokenExpiration);
    }

    public String generateRefreshToken(User user) {
        return buildToken(new HashMap<>(), user.getId().toString(), refreshTokenExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, Long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuer(issuer)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired");
            return false;
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed");
            return false;
        } catch (SignatureException e) {
            log.warn("JWT signature validation failed");
            return false;
        } catch (Exception e) {
            log.warn("JWT token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public UUID extractUserId(String token) {
        String subject = extractAllClaims(token).getSubject();
        return UUID.fromString(subject);
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    public Long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public Long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public String getPublicKeyPem() {
        return "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getEncoder().encodeToString(publicKey.getEncoded()) +
                "\n-----END PUBLIC KEY-----";
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
