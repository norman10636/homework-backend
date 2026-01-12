package com.example.demo.controller;

import com.example.demo.model.ApiLimit;
import com.example.demo.model.dto.CheckResponse;
import com.example.demo.model.dto.CreateLimitRequest;
import com.example.demo.model.dto.LimitsResponse;
import com.example.demo.model.dto.UsageResponse;
import com.example.demo.service.RateLimitService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/")
public class RateLimitController {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitController.class);
    
    private final RateLimitService rateLimitService;
    
    public RateLimitController(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }
    
    @PostMapping("/limits")
    public ResponseEntity<?> createLimit(@Valid @RequestBody CreateLimitRequest request) {
        try {
            ApiLimit apiLimit = rateLimitService.createLimit(request);
            logger.info("Created rate limit for apiKey: {}", request.getApiKey());
            return ResponseEntity.status(HttpStatus.CREATED).body(apiLimit);
        } catch (Exception e) {
            logger.error("Error creating limit for apiKey: {}", request.getApiKey(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to create rate limit: " + e.getMessage());
        }
    }
    
    @GetMapping("/check")
    public ResponseEntity<CheckResponse> checkApiAccess(@RequestParam String apiKey) {
        try {
            CheckResponse response = rateLimitService.checkApiAccess(apiKey);
            
            if (!response.isAllowed()) {
                logger.info("Request blocked for apiKey: {} - {}", apiKey, response.getMessage());
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
            }
            
            logger.debug("Request allowed for apiKey: {}", apiKey);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error checking API access for apiKey: {}", apiKey, e);
            CheckResponse errorResponse = new CheckResponse(true, "Rate limiting service error - request allowed");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    @GetMapping("/usage")
    public ResponseEntity<?> getUsage(@RequestParam String apiKey) {
        try {
            UsageResponse response = rateLimitService.getUsage(apiKey);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting usage for apiKey: {}", apiKey, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Failed to get usage information: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/limits/{apiKey}")
    public ResponseEntity<?> removeLimit(@PathVariable String apiKey) {
        try {
            rateLimitService.removeLimit(apiKey);
            logger.info("Removed rate limit for apiKey: {}", apiKey);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error removing limit for apiKey: {}", apiKey, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Failed to remove rate limit: " + e.getMessage());
        }
    }
    
    @GetMapping("/limits")
    public ResponseEntity<?> getAllLimits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            if (size > 100) {
                return ResponseEntity.badRequest().body("Page size cannot exceed 100");
            }
            
            LimitsResponse response = rateLimitService.getAllLimits(page, size);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error getting all limits", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to get limits: " + e.getMessage());
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Rate Limiter Service is running");
    }
}