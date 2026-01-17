package com.example.demo.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class RateLimitEventMessage {
    
    private String apiKey;
    private String eventType;
    private Integer currentCount;
    private Integer limitCount;
    private Long windowTtl;
    private LocalDateTime timestamp;
    private String message;
    
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
}