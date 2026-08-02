package com.ratelimiter.limiter;

import com.ratelimiter.model.RouteLimitConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component("sliding")
public class SlidingWindowLogLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> slidingWindowLogScript;

    public SlidingWindowLogLimiter(StringRedisTemplate redisTemplate,
                                   @Qualifier("slidingWindowLogScript") DefaultRedisScript<Long> slidingWindowLogScript) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowLogScript = slidingWindowLogScript;
    }

    @Override
    public boolean isAllowed(String clientId, String route, RouteLimitConfig config) {
        String key = String.format("rl:sliding:%s:%s", clientId, route);
        List<String> keys = Collections.singletonList(key);
        
        long windowMs = config.getWindowSeconds() * 1000L;
        String requestId = UUID.randomUUID().toString();
        
        Long result = redisTemplate.execute(
            slidingWindowLogScript,
            keys,
            String.valueOf(config.getLimit()),
            String.valueOf(windowMs),
            String.valueOf(System.currentTimeMillis()),
            requestId
        );
        
        return result != null && result == 1L;
    }
}
