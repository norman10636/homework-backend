package com.example.demo.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class CreateLimitRequest {
    
    @NotBlank(message = "API key cannot be blank")
    private String apiKey;
    
    @Positive(message = "Limit must be positive")
    private Integer limit;
    
    @Positive(message = "Window seconds must be positive")
    private Integer windowSeconds;
    
    public CreateLimitRequest() {}
    
    public CreateLimitRequest(String apiKey, Integer limit, Integer windowSeconds) {
        this.apiKey = apiKey;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public Integer getLimit() {
        return limit;
    }
    
    public void setLimit(Integer limit) {
        this.limit = limit;
    }
    
    public Integer getWindowSeconds() {
        return windowSeconds;
    }
    
    public void setWindowSeconds(Integer windowSeconds) {
        this.windowSeconds = windowSeconds;
    }
}