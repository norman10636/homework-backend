package com.example.demo.mq;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public class RateLimitEventType {

    @Getter
    @RequiredArgsConstructor
    public enum Event {
        BLOCKED("Request blocked due to rate limit exceeded"),
        CONFIG_CHANGE("Rate limit configuration changed");

        private final String defaultMessage;
    }

    @Getter
    @RequiredArgsConstructor
    public enum ConfigAction {
        CREATED("created"),
        UPDATED("updated"),
        DELETED("deleted");

        private final String action;

        public String toMessage() {
            return "Rate limit configuration " + action;
        }
    }
}
