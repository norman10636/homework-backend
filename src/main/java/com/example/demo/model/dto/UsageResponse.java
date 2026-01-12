package com.example.demo.model.dto;

public class UsageResponse {
    
    private String apiKey;
    private Integer currentCount;
    private Integer limitCount;
    private Integer remaining;
    private Long windowTtl;
    private Integer windowSeconds;
    
    public UsageResponse() {}
    
    public UsageResponse(String apiKey, Integer currentCount, Integer limitCount, Integer remaining, Long windowTtl, Integer windowSeconds) {
        this.apiKey = apiKey;
        this.currentCount = currentCount;
        this.limitCount = limitCount;
        this.remaining = remaining;
        this.windowTtl = windowTtl;
        this.windowSeconds = windowSeconds;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public Integer getCurrentCount() {
        return currentCount;
    }
    
    public void setCurrentCount(Integer currentCount) {
        this.currentCount = currentCount;
    }
    
    public Integer getLimitCount() {
        return limitCount;
    }
    
    public void setLimitCount(Integer limitCount) {
        this.limitCount = limitCount;
    }
    
    public Integer getRemaining() {
        return remaining;
    }
    
    public void setRemaining(Integer remaining) {
        this.remaining = remaining;
    }
    
    public Long getWindowTtl() {
        return windowTtl;
    }
    
    public void setWindowTtl(Long windowTtl) {
        this.windowTtl = windowTtl;
    }
    
    public Integer getWindowSeconds() {
        return windowSeconds;
    }
    
    public void setWindowSeconds(Integer windowSeconds) {
        this.windowSeconds = windowSeconds;
    }
}