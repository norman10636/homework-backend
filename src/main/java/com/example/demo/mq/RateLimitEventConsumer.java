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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
    private static final long DEDUP_EXPIRE_SECONDS = 86400; // 24 小時

    // 統計指標
    private final AtomicLong totalBlockedCount = new AtomicLong(0);
    private final AtomicLong totalConfigChangeCount = new AtomicLong(0);
    private final AtomicLong totalConsumedCount = new AtomicLong(0);
    private final AtomicLong totalDuplicateCount = new AtomicLong(0);

    // 用於告警檢測：記錄每個 apiKey 的 blocked 次數
    private final Map<String, BlockedCounter> blockedCounters = new ConcurrentHashMap<>();

    // 告警閾值：1 分鐘內超過 100 次 blocked 觸發告警
    private static final int ALERT_THRESHOLD = 100;
    private static final long ALERT_WINDOW_MS = 60_000;

    @Override
    public void onMessage(MessageExt messageExt) {
        String msgId = messageExt.getMsgId();

        // Redis 去重檢查，校驗重複消費
        if (!tryAcquireDedupLock(msgId)) {
            log.debug("Duplicate message ignored: msgId={}", msgId);
            totalDuplicateCount.incrementAndGet();
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
            totalConsumedCount.incrementAndGet();

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
        totalBlockedCount.incrementAndGet();

        // 記錄審計日誌
        log.info("[AUDIT] BLOCKED - apiKey={}, currentCount={}, limitCount={}, windowTtl={}, message={}",
            event.getApiKey(),
            event.getCurrentCount(),
            event.getLimitCount(),
            event.getWindowTtl(),
            event.getMessage());

        // 告警檢測
        checkAndAlert(event.getApiKey());
    }

    private void handleConfigChangeEvent(RateLimitEventMessage event) {
        totalConfigChangeCount.incrementAndGet();

        // 記錄審計日誌
        log.info("[AUDIT] CONFIG_CHANGE - apiKey={}, message={}, timestamp={}",
            event.getApiKey(),
            event.getMessage(),
            event.getTimestamp());
    }

    /**
     * 檢測是否需要觸發告警
     */
    private void checkAndAlert(String apiKey) {
        long now = System.currentTimeMillis();

        BlockedCounter counter = blockedCounters.compute(apiKey, (key, existing) -> {
            if (existing == null || now - existing.windowStart > ALERT_WINDOW_MS) {
                return new BlockedCounter(now, 1);
            } else {
                existing.count++;
                return existing;
            }
        });

        if (counter.count == ALERT_THRESHOLD) {
            triggerAlert(apiKey, counter.count);
        }
    }

    private void triggerAlert(String apiKey, int count) {
        // TODO: 可對接外部告警系統（Webhook、SMS、Email 等）
        log.warn("[ALERT] High rate limit blocked detected! apiKey={}, blockedCount={} in last {} seconds",
            apiKey, count, ALERT_WINDOW_MS / 1000);
    }

    // ========== 監控指標 API ==========

    public long getTotalBlockedCount() {
        return totalBlockedCount.get();
    }

    public long getTotalConfigChangeCount() {
        return totalConfigChangeCount.get();
    }

    public long getTotalConsumedCount() {
        return totalConsumedCount.get();
    }

    public long getTotalDuplicateCount() {
        return totalDuplicateCount.get();
    }

    public Map<String, Integer> getBlockedCountsByApiKey() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        long now = System.currentTimeMillis();
        blockedCounters.forEach((apiKey, counter) -> {
            if (now - counter.windowStart <= ALERT_WINDOW_MS) {
                result.put(apiKey, counter.count);
            }
        });
        return result;
    }

    private static class BlockedCounter {
        long windowStart;
        int count;

        BlockedCounter(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
