package com.ratelimiter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lets the dashboard toggle a "Redis is down" simulation at runtime,
 * so fail-open / fail-closed behaviour can be demonstrated live
 * without actually killing the Redis process.
 *
 * RateLimitFilter checks isSimulatingDown() before calling the limiter.
 */
@Component
public class RedisSimulator {

    private static final Logger log = LoggerFactory.getLogger(RedisSimulator.class);
    private final AtomicBoolean simulatingDown = new AtomicBoolean(false);

    public boolean isSimulatingDown() {
        return simulatingDown.get();
    }

    public void simulateDown() {
        simulatingDown.set(true);
        log.warn("[redis-simulator] Redis outage simulation ENABLED — limiter will behave as if Redis is unreachable");
    }

    public void simulateUp() {
        simulatingDown.set(false);
        log.info("[redis-simulator] Redis outage simulation DISABLED — back to normal operation");
    }
}
