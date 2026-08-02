package com.ratelimiter.limiter;

import com.ratelimiter.model.RouteLimitConfig;

public interface RateLimiter {
    boolean isAllowed(String clientId, String route, RouteLimitConfig config);
}
