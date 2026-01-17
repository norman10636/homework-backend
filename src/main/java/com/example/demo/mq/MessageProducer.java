package com.example.demo.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageProducer {
    
    private static final String RATE_LIMIT_TOPIC = "rate-limit-events";
    private volatile boolean mqEnabled = true;
    private volatile long lastFailTime = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 1分鐘熔斷
    
    private final DefaultMQProducer producer;
    private final ObjectMapper objectMapper;
    
    public void sendRateLimitEvent(RateLimitEventMessage eventMessage) {
        // 熔斷器檢查
        if (!mqEnabled && (System.currentTimeMillis() - lastFailTime < CIRCUIT_BREAKER_TIMEOUT)) {
            log.debug("MQ circuit breaker active, skipping message for apiKey: {}", eventMessage.getApiKey());
            return;
        }
        
        // 使用異步執行，不阻塞主流程
        new Thread(() -> {
            try {
                String messageBody = objectMapper.writeValueAsString(eventMessage);
                Message message = new Message(
                    RATE_LIMIT_TOPIC,
                    eventMessage.getEventType(),
                    messageBody.getBytes(StandardCharsets.UTF_8)
                );
                
                SendResult result = producer.send(message);
                log.debug("Sent rate limit event for apiKey: {} - {}", 
                    eventMessage.getApiKey(), result.getSendStatus());
                
                // 成功後重置熔斷器
                mqEnabled = true;
                
            } catch (Exception e) {
                log.warn("Failed to send rate limit event for apiKey: {} - {}", 
                    eventMessage.getApiKey(), e.getMessage());
                
                // 觸發熔斷器
                mqEnabled = false;
                lastFailTime = System.currentTimeMillis();
            }
        }).start();
    }
    
    public void sendBlockedEvent(String apiKey, Integer currentCount, Integer limitCount, Long windowTtl) {
        RateLimitEventMessage event = new RateLimitEventMessage(
            apiKey, 
            "BLOCKED", 
            currentCount, 
            limitCount, 
            windowTtl, 
            "Request blocked due to rate limit exceeded"
        );
        sendRateLimitEvent(event);
    }
    
    public void sendConfigChangeEvent(String apiKey, String action) {
        RateLimitEventMessage event = new RateLimitEventMessage(
            apiKey, 
            "CONFIG_CHANGE", 
            null, 
            null, 
            null, 
            "Rate limit configuration " + action
        );
        sendRateLimitEvent(event);
    }
}