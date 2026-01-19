package com.example.demo.service;

import com.example.demo.model.ApiLimit;
import com.example.demo.model.dto.CheckResponse;
import com.example.demo.model.dto.CreateLimitRequest;
import com.example.demo.model.dto.LimitsResponse;
import com.example.demo.model.dto.UsageResponse;
import com.example.demo.mq.MessageProducer;
import com.example.demo.mq.RateLimitEventType;
import com.example.demo.repository.ApiLimitRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitService Unit Tests")
class RateLimitServiceTest {

    @Mock
    private ApiLimitRepository apiLimitRepository;
    
    @Mock
    private RedisService redisService;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @Mock
    private MessageProducer messageProducer;
    
    @InjectMocks
    private RateLimitService rateLimitService;
    
    private ApiLimit testApiLimit;
    private CreateLimitRequest testRequest;
    
    @BeforeEach
    void setUp() {
        testApiLimit = new ApiLimit("test-api-key", 10, 60);
        testApiLimit.setCreatedAt(LocalDateTime.now());
        testApiLimit.setUpdatedAt(LocalDateTime.now());
        
        testRequest = new CreateLimitRequest("test-api-key", 10, 60);
    }
    
    @Test
    @DisplayName("Should create limit successfully")
    void shouldCreateLimitSuccessfully() throws Exception {
        // Given
        String configJson = "{\"apiKey\":\"test-api-key\",\"limitCount\":10,\"windowSeconds\":60}";
        given(apiLimitRepository.save(any(ApiLimit.class))).willReturn(testApiLimit);
        given(objectMapper.writeValueAsString(testApiLimit)).willReturn(configJson);
        
        // When
        ApiLimit result = rateLimitService.createLimit(testRequest);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getApiKey()).isEqualTo("test-api-key");
        assertThat(result.getLimitCount()).isEqualTo(10);
        assertThat(result.getWindowSeconds()).isEqualTo(60);
        
        then(apiLimitRepository).should().save(any(ApiLimit.class));
        then(redisService).should().cacheApiLimitConfig("test-api-key", configJson);
        then(messageProducer).should().sendConfigChangeEvent("test-api-key", RateLimitEventType.ConfigAction.CREATED);
    }
    
    @Test
    @DisplayName("Should handle cache failure gracefully during create")
    void shouldHandleCacheFailureGracefullyDuringCreate() throws Exception {
        // Given
        given(apiLimitRepository.save(any(ApiLimit.class))).willReturn(testApiLimit);
        given(objectMapper.writeValueAsString(testApiLimit)).willThrow(new JsonProcessingException("Cache error") {});
        
        // When
        ApiLimit result = rateLimitService.createLimit(testRequest);
        
        // Then
        assertThat(result).isNotNull();
        then(apiLimitRepository).should().save(any(ApiLimit.class));
        then(messageProducer).should().sendConfigChangeEvent("test-api-key", RateLimitEventType.ConfigAction.CREATED);
    }
    
    @Test
    @DisplayName("Should allow request when no rate limit configured")
    void shouldAllowRequestWhenNoRateLimitConfigured() {
        // Given
        given(redisService.getCachedApiLimitConfig("unknown-key")).willReturn(null);
        given(apiLimitRepository.findByApiKey("unknown-key")).willReturn(Optional.empty());
        
        // When
        CheckResponse result = rateLimitService.checkApiAccess("unknown-key");
        
        // Then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getMessage()).isEqualTo("No rate limit configured for this API key");
    }
    
    @Test
    @DisplayName("Should allow request when Redis unavailable (fail-open)")
    void shouldAllowRequestWhenRedisUnavailable() throws Exception {
        // Given
        String configJson = "{\"apiKey\":\"test-api-key\",\"limitCount\":10,\"windowSeconds\":60}";
        given(redisService.getCachedApiLimitConfig("test-api-key")).willReturn(configJson);
        given(objectMapper.readValue(configJson, ApiLimit.class)).willReturn(testApiLimit);
        given(redisService.isRedisAvailable()).willReturn(false);
        
        // When
        CheckResponse result = rateLimitService.checkApiAccess("test-api-key");
        
        // Then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Rate limiting unavailable - request allowed");
    }
    
    @Test
    @DisplayName("Should allow request when within rate limit")
    void shouldAllowRequestWhenWithinRateLimit() throws Exception {
        // Given
        String configJson = "{\"apiKey\":\"test-api-key\",\"limitCount\":10,\"windowSeconds\":60}";
        given(redisService.getCachedApiLimitConfig("test-api-key")).willReturn(configJson);
        given(objectMapper.readValue(configJson, ApiLimit.class)).willReturn(testApiLimit);
        given(redisService.isRedisAvailable()).willReturn(true);
        given(redisService.executeRateLimit("test-api-key", 60, 10)).willReturn(5L);
        given(redisService.getTtl("test-api-key")).willReturn(45L);
        
        // When
        CheckResponse result = rateLimitService.checkApiAccess("test-api-key");
        
        // Then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Request allowed");
        assertThat(result.getCurrentCount()).isEqualTo(5);
        assertThat(result.getLimitCount()).isEqualTo(10);
        assertThat(result.getRemainingTtl()).isEqualTo(45L);
    }
    
    @Test
    @DisplayName("Should block request when rate limit exceeded")
    void shouldBlockRequestWhenRateLimitExceeded() throws Exception {
        // Given
        String configJson = "{\"apiKey\":\"test-api-key\",\"limitCount\":10,\"windowSeconds\":60}";
        given(redisService.getCachedApiLimitConfig("test-api-key")).willReturn(configJson);
        given(objectMapper.readValue(configJson, ApiLimit.class)).willReturn(testApiLimit);
        given(redisService.isRedisAvailable()).willReturn(true);
        given(redisService.executeRateLimit("test-api-key", 60, 10)).willReturn(12L);
        given(redisService.getTtl("test-api-key")).willReturn(30L);
        
        // When
        CheckResponse result = rateLimitService.checkApiAccess("test-api-key");
        
        // Then
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Rate limit exceeded");
        assertThat(result.getCurrentCount()).isEqualTo(12);
        assertThat(result.getLimitCount()).isEqualTo(10);
        assertThat(result.getRemainingTtl()).isEqualTo(30L);
        
        then(messageProducer).should().sendBlockedEvent("test-api-key", 12, 10, 30L);
    }
    
    @Test
    @DisplayName("Should allow request when rate limit execution fails (fail-open)")
    void shouldAllowRequestWhenRateLimitExecutionFails() throws Exception {
        // Given
        String configJson = "{\"apiKey\":\"test-api-key\",\"limitCount\":10,\"windowSeconds\":60}";
        given(redisService.getCachedApiLimitConfig("test-api-key")).willReturn(configJson);
        given(objectMapper.readValue(configJson, ApiLimit.class)).willReturn(testApiLimit);
        given(redisService.isRedisAvailable()).willReturn(true);
        given(redisService.executeRateLimit("test-api-key", 60, 10)).willReturn(null);
        
        // When
        CheckResponse result = rateLimitService.checkApiAccess("test-api-key");
        
        // Then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Rate limiting failed - request allowed");
    }
    
    @Test
    @DisplayName("Should handle exceptions gracefully (fail-open)")
    void shouldHandleExceptionsGracefully() throws Exception {
        // Given
        given(redisService.getCachedApiLimitConfig("test-api-key")).willReturn(null);
        given(apiLimitRepository.findByApiKey("test-api-key")).willReturn(Optional.of(testApiLimit));
        given(objectMapper.writeValueAsString(testApiLimit)).willReturn("config");
        given(redisService.isRedisAvailable()).willReturn(true);
        given(redisService.executeRateLimit("test-api-key", 60, 10)).willThrow(new RuntimeException("Redis execution error"));
        
        // When
        CheckResponse result = rateLimitService.checkApiAccess("test-api-key");
        
        // Then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Rate limiting error - request allowed");
    }
    
    @Test
    @DisplayName("Should get usage information successfully")
    void shouldGetUsageInformationSuccessfully() throws Exception {
        // Given
        String configJson = "{\"apiKey\":\"test-api-key\",\"limitCount\":10,\"windowSeconds\":60}";
        given(redisService.getCachedApiLimitConfig("test-api-key")).willReturn(configJson);
        given(objectMapper.readValue(configJson, ApiLimit.class)).willReturn(testApiLimit);
        given(redisService.getCurrentCount("test-api-key")).willReturn(3L);
        given(redisService.getTtl("test-api-key")).willReturn(45L);
        
        // When
        UsageResponse result = rateLimitService.getUsage("test-api-key");
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getApiKey()).isEqualTo("test-api-key");
        assertThat(result.getCurrentCount()).isEqualTo(3);
        assertThat(result.getLimitCount()).isEqualTo(10);
        assertThat(result.getRemaining()).isEqualTo(7);
        assertThat(result.getWindowTtl()).isEqualTo(45L);
        assertThat(result.getWindowSeconds()).isEqualTo(60);
    }
    
    @Test
    @DisplayName("Should handle null values in usage response")
    void shouldHandleNullValuesInUsageResponse() throws Exception {
        // Given
        String configJson = "{\"apiKey\":\"test-api-key\",\"limitCount\":10,\"windowSeconds\":60}";
        given(redisService.getCachedApiLimitConfig("test-api-key")).willReturn(configJson);
        given(objectMapper.readValue(configJson, ApiLimit.class)).willReturn(testApiLimit);
        given(redisService.getCurrentCount("test-api-key")).willReturn(null);
        given(redisService.getTtl("test-api-key")).willReturn(-1L);
        
        // When
        UsageResponse result = rateLimitService.getUsage("test-api-key");
        
        // Then
        assertThat(result.getCurrentCount()).isEqualTo(0);
        assertThat(result.getRemaining()).isEqualTo(10);
        assertThat(result.getWindowTtl()).isEqualTo(0L);
    }
    
    @Test
    @DisplayName("Should throw exception when API key not found for usage")
    void shouldThrowExceptionWhenApiKeyNotFoundForUsage() {
        // Given
        given(redisService.getCachedApiLimitConfig("unknown-key")).willReturn(null);
        given(apiLimitRepository.findByApiKey("unknown-key")).willReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> rateLimitService.getUsage("unknown-key"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Failed to get usage information");
    }
    
    @Test
    @DisplayName("Should remove limit successfully")
    void shouldRemoveLimitSuccessfully() {
        // Given
        given(apiLimitRepository.existsByApiKey("test-api-key")).willReturn(true);
        
        // When
        rateLimitService.removeLimit("test-api-key");
        
        // Then
        then(apiLimitRepository).should().deleteByApiKey("test-api-key");
        then(redisService).should().evictCache("test-api-key");
        then(messageProducer).should().sendConfigChangeEvent("test-api-key", RateLimitEventType.ConfigAction.DELETED);
    }
    
    @Test
    @DisplayName("Should throw exception when removing non-existent API key")
    void shouldThrowExceptionWhenRemovingNonExistentApiKey() {
        // Given
        given(apiLimitRepository.existsByApiKey("unknown-key")).willReturn(false);
        
        // When & Then
        assertThatThrownBy(() -> rateLimitService.removeLimit("unknown-key"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("API key not found");
    }
    
    @Test
    @DisplayName("Should get all limits with pagination")
    void shouldGetAllLimitsWithPagination() {
        // Given
        ApiLimit limit1 = new ApiLimit("api-key-1", 5, 30);
        ApiLimit limit2 = new ApiLimit("api-key-2", 15, 120);
        Page<ApiLimit> mockPage = new PageImpl<>(Arrays.asList(limit1, limit2), PageRequest.of(0, 10), 2);
        
        given(apiLimitRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).willReturn(mockPage);
        
        // When
        LimitsResponse result = rateLimitService.getAllLimits(0, 10);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getLimits()).hasSize(2);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getTotalElements()).isEqualTo(2L);
        assertThat(result.getCurrentPage()).isEqualTo(0);
        assertThat(result.getPageSize()).isEqualTo(10);
        
        assertThat(result.getLimits().get(0).getApiKey()).isEqualTo("api-key-1");
        assertThat(result.getLimits().get(0).getLimitCount()).isEqualTo(5);
        assertThat(result.getLimits().get(1).getApiKey()).isEqualTo("api-key-2");
        assertThat(result.getLimits().get(1).getLimitCount()).isEqualTo(15);
    }
    
    @Test
    @DisplayName("Should get config from cache successfully")
    void shouldGetConfigFromCacheSuccessfully() throws Exception {
        // Given
        String configJson = "{\"apiKey\":\"test-api-key\",\"limitCount\":10,\"windowSeconds\":60}";
        given(redisService.getCachedApiLimitConfig("test-api-key")).willReturn(configJson);
        given(objectMapper.readValue(configJson, ApiLimit.class)).willReturn(testApiLimit);
        given(redisService.isRedisAvailable()).willReturn(true);
        given(redisService.executeRateLimit("test-api-key", 60, 10)).willReturn(5L);
        given(redisService.getTtl("test-api-key")).willReturn(45L);
        
        // When
        CheckResponse result = rateLimitService.checkApiAccess("test-api-key");
        
        // Then
        assertThat(result.isAllowed()).isTrue();
        then(apiLimitRepository).should(never()).findByApiKey("test-api-key");
    }
    
    @Test
    @DisplayName("Should get config from database when cache miss")
    void shouldGetConfigFromDatabaseWhenCacheMiss() throws Exception {
        // Given
        given(redisService.getCachedApiLimitConfig("test-api-key")).willReturn(null);
        given(apiLimitRepository.findByApiKey("test-api-key")).willReturn(Optional.of(testApiLimit));
        String configJson = "{\"apiKey\":\"test-api-key\",\"limitCount\":10,\"windowSeconds\":60}";
        given(objectMapper.writeValueAsString(testApiLimit)).willReturn(configJson);
        given(redisService.isRedisAvailable()).willReturn(true);
        given(redisService.executeRateLimit("test-api-key", 60, 10)).willReturn(5L);
        given(redisService.getTtl("test-api-key")).willReturn(45L);
        
        // When
        CheckResponse result = rateLimitService.checkApiAccess("test-api-key");
        
        // Then
        assertThat(result.isAllowed()).isTrue();
        then(apiLimitRepository).should().findByApiKey("test-api-key");
        then(redisService).should().cacheApiLimitConfig("test-api-key", configJson);
    }
    
    @Test
    @DisplayName("Should handle cache read failure gracefully")
    void shouldHandleCacheReadFailureGracefully() throws Exception {
        // Given
        given(redisService.getCachedApiLimitConfig("test-api-key")).willThrow(new RuntimeException("Cache read error"));
        given(apiLimitRepository.findByApiKey("test-api-key")).willReturn(Optional.of(testApiLimit));
        String configJson = "{\"apiKey\":\"test-api-key\",\"limitCount\":10,\"windowSeconds\":60}";
        given(objectMapper.writeValueAsString(testApiLimit)).willReturn(configJson);
        given(redisService.isRedisAvailable()).willReturn(true);
        given(redisService.executeRateLimit("test-api-key", 60, 10)).willReturn(5L);
        given(redisService.getTtl("test-api-key")).willReturn(45L);
        
        // When
        CheckResponse result = rateLimitService.checkApiAccess("test-api-key");
        
        // Then
        assertThat(result.isAllowed()).isTrue();
        then(apiLimitRepository).should().findByApiKey("test-api-key");
    }
}