package com.example.demo.controller;

import com.example.demo.model.ApiLimit;
import com.example.demo.model.dto.CheckResponse;
import com.example.demo.model.dto.CreateLimitRequest;
import com.example.demo.model.dto.LimitsResponse;
import com.example.demo.model.dto.UsageResponse;
import com.example.demo.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(RateLimitController.class)
@DisplayName("RateLimitController Unit Tests")
class RateLimitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RateLimitService rateLimitService;

    @Autowired
    private ObjectMapper objectMapper;

    private ApiLimit testApiLimit;
    private CreateLimitRequest testRequest;
    private CheckResponse allowedResponse;
    private CheckResponse blockedResponse;
    private UsageResponse usageResponse;
    private LimitsResponse limitsResponse;

    @BeforeEach
    void setUp() {
        testApiLimit = new ApiLimit("test-api-key", 10, 60);
        testApiLimit.setCreatedAt(LocalDateTime.now());
        testApiLimit.setUpdatedAt(LocalDateTime.now());

        testRequest = new CreateLimitRequest("test-api-key", 10, 60);

        allowedResponse = new CheckResponse(true, "Request allowed", 5, 10, 55L);
        blockedResponse = new CheckResponse(false, "Rate limit exceeded", 11, 10, 45L);

        usageResponse = new UsageResponse("test-api-key", 5, 10, 5, 55L, 60);

        LimitsResponse.LimitInfo limitInfo1 = new LimitsResponse.LimitInfo(
            "api-key-1", 5, 30, LocalDateTime.now(), LocalDateTime.now()
        );
        LimitsResponse.LimitInfo limitInfo2 = new LimitsResponse.LimitInfo(
            "api-key-2", 15, 120, LocalDateTime.now(), LocalDateTime.now()
        );
        limitsResponse = new LimitsResponse(Arrays.asList(limitInfo1, limitInfo2), 1, 2L, 0, 10);
    }

    @Test
    @DisplayName("Should create limit successfully")
    void shouldCreateLimitSuccessfully() throws Exception {
        // Given
        given(rateLimitService.createLimit(any(CreateLimitRequest.class))).willReturn(testApiLimit);

        // When & Then
        mockMvc.perform(post("/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiKey").value("test-api-key"))
                .andExpect(jsonPath("$.limitCount").value(10))
                .andExpect(jsonPath("$.windowSeconds").value(60));
    }

    @Test
    @DisplayName("Should return validation error for invalid request")
    void shouldReturnValidationErrorForInvalidRequest() throws Exception {
        // Given - Invalid request with null apiKey
        CreateLimitRequest invalidRequest = new CreateLimitRequest(null, 10, 60);

        // When & Then
        mockMvc.perform(post("/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return validation error for negative limit")
    void shouldReturnValidationErrorForNegativeLimit() throws Exception {
        // Given - Invalid request with negative limit
        CreateLimitRequest invalidRequest = new CreateLimitRequest("test-api-key", -1, 60);

        // When & Then
        mockMvc.perform(post("/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle service exception during create limit")
    void shouldHandleServiceExceptionDuringCreateLimit() throws Exception {
        // Given
        given(rateLimitService.createLimit(any(CreateLimitRequest.class)))
            .willThrow(new RuntimeException("Database error"));

        // When & Then
        mockMvc.perform(post("/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to create rate limit: Database error"));
    }

    @Test
    @DisplayName("Should allow access when within rate limit")
    void shouldAllowAccessWhenWithinRateLimit() throws Exception {
        // Given
        given(rateLimitService.checkApiAccess("test-api-key")).willReturn(allowedResponse);

        // When & Then
        mockMvc.perform(get("/check")
                .param("apiKey", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.message").value("Request allowed"))
                .andExpect(jsonPath("$.currentCount").value(5))
                .andExpect(jsonPath("$.limitCount").value(10))
                .andExpect(jsonPath("$.remainingTtl").value(55));
    }

    @Test
    @DisplayName("Should block access when rate limit exceeded")
    void shouldBlockAccessWhenRateLimitExceeded() throws Exception {
        // Given
        given(rateLimitService.checkApiAccess("test-api-key")).willReturn(blockedResponse);

        // When & Then
        mockMvc.perform(get("/check")
                .param("apiKey", "test-api-key"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded"))
                .andExpect(jsonPath("$.currentCount").value(11))
                .andExpect(jsonPath("$.limitCount").value(10))
                .andExpect(jsonPath("$.remainingTtl").value(45));
    }

    @Test
    @DisplayName("Should handle service exception during check access")
    void shouldHandleServiceExceptionDuringCheckAccess() throws Exception {
        // Given
        given(rateLimitService.checkApiAccess("test-api-key"))
            .willThrow(new RuntimeException("Redis connection error"));

        // When & Then
        mockMvc.perform(get("/check")
                .param("apiKey", "test-api-key"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.message").value("Rate limiting service error - request allowed"));
    }

    @Test
    @DisplayName("Should get usage information successfully")
    void shouldGetUsageInformationSuccessfully() throws Exception {
        // Given
        given(rateLimitService.getUsage("test-api-key")).willReturn(usageResponse);

        // When & Then
        mockMvc.perform(get("/usage")
                .param("apiKey", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.apiKey").value("test-api-key"))
                .andExpect(jsonPath("$.currentCount").value(5))
                .andExpect(jsonPath("$.limitCount").value(10))
                .andExpect(jsonPath("$.remaining").value(5))
                .andExpect(jsonPath("$.windowTtl").value(55))
                .andExpect(jsonPath("$.windowSeconds").value(60));
    }

    @Test
    @DisplayName("Should handle service exception during get usage")
    void shouldHandleServiceExceptionDuringGetUsage() throws Exception {
        // Given
        given(rateLimitService.getUsage("unknown-key"))
            .willThrow(new RuntimeException("API key not found"));

        // When & Then
        mockMvc.perform(get("/usage")
                .param("apiKey", "unknown-key"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Failed to get usage information: API key not found"));
    }

    @Test
    @DisplayName("Should remove limit successfully")
    void shouldRemoveLimitSuccessfully() throws Exception {
        // When & Then
        mockMvc.perform(delete("/limits/test-api-key"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Should handle service exception during remove limit")
    void shouldHandleServiceExceptionDuringRemoveLimit() throws Exception {
        // Given
        willThrow(new RuntimeException("API key not found")).given(rateLimitService).removeLimit("unknown-key");

        // When & Then
        mockMvc.perform(delete("/limits/unknown-key"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Failed to remove rate limit: API key not found"));
    }

    @Test
    @DisplayName("Should get all limits successfully")
    void shouldGetAllLimitsSuccessfully() throws Exception {
        // Given
        given(rateLimitService.getAllLimits(0, 10)).willReturn(limitsResponse);

        // When & Then
        mockMvc.perform(get("/limits")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.limits").isArray())
                .andExpect(jsonPath("$.limits[0].apiKey").value("api-key-1"))
                .andExpect(jsonPath("$.limits[0].limitCount").value(5))
                .andExpect(jsonPath("$.limits[1].apiKey").value("api-key-2"))
                .andExpect(jsonPath("$.limits[1].limitCount").value(15))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.currentPage").value(0))
                .andExpect(jsonPath("$.pageSize").value(10));
    }

    @Test
    @DisplayName("Should get all limits with default pagination")
    void shouldGetAllLimitsWithDefaultPagination() throws Exception {
        // Given
        given(rateLimitService.getAllLimits(0, 10)).willReturn(limitsResponse);

        // When & Then
        mockMvc.perform(get("/limits"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Should reject page size exceeding limit")
    void shouldRejectPageSizeExceedingLimit() throws Exception {
        // When & Then
        mockMvc.perform(get("/limits")
                .param("page", "0")
                .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Page size cannot exceed 100"));
    }

    @Test
    @DisplayName("Should handle service exception during get all limits")
    void shouldHandleServiceExceptionDuringGetAllLimits() throws Exception {
        // Given
        given(rateLimitService.getAllLimits(0, 10))
            .willThrow(new RuntimeException("Database connection error"));

        // When & Then
        mockMvc.perform(get("/limits")
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to get limits: Database connection error"));
    }

    @Test
    @DisplayName("Should return health check response")
    void shouldReturnHealthCheckResponse() throws Exception {
        // When & Then
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Rate Limiter Service is running"));
    }

    @Test
    @DisplayName("Should handle missing apiKey parameter")
    void shouldHandleMissingApiKeyParameter() throws Exception {
        // When & Then
        mockMvc.perform(get("/check"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle empty apiKey parameter")
    void shouldHandleEmptyApiKeyParameter() throws Exception {
        // Given
        given(rateLimitService.checkApiAccess("")).willReturn(allowedResponse);

        // When & Then
        mockMvc.perform(get("/check")
                .param("apiKey", ""))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle special characters in apiKey")
    void shouldHandleSpecialCharactersInApiKey() throws Exception {
        // Given
        String specialApiKey = "test-key-123!@#$%^&*()";
        given(rateLimitService.checkApiAccess(specialApiKey)).willReturn(allowedResponse);

        // When & Then
        mockMvc.perform(get("/check")
                .param("apiKey", specialApiKey))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle negative page parameter")
    void shouldHandleNegativePageParameter() throws Exception {
        // Given
        given(rateLimitService.getAllLimits(-1, 10)).willReturn(limitsResponse);

        // When & Then
        mockMvc.perform(get("/limits")
                .param("page", "-1")
                .param("size", "10"))
                .andExpect(status().isOk()); // Service should handle negative page internally
    }
}