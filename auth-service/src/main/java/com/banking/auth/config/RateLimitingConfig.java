package com.banking.auth.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RateLimitingConfig {

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig loginConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(10)
                .timeoutDuration(Duration.ofMillis(100))
                .build();

        RateLimiterConfig registerConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .limitForPeriod(5)
                .timeoutDuration(Duration.ofMillis(100))
                .build();

        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(50)
                .timeoutDuration(Duration.ofMillis(100))
                .build();

        RateLimiterRegistry registry = RateLimiterRegistry.of(defaultConfig);
        registry.rateLimiter("login", loginConfig);
        registry.rateLimiter("register", registerConfig);

        return registry;
    }

    @Bean
    public RateLimiter loginRateLimiter(RateLimiterRegistry registry) {
        return registry.rateLimiter("login");
    }

    @Bean
    public RateLimiter registerRateLimiter(RateLimiterRegistry registry) {
        return registry.rateLimiter("register");
    }
}
