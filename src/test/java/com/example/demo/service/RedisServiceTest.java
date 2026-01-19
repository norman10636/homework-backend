package com.example.demo.service;

import com.example.demo.common.RedisKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisService Unit Tests")
class RedisServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisTemplate<String, String> counterRedisTemplate;

    @Mock
    private DefaultRedisScript<Long> rateLimitScript;

    @Mock
    private DefaultRedisScript<Long> getCurrentCountScript;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ValueOperations<String, String> counterValueOperations;

    private RedisService redisService;

    private static final String TEST_API_KEY = "test-api-key";
    private static final String CONFIG_KEY = RedisKey.configCache(TEST_API_KEY);
    private static final String RATE_LIMIT_KEY = RedisKey.rateLimitCounter(TEST_API_KEY);

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(counterRedisTemplate.opsForValue()).thenReturn(counterValueOperations);
        redisService = new RedisService(redisTemplate, counterRedisTemplate, rateLimitScript, getCurrentCountScript);
    }

    @Test
    @DisplayName("Should execute rate limit script successfully")
    void shouldExecuteRateLimitScriptSuccessfully() {
        // Given
        int windowSeconds = 60;
        int limit = 10;
        Long expectedCount = 5L;
        
        when(counterRedisTemplate.execute(
            eq(rateLimitScript),
            eq(Collections.singletonList(RATE_LIMIT_KEY)),
            eq("60"),
            eq("10")
        )).thenReturn(expectedCount);

        // When
        Long result = redisService.executeRateLimit(TEST_API_KEY, windowSeconds, limit);

        // Then
        assertThat(result).isEqualTo(expectedCount);
    }

    @Test
    @DisplayName("Should handle exception during rate limit execution")
    void shouldHandleExceptionDuringRateLimitExecution() {
        // Given
        int windowSeconds = 60;
        int limit = 10;
        
        lenient().when(counterRedisTemplate.execute(
            any(DefaultRedisScript.class),
            anyList(),
            anyString(),
            anyString()
        )).thenThrow(new RuntimeException("Redis connection error"));

        // When
        Long result = redisService.executeRateLimit(TEST_API_KEY, windowSeconds, limit);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should get current count successfully")
    void shouldGetCurrentCountSuccessfully() {
        // Given
        Long expectedCount = 3L;
        
        when(counterRedisTemplate.execute(
            eq(getCurrentCountScript),
            eq(Collections.singletonList(RATE_LIMIT_KEY))
        )).thenReturn(expectedCount);

        // When
        Long result = redisService.getCurrentCount(TEST_API_KEY);

        // Then
        assertThat(result).isEqualTo(expectedCount);
    }

    @Test
    @DisplayName("Should handle exception during get current count")
    void shouldHandleExceptionDuringGetCurrentCount() {
        // Given
        lenient().when(counterRedisTemplate.execute(
            any(DefaultRedisScript.class),
            anyList()
        )).thenThrow(new RuntimeException("Redis connection error"));

        // When
        Long result = redisService.getCurrentCount(TEST_API_KEY);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should get TTL successfully")
    void shouldGetTtlSuccessfully() {
        // Given
        Long expectedTtl = 45L;
        
        when(counterRedisTemplate.getExpire(RATE_LIMIT_KEY, TimeUnit.SECONDS))
            .thenReturn(expectedTtl);

        // When
        Long result = redisService.getTtl(TEST_API_KEY);

        // Then
        assertThat(result).isEqualTo(expectedTtl);
    }

    @Test
    @DisplayName("Should handle exception during get TTL")
    void shouldHandleExceptionDuringGetTtl() {
        // Given
        lenient().when(counterRedisTemplate.getExpire(anyString(), any(TimeUnit.class)))
            .thenThrow(new RuntimeException("Redis connection error"));

        // When
        Long result = redisService.getTtl(TEST_API_KEY);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should cache API limit config successfully")
    void shouldCacheApiLimitConfigSuccessfully() {
        // Given
        String configJson = "{\"apiKey\":\"test-api-key\",\"limitCount\":10,\"windowSeconds\":60}";

        // When
        assertThatNoException().isThrownBy(() -> 
            redisService.cacheApiLimitConfig(TEST_API_KEY, configJson));

        // Then
        verify(valueOperations).set(CONFIG_KEY, configJson, 300, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Should handle exception during cache config")
    void shouldHandleExceptionDuringCacheConfig() {
        // Given
        String configJson = "{\"apiKey\":\"test-api-key\",\"limitCount\":10,\"windowSeconds\":60}";
        
        lenient().doThrow(new RuntimeException("Redis connection error"))
            .when(valueOperations).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        // When & Then - Should not throw exception
        assertThatNoException().isThrownBy(() -> 
            redisService.cacheApiLimitConfig(TEST_API_KEY, configJson));
    }

    @Test
    @DisplayName("Should get cached config successfully")
    void shouldGetCachedConfigSuccessfully() {
        // Given
        String expectedConfig = "{\"apiKey\":\"test-api-key\",\"limitCount\":10,\"windowSeconds\":60}";
        
        when(valueOperations.get(CONFIG_KEY)).thenReturn(expectedConfig);

        // When
        String result = redisService.getCachedApiLimitConfig(TEST_API_KEY);

        // Then
        assertThat(result).isEqualTo(expectedConfig);
    }

    @Test
    @DisplayName("Should return null when cached config not found")
    void shouldReturnNullWhenCachedConfigNotFound() {
        // Given
        lenient().when(valueOperations.get(CONFIG_KEY)).thenReturn(null);

        // When
        String result = redisService.getCachedApiLimitConfig(TEST_API_KEY);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle exception during get cached config")
    void shouldHandleExceptionDuringGetCachedConfig() {
        // Given
        lenient().when(valueOperations.get(anyString()))
            .thenThrow(new RuntimeException("Redis connection error"));

        // When
        String result = redisService.getCachedApiLimitConfig(TEST_API_KEY);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should convert non-string cached values to string")
    void shouldConvertNonStringCachedValuesToString() {
        // Given
        Integer integerValue = 12345;
        
        when(valueOperations.get(CONFIG_KEY)).thenReturn(integerValue);

        // When
        String result = redisService.getCachedApiLimitConfig(TEST_API_KEY);

        // Then
        assertThat(result).isEqualTo("12345");
    }

    @Test
    @DisplayName("Should evict cache successfully")
    void shouldEvictCacheSuccessfully() {
        // When
        assertThatNoException().isThrownBy(() -> 
            redisService.evictCache(TEST_API_KEY));

        // Then
        verify(redisTemplate).delete(CONFIG_KEY);
        verify(counterRedisTemplate).delete(RATE_LIMIT_KEY);
    }

    @Test
    @DisplayName("Should handle exception during cache eviction")
    void shouldHandleExceptionDuringCacheEviction() {
        // Given
        lenient().doThrow(new RuntimeException("Redis connection error"))
            .when(redisTemplate).delete(anyString());

        // When & Then - Should not throw exception
        assertThatNoException().isThrownBy(() -> 
            redisService.evictCache(TEST_API_KEY));
    }

    @Test
    @DisplayName("Should return true when Redis is available")
    void shouldReturnTrueWhenRedisIsAvailable() {
        // Given
        lenient().when(counterValueOperations.get("health-check")).thenReturn("ok");

        // When
        boolean result = redisService.isRedisAvailable();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when Redis is not available")
    void shouldReturnFalseWhenRedisIsNotAvailable() {
        // Given
        lenient().when(counterValueOperations.get("health-check"))
            .thenThrow(new RuntimeException("Redis connection error"));

        // When
        boolean result = redisService.isRedisAvailable();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should handle null return from Redis health check")
    void shouldHandleNullReturnFromRedisHealthCheck() {
        // Given
        lenient().when(counterValueOperations.get("health-check")).thenReturn(null);

        // When
        boolean result = redisService.isRedisAvailable();

        // Then
        assertThat(result).isTrue(); // Method only checks if no exception is thrown
    }
}