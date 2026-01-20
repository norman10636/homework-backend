package com.example.demo.mq;

import com.example.demo.common.RedisKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@RocketMQMessageListener(
    topic = "${app.rocketmq.topic}",
    consumerGroup = "${app.rocketmq.consumer.group}",
    messageModel = MessageModel.CLUSTERING,
    consumeMode = ConsumeMode.CONCURRENTLY,
    consumeThreadNumber = 4
)
public class RateLimitEventConsumer implements RocketMQListener<MessageExt> {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Redis 去重配置
    private static final long DEDUP_EXPIRE_SECONDS = 3600; // 1 小時

    @Override
    public void onMessage(MessageExt messageExt) {
        String msgId = messageExt.getMsgId();

        // Redis 去重檢查，校驗重複消費
        if (!tryAcquireDedupLock(msgId)) {
            log.debug("Duplicate message ignored: msgId={}", msgId);
            return;
        }

        try {
            // 解析訊息
            String body = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            RateLimitEventMessage message = objectMapper.readValue(body, RateLimitEventMessage.class);

            log.debug("Received message: msgId={}, eventType={}, apiKey={}",
                msgId, message.getEventType(), message.getApiKey());

            // 處理訊息
            processEvent(message);

        } catch (Exception e) {
            log.error("Failed to process message: msgId={}, error={}", msgId, e.getMessage(), e);
            // 處理失敗，刪除去重 key，允許重試
            releaseDedupLock(msgId);
            throw new RuntimeException("Message processing failed", e);
        }
    }

    /**
     * 嘗試獲取去重鎖
     * @return true 表示是新訊息，false 表示重複訊息
     */
    private boolean tryAcquireDedupLock(String msgId) {
        try {
            String dedupKey = RedisKey.mqDedup(msgId);
            Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "1", DEDUP_EXPIRE_SECONDS, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(isNew);
        } catch (Exception e) {
            // Redis 異常時，允許處理（降級為 at-least-once）
            log.warn("Redis dedup check failed, allowing message: msgId={}, error={}", msgId, e.getMessage());
            return true;
        }
    }

    /**
     * 釋放去重鎖（處理失敗時調用，允許重試）
     */
    private void releaseDedupLock(String msgId) {
        try {
            String dedupKey = RedisKey.mqDedup(msgId);
            redisTemplate.delete(dedupKey);
        } catch (Exception e) {
            log.warn("Failed to release dedup lock: msgId={}, error={}", msgId, e.getMessage());
        }
    }

    private void processEvent(RateLimitEventMessage message) {
        if (message.getEventType() == null) {
            log.warn("Event type is null, ignoring message");
            return;
        }

        switch (message.getEventType()) {
            case BLOCKED -> handleBlockedEvent(message);
            case CONFIG_CHANGE -> handleConfigChangeEvent(message);
        }
    }

    private void handleBlockedEvent(RateLimitEventMessage event) {
        log.info("[AUDIT] BLOCKED - apiKey={}, currentCount={}, limitCount={}, windowTtl={}, message={}",
            event.getApiKey(),
            event.getCurrentCount(),
            event.getLimitCount(),
            event.getWindowTtl(),
            event.getMessage());
    }

    private void handleConfigChangeEvent(RateLimitEventMessage event) {
        log.info("[AUDIT] CONFIG_CHANGE - apiKey={}, message={}, timestamp={}",
            event.getApiKey(),
            event.getMessage(),
            event.getTimestamp());
    }
}
