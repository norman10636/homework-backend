package com.example.demo.mq;

import java.time.LocalDateTime;

public class RateLimitEventMessage {
    
    private String apiKey;
    private String eventType;
    private Integer currentCount;
    private Integer limitCount;
    private Long windowTtl;
    private LocalDateTime timestamp;
    private String message;
    
    public RateLimitEventMessage() {
        this.timestamp = LocalDateTime.now();
    }
    
    public RateLimitEventMessage(String apiKey, String eventType, Integer currentCount, 
                                Integer limitCount, Long windowTtl, String message) {
        this.apiKey = apiKey;
        this.eventType = eventType;
        this.currentCount = currentCount;
        this.limitCount = limitCount;
        this.windowTtl = windowTtl;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
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
    
    public Long getWindowTtl() {
        return windowTtl;
    }
    
    public void setWindowTtl(Long windowTtl) {
        this.windowTtl = windowTtl;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}