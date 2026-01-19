package com.example.demo.mq;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class RateLimitEventMessage {

    private String apiKey;
    private RateLimitEventType.Event eventType;
    private Integer currentCount;
    private Integer limitCount;
    private Long windowTtl;
    private LocalDateTime timestamp;
    private String message;

    public RateLimitEventMessage(String apiKey, RateLimitEventType.Event eventType, Integer currentCount,
                                Integer limitCount, Long windowTtl, String message) {
        this.apiKey = apiKey;
        this.eventType = eventType;
        this.currentCount = currentCount;
        this.limitCount = limitCount;
        this.windowTtl = windowTtl;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    /**
     * 建立 BLOCKED 事件
     */
    public static RateLimitEventMessage blocked(String apiKey, Integer currentCount,
                                                 Integer limitCount, Long windowTtl) {
        return new RateLimitEventMessage(
            apiKey,
            RateLimitEventType.Event.BLOCKED,
            currentCount,
            limitCount,
            windowTtl,
            RateLimitEventType.Event.BLOCKED.getDefaultMessage()
        );
    }

    /**
     * 建立 CONFIG_CHANGE 事件
     */
    public static RateLimitEventMessage configChange(String apiKey, RateLimitEventType.ConfigAction action) {
        return new RateLimitEventMessage(
            apiKey,
            RateLimitEventType.Event.CONFIG_CHANGE,
            null,
            null,
            null,
            action.toMessage()
        );
    }
}
