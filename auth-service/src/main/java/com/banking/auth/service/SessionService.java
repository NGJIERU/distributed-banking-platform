package com.banking.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String SESSION_PREFIX = "session:";
    private static final String USER_SESSIONS_PREFIX = "user_sessions:";
    private static final Duration SESSION_TTL = Duration.ofDays(7);

    private final RedisTemplate<String, String> redisTemplate;

    public String createSession(UUID userId, String refreshToken, String ipAddress, String userAgent) {
        String sessionId = UUID.randomUUID().toString();
        String sessionKey = SESSION_PREFIX + sessionId;
        String userSessionsKey = USER_SESSIONS_PREFIX + userId.toString();

        String sessionData = String.join("|", 
                userId.toString(), 
                refreshToken, 
                ipAddress != null ? ipAddress : "", 
                userAgent != null ? userAgent : "",
                String.valueOf(System.currentTimeMillis())
        );

        redisTemplate.opsForValue().set(sessionKey, sessionData, SESSION_TTL);
        redisTemplate.opsForSet().add(userSessionsKey, sessionId);
        redisTemplate.expire(userSessionsKey, SESSION_TTL);

        log.info("Session created for user {}: sessionId={}", userId, sessionId);
        return sessionId;
    }

    public boolean isSessionValid(String sessionId) {
        String sessionKey = SESSION_PREFIX + sessionId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey));
    }

    public String getSessionData(String sessionId) {
        String sessionKey = SESSION_PREFIX + sessionId;
        return redisTemplate.opsForValue().get(sessionKey);
    }

    public UUID getUserIdFromSession(String sessionId) {
        String sessionData = getSessionData(sessionId);
        if (sessionData == null) {
            return null;
        }
        String[] parts = sessionData.split("\\|");
        if (parts.length > 0) {
            return UUID.fromString(parts[0]);
        }
        return null;
    }

    public void invalidateSession(String sessionId) {
        String sessionKey = SESSION_PREFIX + sessionId;
        String sessionData = redisTemplate.opsForValue().get(sessionKey);
        
        if (sessionData != null) {
            String[] parts = sessionData.split("\\|");
            if (parts.length > 0) {
                String userId = parts[0];
                String userSessionsKey = USER_SESSIONS_PREFIX + userId;
                redisTemplate.opsForSet().remove(userSessionsKey, sessionId);
            }
        }
        
        redisTemplate.delete(sessionKey);
        log.info("Session invalidated: {}", sessionId);
    }

    public void invalidateAllUserSessions(UUID userId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + userId.toString();
        Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);
        
        if (sessionIds != null && !sessionIds.isEmpty()) {
            for (String sessionId : sessionIds) {
                redisTemplate.delete(SESSION_PREFIX + sessionId);
            }
            redisTemplate.delete(userSessionsKey);
            log.info("All sessions invalidated for user {}: {} sessions", userId, sessionIds.size());
        }
    }

    public Set<String> getUserActiveSessions(UUID userId) {
        String userSessionsKey = USER_SESSIONS_PREFIX + userId.toString();
        return redisTemplate.opsForSet().members(userSessionsKey);
    }

    public int getActiveSessionCount(UUID userId) {
        Set<String> sessions = getUserActiveSessions(userId);
        return sessions != null ? sessions.size() : 0;
    }

    public void refreshSession(String sessionId) {
        String sessionKey = SESSION_PREFIX + sessionId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey))) {
            redisTemplate.expire(sessionKey, SESSION_TTL);
            log.debug("Session refreshed: {}", sessionId);
        }
    }
}
