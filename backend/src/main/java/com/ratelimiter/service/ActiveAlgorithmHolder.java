package com.ratelimiter.service;

import com.ratelimiter.limiter.RateLimiter;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ActiveAlgorithmHolder {
    private String algorithm = "fixed"; // default to fixed window
    private final Map<String, RateLimiter> limiters;

    public ActiveAlgorithmHolder(Map<String, RateLimiter> limiters) {
        this.limiters = limiters;
    }

    public synchronized String getAlgorithm() {
        return algorithm;
    }

    public synchronized void setAlgorithm(String algorithm) {
        if (!limiters.containsKey(algorithm)) {
            throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
        this.algorithm = algorithm;
    }

    public synchronized RateLimiter getActiveLimiter() {
        return limiters.get(algorithm);
    }
}
