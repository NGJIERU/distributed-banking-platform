package com.banking.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitingService {

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    private static final int LOGIN_LIMIT_PER_MINUTE = 10;
    private static final int REGISTER_LIMIT_PER_MINUTE = 5;
    private static final int DEFAULT_LIMIT_PER_SECOND = 50;

    private final RedisTemplate<String, String> redisTemplate;

    public boolean isLoginAllowed(String ipAddress) {
        return isAllowed("login:" + ipAddress, LOGIN_LIMIT_PER_MINUTE, Duration.ofMinutes(1));
    }

    public boolean isRegistrationAllowed(String ipAddress) {
        return isAllowed("register:" + ipAddress, REGISTER_LIMIT_PER_MINUTE, Duration.ofMinutes(1));
    }

    public boolean isRequestAllowed(String ipAddress) {
        return isAllowed("request:" + ipAddress, DEFAULT_LIMIT_PER_SECOND, Duration.ofSeconds(1));
    }

    private boolean isAllowed(String key, int limit, Duration window) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        try {
            Long currentCount = redisTemplate.opsForValue().increment(redisKey);
            if (currentCount == null) {
                return false;
            }
            if (currentCount == 1) {
                redisTemplate.expire(redisKey, window);
            }
            if (currentCount > limit) {
                log.warn("Rate limit exceeded for key: {}, count: {}, limit: {}", key, currentCount, limit);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Redis error during rate limiting, allowing request: {}", e.getMessage());
            return true;
        }
    }

    public long getRemainingRequests(String key, int limit) {
        String redisKey = RATE_LIMIT_PREFIX + key;
        try {
            String value = redisTemplate.opsForValue().get(redisKey);
            if (value == null) {
                return limit;
            }
            long current = Long.parseLong(value);
            return Math.max(0, limit - current);
        } catch (Exception e) {
            return limit;
        }
    }
}
