package com.ratelimiter.model;

public class RouteLimitConfig {
    private int limit;
    private int windowSeconds;

    public RouteLimitConfig() {}

    public RouteLimitConfig(int limit, int windowSeconds) {
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }
}
