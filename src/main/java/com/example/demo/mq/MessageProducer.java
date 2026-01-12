package com.example.demo.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class MessageProducer {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageProducer.class);
    private static final String RATE_LIMIT_TOPIC = "rate-limit-events";
    
    private final DefaultMQProducer producer;
    private final ObjectMapper objectMapper;
    
    public MessageProducer(DefaultMQProducer producer, ObjectMapper objectMapper) {
        this.producer = producer;
        this.objectMapper = objectMapper;
    }
    
    public void sendRateLimitEvent(RateLimitEventMessage eventMessage) {
        try {
            String messageBody = objectMapper.writeValueAsString(eventMessage);
            Message message = new Message(
                RATE_LIMIT_TOPIC,
                eventMessage.getEventType(),
                messageBody.getBytes(StandardCharsets.UTF_8)
            );
            
            SendResult result = producer.send(message);
            logger.info("Sent rate limit event for apiKey: {} - {}", 
                eventMessage.getApiKey(), result.getSendStatus());
            
        } catch (Exception e) {
            logger.error("Failed to send rate limit event for apiKey: {}", 
                eventMessage.getApiKey(), e);
        }
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