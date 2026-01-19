package com.example.demo.service;

import com.example.demo.common.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private static final int CONFIG_CACHE_TTL = 300; // 5 minutes

    private final RedisTemplate<String, Object> redisTemplate;
    @Qualifier("counterRedisTemplate")
    private final RedisTemplate<String, String> counterRedisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;
    private final DefaultRedisScript<Long> getCurrentCountScript;

    public Long executeRateLimit(String apiKey, int windowSeconds, int limit) {
        try {
            String key = RedisKey.rateLimitCounter(apiKey);
            return counterRedisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(windowSeconds),
                String.valueOf(limit)
            );
        } catch (Exception e) {
            log.error("Failed to execute rate limit for apiKey: {}", apiKey, e);
            return null;
        }
    }

    public Long getCurrentCount(String apiKey) {
        try {
            String key = RedisKey.rateLimitCounter(apiKey);
            return counterRedisTemplate.execute(
                getCurrentCountScript,
                Collections.singletonList(key)
            );
        } catch (Exception e) {
            log.error("Failed to get current count for apiKey: {}", apiKey, e);
            return null;
        }
    }

    public Long getTtl(String apiKey) {
        try {
            String key = RedisKey.rateLimitCounter(apiKey);
            return counterRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to get TTL for apiKey: {}", apiKey, e);
            return null;
        }
    }

    public void cacheApiLimitConfig(String apiKey, String configJson) {
        try {
            String key = RedisKey.configCache(apiKey);
            redisTemplate.opsForValue().set(key, configJson, CONFIG_CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to cache config for apiKey: {}", apiKey, e);
        }
    }

    public String getCachedApiLimitConfig(String apiKey) {
        try {
            String key = RedisKey.configCache(apiKey);
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.error("Failed to get cached config for apiKey: {}", apiKey, e);
            return null;
        }
    }

    public void evictCache(String apiKey) {
        try {
            String configKey = RedisKey.configCache(apiKey);
            String countKey = RedisKey.rateLimitCounter(apiKey);
            redisTemplate.delete(configKey);
            counterRedisTemplate.delete(countKey);
        } catch (Exception e) {
            log.error("Failed to evict cache for apiKey: {}", apiKey, e);
        }
    }

    public boolean isRedisAvailable() {
        try {
            counterRedisTemplate.opsForValue().get("health-check");
            return true;
        } catch (Exception e) {
            log.warn("Redis is not available", e);
            return false;
        }
    }
}
