package com.ratelimiter.limiter;

import com.ratelimiter.model.RouteLimitConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component("fixed")
public class FixedWindowLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> fixedWindowScript;

    public FixedWindowLimiter(StringRedisTemplate redisTemplate,
                              @Qualifier("fixedWindowScript") DefaultRedisScript<Long> fixedWindowScript) {
        this.redisTemplate = redisTemplate;
        this.fixedWindowScript = fixedWindowScript;
    }

    @Override
    public boolean isAllowed(String clientId, String route, RouteLimitConfig config) {
        long windowStartMinute = System.currentTimeMillis() / 60000;
        String key = String.format("rl:fixed:%s:%s:%d", clientId, route, windowStartMinute);
        List<String> keys = Collections.singletonList(key);
        
        Long result = redisTemplate.execute(
            fixedWindowScript,
            keys,
            String.valueOf(config.getLimit()),
            String.valueOf(config.getWindowSeconds())
        );
        
        return result != null && result == 1L;
    }
}
