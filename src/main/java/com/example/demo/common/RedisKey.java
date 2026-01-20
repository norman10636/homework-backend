package com.example.demo.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisKey {

    // ==================== Prefixes ====================

    private static final String CONFIG_CACHE_PREFIX = "cache:config:";
    private static final String RATE_LIMIT_PREFIX = "rate:limit:cnt:";
    private static final String MQ_DEDUP_PREFIX = "mq:dedup:";

    // ==================== Key Builders ====================

    /**
     * 配置緩存 Key
     * 格式: cache:config:{apiKey}
     */
    public static String configCache(String apiKey) {
        return CONFIG_CACHE_PREFIX + apiKey;
    }

    /**
     * 限流計數器 Key
     * 格式: rate:limit:cnt:{apiKey}
     */
    public static String rateLimitCounter(String apiKey) {
        return RATE_LIMIT_PREFIX + apiKey;
    }

    /**
     * MQ 去重 Key
     * 格式: mq:dedup:{msgId}
     */
    public static String mqDedup(String msgId) {
        return MQ_DEDUP_PREFIX + msgId;
    }

}
