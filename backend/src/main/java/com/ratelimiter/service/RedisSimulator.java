package com.ratelimiter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lets the dashboard toggle a "Redis is down" simulation at runtime,
 * and change the failure mode (open vs closed) dynamically.
 */
@Component
public class RedisSimulator {

    private static final Logger log = LoggerFactory.getLogger(RedisSimulator.class);
    private final AtomicBoolean simulatingDown = new AtomicBoolean(false);
    private final AtomicReference<String> failMode;

    public RedisSimulator(@Value("${rate-limiter.fail-mode:open}") String defaultFailMode) {
        this.failMode = new AtomicReference<>(defaultFailMode);
    }

    public boolean isSimulatingDown() {
        return simulatingDown.get();
    }

    public void setSimulatingDown(boolean down) {
        simulatingDown.set(down);
        if (down) {
            log.warn("[redis-simulator] Redis outage simulation ENABLED — limiter will behave as if Redis is unreachable");
        } else {
            log.info("[redis-simulator] Redis outage simulation DISABLED — back to normal operation");
        }
    }

    public String getFailMode() {
        return failMode.get();
    }

    public void setFailMode(String mode) {
        if ("open".equalsIgnoreCase(mode) || "closed".equalsIgnoreCase(mode)) {
            failMode.set(mode.toLowerCase());
            log.info("[redis-simulator] Failure mode updated to: {}", mode.toLowerCase());
        } else {
            throw new IllegalArgumentException("Invalid failure mode: " + mode);
        }
    }
}
