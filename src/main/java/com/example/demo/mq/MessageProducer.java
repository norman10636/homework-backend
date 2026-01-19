package com.example.demo.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageProducer {

    private static final long CIRCUIT_BREAKER_TIMEOUT = 60000; // 1分鐘熔斷

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${app.rocketmq.topic}")
    private String topic;

    private volatile boolean mqEnabled = true;
    private volatile long lastFailTime = 0;

    public void sendRateLimitEvent(RateLimitEventMessage eventMessage) {
        // 熔斷器檢查
        if (!mqEnabled && (System.currentTimeMillis() - lastFailTime < CIRCUIT_BREAKER_TIMEOUT)) {
            log.debug("MQ circuit breaker active, skipping message for apiKey: {}", eventMessage.getApiKey());
            return;
        }

        // 使用 RocketMQTemplate 異步發送，topic:tag 格式
        String destination = topic + ":" + eventMessage.getEventType().name();

        rocketMQTemplate.asyncSend(destination, MessageBuilder.withPayload(eventMessage).build(),
            new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.debug("Sent rate limit event for apiKey: {} - {}",
                        eventMessage.getApiKey(), sendResult.getSendStatus());
                    mqEnabled = true;
                }

                @Override
                public void onException(Throwable e) {
                    log.warn("Failed to send rate limit event for apiKey: {} - {}",
                        eventMessage.getApiKey(), e.getMessage());
                    mqEnabled = false;
                    lastFailTime = System.currentTimeMillis();
                }
            }
        );
    }

    public void sendBlockedEvent(String apiKey, Integer currentCount, Integer limitCount, Long windowTtl) {
        sendRateLimitEvent(RateLimitEventMessage.blocked(apiKey, currentCount, limitCount, windowTtl));
    }

    public void sendConfigChangeEvent(String apiKey, RateLimitEventType.ConfigAction action) {
        sendRateLimitEvent(RateLimitEventMessage.configChange(apiKey, action));
    }
}
