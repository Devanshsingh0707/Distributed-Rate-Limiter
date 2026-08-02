package com.ratelimiter.limiter;

import com.ratelimiter.model.RouteLimitConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component("token")
public class TokenBucketLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> tokenBucketScript;

    public TokenBucketLimiter(StringRedisTemplate redisTemplate,
                              @Qualifier("tokenBucketScript") DefaultRedisScript<Long> tokenBucketScript) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = tokenBucketScript;
    }

    @Override
    public boolean isAllowed(String clientId, String route, RouteLimitConfig config) {
        String key = String.format("rl:bucket:%s:%s", clientId, route);
        List<String> keys = Collections.singletonList(key);
        
        double refillRate = (double) config.getLimit() / config.getWindowSeconds();
        
        Long result = redisTemplate.execute(
            tokenBucketScript,
            keys,
            String.valueOf(config.getLimit()),
            String.format("%.8f", refillRate),
            "1",
            String.valueOf(System.currentTimeMillis())
        );
        
        return result != null && result == 1L;
    }
}
