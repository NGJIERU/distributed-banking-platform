package com.banking.auth.service;

import com.banking.auth.entity.AuditLog;
import com.banking.auth.entity.User;
import com.banking.auth.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String eventType, User user, boolean success,
                         String ipAddress, String userAgent, Map<String, Object> eventData) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .eventType(eventType)
                    .user(user)
                    .success(success)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .eventData(eventData)
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit log created: type={}, userId={}, success={}", 
                    eventType, user != null ? user.getId() : null, success);
        } catch (Exception e) {
            log.error("Failed to create audit log: type={}", eventType, e);
        }
    }

    public void logLoginSuccess(User user, String ipAddress, String userAgent) {
        logEvent(
                AuditLog.EventType.USER_LOGIN_SUCCESS.name(),
                user,
                true,
                ipAddress,
                userAgent,
                Map.of("email", user.getEmail())
        );
    }

    public void logLoginFailure(String email, String reason, String ipAddress, String userAgent) {
        logEvent(
                AuditLog.EventType.USER_LOGIN_FAILED.name(),
                null,
                false,
                ipAddress,
                userAgent,
                Map.of("email", email, "reason", reason)
        );
    }

    public void logRegistration(User user, String ipAddress, String userAgent) {
        logEvent(
                AuditLog.EventType.USER_REGISTERED.name(),
                user,
                true,
                ipAddress,
                userAgent,
                Map.of("email", user.getEmail())
        );
    }

    public void logLogout(User user, String ipAddress, String userAgent) {
        logEvent(
                AuditLog.EventType.USER_LOGOUT.name(),
                user,
                true,
                ipAddress,
                userAgent,
                null
        );
    }

    public void logTokenRefresh(User user, String ipAddress, String userAgent) {
        logEvent(
                AuditLog.EventType.TOKEN_REFRESHED.name(),
                user,
                true,
                ipAddress,
                userAgent,
                null
        );
    }

    public void logMfaEnabled(User user, String ipAddress, String userAgent) {
        logEvent(
                AuditLog.EventType.MFA_ENABLED.name(),
                user,
                true,
                ipAddress,
                userAgent,
                null
        );
    }

    public void logMfaDisabled(User user, String ipAddress, String userAgent) {
        logEvent(
                AuditLog.EventType.MFA_DISABLED.name(),
                user,
                true,
                ipAddress,
                userAgent,
                null
        );
    }

    public void logPasswordChange(User user, String ipAddress, String userAgent) {
        logEvent(
                AuditLog.EventType.PASSWORD_CHANGED.name(),
                user,
                true,
                ipAddress,
                userAgent,
                null
        );
    }

    public void logAccountLocked(User user, String ipAddress, String userAgent) {
        logEvent(
                AuditLog.EventType.ACCOUNT_LOCKED.name(),
                user,
                false,
                ipAddress,
                userAgent,
                Map.of("email", user.getEmail(), "reason", "Too many failed login attempts")
        );
    }
}
