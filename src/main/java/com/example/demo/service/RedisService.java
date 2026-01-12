package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisService.class);
    private static final String CONFIG_CACHE_PREFIX = "cache:config:";
    private static final String RATE_LIMIT_PREFIX = "rate:limit:cnt:";
    private static final int CONFIG_CACHE_TTL = 300; // 5 minutes
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;
    private final DefaultRedisScript<Long> getCurrentCountScript;
    
    public RedisService(RedisTemplate<String, Object> redisTemplate,
                       DefaultRedisScript<Long> rateLimitScript,
                       DefaultRedisScript<Long> getCurrentCountScript) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.getCurrentCountScript = getCurrentCountScript;
    }
    
    public Long executeRateLimit(String apiKey, int windowSeconds, int limit) {
        try {
            String key = RATE_LIMIT_PREFIX + apiKey;
            return redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                windowSeconds,
                limit
            );
        } catch (Exception e) {
            logger.error("Failed to execute rate limit for apiKey: {}", apiKey, e);
            return null;
        }
    }
    
    public Long getCurrentCount(String apiKey) {
        try {
            String key = RATE_LIMIT_PREFIX + apiKey;
            return redisTemplate.execute(
                getCurrentCountScript,
                Collections.singletonList(key)
            );
        } catch (Exception e) {
            logger.error("Failed to get current count for apiKey: {}", apiKey, e);
            return null;
        }
    }
    
    public Long getTtl(String apiKey) {
        try {
            String key = RATE_LIMIT_PREFIX + apiKey;
            return redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Failed to get TTL for apiKey: {}", apiKey, e);
            return null;
        }
    }
    
    public void cacheApiLimitConfig(String apiKey, String configJson) {
        try {
            String key = CONFIG_CACHE_PREFIX + apiKey;
            redisTemplate.opsForValue().set(key, configJson, CONFIG_CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Failed to cache config for apiKey: {}", apiKey, e);
        }
    }
    
    public String getCachedApiLimitConfig(String apiKey) {
        try {
            String key = CONFIG_CACHE_PREFIX + apiKey;
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            logger.error("Failed to get cached config for apiKey: {}", apiKey, e);
            return null;
        }
    }
    
    public void evictCache(String apiKey) {
        try {
            String configKey = CONFIG_CACHE_PREFIX + apiKey;
            String countKey = RATE_LIMIT_PREFIX + apiKey;
            redisTemplate.delete(configKey);
            redisTemplate.delete(countKey);
        } catch (Exception e) {
            logger.error("Failed to evict cache for apiKey: {}", apiKey, e);
        }
    }
    
    public boolean isRedisAvailable() {
        try {
            redisTemplate.opsForValue().get("health-check");
            return true;
        } catch (Exception e) {
            logger.warn("Redis is not available", e);
            return false;
        }
    }
}