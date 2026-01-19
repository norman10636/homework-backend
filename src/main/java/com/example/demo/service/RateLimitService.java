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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {
    
    private static final boolean FAIL_OPEN = true; // Fail-open strategy
    
    private final ApiLimitRepository apiLimitRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final MessageProducer messageProducer;
    
    @Transactional
    public ApiLimit createLimit(CreateLimitRequest request) {
        ApiLimit apiLimit = new ApiLimit(request.getApiKey(), request.getLimit(), request.getWindowSeconds());
        ApiLimit savedLimit = apiLimitRepository.save(apiLimit);
        
        // Cache the configuration
        try {
            String configJson = objectMapper.writeValueAsString(savedLimit);
            redisService.cacheApiLimitConfig(request.getApiKey(), configJson);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache configuration for apiKey: {}", request.getApiKey(), e);
        }
        
        // Send async event
        messageProducer.sendConfigChangeEvent(request.getApiKey(), RateLimitEventType.ConfigAction.CREATED);
        
        return savedLimit;
    }
    
    public CheckResponse checkApiAccess(String apiKey) {
        try {
            // Get configuration with Cache-aside Pattern
            ApiLimit config = getApiLimitConfig(apiKey);
            if (config == null) {
                return new CheckResponse(true, "No rate limit configured for this API key");
            }
            
            // Execute rate limiting with atomic Lua script
            if (!redisService.isRedisAvailable()) {
                if (FAIL_OPEN) {
                    log.warn("Redis unavailable, allowing request for apiKey: {}", apiKey);
                    return new CheckResponse(true, "Rate limiting unavailable - request allowed");
                } else {
                    return new CheckResponse(false, "Rate limiting service unavailable");
                }
            }
            
            Long currentCount = redisService.executeRateLimit(apiKey, config.getWindowSeconds(), config.getLimitCount());
            log.info("Rate limit check for apiKey: {}, currentCount: {}, limit: {}", 
                apiKey, currentCount, config.getLimitCount());
                
            if (currentCount == null) {
                if (FAIL_OPEN) {
                    log.warn("Failed to execute rate limit, allowing request for apiKey: {}", apiKey);
                    return new CheckResponse(true, "Rate limiting failed - request allowed");
                } else {
                    return new CheckResponse(false, "Rate limiting failed");
                }
            }
            
            Long ttl = redisService.getTtl(apiKey);
            
            if (currentCount > config.getLimitCount()) {
                // Send blocked event async
                messageProducer.sendBlockedEvent(apiKey, currentCount.intValue(), config.getLimitCount(), ttl);
                return new CheckResponse(false, "Rate limit exceeded", 
                    currentCount.intValue(), config.getLimitCount(), ttl);
            } else {
                return new CheckResponse(true, "Request allowed", 
                    currentCount.intValue(), config.getLimitCount(), ttl);
            }
            
        } catch (Exception e) {
            log.error("Error checking API access for apiKey: {}", apiKey, e);
            if (FAIL_OPEN) {
                return new CheckResponse(true, "Rate limiting error - request allowed");
            } else {
                return new CheckResponse(false, "Rate limiting service error");
            }
        }
    }
    
    public UsageResponse getUsage(String apiKey) {
        try {
            ApiLimit config = getApiLimitConfig(apiKey);
            if (config == null) {
                throw new RuntimeException("API key not found");
            }
            
            Long currentCount = redisService.getCurrentCount(apiKey);
            Long ttl = redisService.getTtl(apiKey);
            
            if (currentCount == null) {
                currentCount = 0L;
            }
            if (ttl == null || ttl < 0) {
                ttl = 0L;
            }
            
            int remaining = Math.max(0, config.getLimitCount() - currentCount.intValue());
            
            return new UsageResponse(apiKey, currentCount.intValue(), config.getLimitCount(), 
                remaining, ttl, config.getWindowSeconds());
            
        } catch (Exception e) {
            log.error("Error getting usage for apiKey: {}", apiKey, e);
            throw new RuntimeException("Failed to get usage information");
        }
    }
    
    @Transactional
    public void removeLimit(String apiKey) {
        if (!apiLimitRepository.existsByApiKey(apiKey)) {
            throw new RuntimeException("API key not found");
        }
        
        apiLimitRepository.deleteByApiKey(apiKey);
        redisService.evictCache(apiKey);
        
        // Send async event
        messageProducer.sendConfigChangeEvent(apiKey, RateLimitEventType.ConfigAction.DELETED);
    }
    
    public LimitsResponse getAllLimits(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ApiLimit> limitPage = apiLimitRepository.findAllByOrderByCreatedAtDesc(pageable);
        
        List<LimitsResponse.LimitInfo> limitInfos = limitPage.getContent().stream()
            .map(limit -> new LimitsResponse.LimitInfo(
                limit.getApiKey(),
                limit.getLimitCount(),
                limit.getWindowSeconds(),
                limit.getCreatedAt(),
                limit.getUpdatedAt()
            ))
            .collect(Collectors.toList());
        
        return new LimitsResponse(
            limitInfos,
            limitPage.getTotalPages(),
            limitPage.getTotalElements(),
            limitPage.getNumber(),
            limitPage.getSize()
        );
    }
    
    private ApiLimit getApiLimitConfig(String apiKey) {
        // Cache-aside Pattern implementation
        try {
            // Try to get from cache first
            String cachedConfig = redisService.getCachedApiLimitConfig(apiKey);
            if (cachedConfig != null) {
                return objectMapper.readValue(cachedConfig, ApiLimit.class);
            }
        } catch (Exception e) {
            log.warn("Failed to get cached config for apiKey: {}", apiKey, e);
        }
        
        // If cache miss, get from database
        Optional<ApiLimit> limitOpt = apiLimitRepository.findByApiKey(apiKey);
        if (limitOpt.isPresent()) {
            ApiLimit limit = limitOpt.get();
            // Cache the result
            try {
                String configJson = objectMapper.writeValueAsString(limit);
                redisService.cacheApiLimitConfig(apiKey, configJson);
            } catch (JsonProcessingException e) {
                log.warn("Failed to cache configuration for apiKey: {}", apiKey, e);
            }
            return limit;
        }
        
        return null;
    }
}