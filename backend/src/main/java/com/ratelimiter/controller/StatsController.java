package com.ratelimiter.controller;

import com.ratelimiter.limiter.RateLimiter;
import com.ratelimiter.model.RouteLimitConfig;
import com.ratelimiter.model.RouteLimitConfigRegistry;
import com.ratelimiter.service.ActiveAlgorithmHolder;
import com.ratelimiter.service.StatsTracker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/stats")
@CrossOrigin(origins = "*")
public class StatsController {

    private final ActiveAlgorithmHolder algorithmHolder;
    private final StatsTracker statsTracker;
    private final RouteLimitConfigRegistry configRegistry;
    private final StringRedisTemplate redisTemplate;

    @Value("${rate-limiter.fail-mode:open}")
    private String failMode;

    public StatsController(ActiveAlgorithmHolder algorithmHolder,
                           StatsTracker statsTracker,
                           RouteLimitConfigRegistry configRegistry,
                           StringRedisTemplate redisTemplate) {
        this.algorithmHolder = algorithmHolder;
        this.statsTracker = statsTracker;
        this.configRegistry = configRegistry;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/algorithm")
    public ResponseEntity<Map<String, String>> setAlgorithm(@RequestBody Map<String, String> request) {
        String algorithm = request.get("algorithm");
        if (algorithm == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "algorithm is required"));
        }
        try {
            algorithmHolder.setAlgorithm(algorithm);
            return ResponseEntity.ok(Map.of("algorithm", algorithmHolder.getAlgorithm()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/algorithm")
    public ResponseEntity<Map<String, String>> getAlgorithm() {
        return ResponseEntity.ok(Map.of("algorithm", algorithmHolder.getAlgorithm()));
    }

    @GetMapping("/summary")
    public ResponseEntity<StatsTracker.Summary> getSummary() {
        return ResponseEntity.ok(statsTracker.getSummary());
    }

    @GetMapping("/feed")
    public ResponseEntity<List<StatsTracker.LogEntry>> getFeed() {
        return ResponseEntity.ok(statsTracker.getFeed());
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        // Clear in-memory stats
        statsTracker.reset();
        
        // Clear rate limiter keys from Redis
        try {
            Set<String> keys = redisTemplate.keys("rl:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            return ResponseEntity.ok(Map.of("message", "Statistics and Redis keys reset successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "message", "In-memory statistics reset, but failed to flush Redis keys",
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Returns Redis connectivity, the configured fail-mode, and how many requests
     * have bypassed rate limiting due to Redis being unreachable (fail-open only).
     * Use this to verify the limiter is guarding traffic and spot outage impact.
     */
    @GetMapping("/redis-status")
    public ResponseEntity<Map<String, Object>> redisStatus() {
        boolean connected;
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            connected = true;
        } catch (Exception e) {
            connected = false;
        }
        long bypass = statsTracker.getSummary().getFailedBypassCount();
        return ResponseEntity.ok(Map.of(
            "redisConnected",    connected,
            "failMode",         failMode,
            "failedBypassCount", bypass,
            "note", bypass > 0
                ? bypass + " request(s) bypassed the limiter because Redis was unreachable"
                : "No bypass events recorded"
        ));
    }

    /** Kept for backward compatibility — prefer /stats/redis-status for full detail. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean connected;
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            connected = true;
        } catch (Exception e) {
            connected = false;
        }
        return ResponseEntity.ok(Map.of("redisConnected", connected));
    }

    @PostMapping("/stress")
    public ResponseEntity<?> stress(@RequestBody StressRequest request) {
        RouteLimitConfig config = configRegistry.getConfig(request.getRoute());
        if (config == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid route: " + request.getRoute()));
        }

        int allowed = 0;
        int blocked = 0;
        RateLimiter limiter = algorithmHolder.getActiveLimiter();

        for (int i = 0; i < request.getCount(); i++) {
            long startTime = System.currentTimeMillis();
            boolean isAllowed = false;
            boolean redisError = false;
            
            try {
                isAllowed = limiter.isAllowed(request.getClientId(), request.getRoute(), config);
            } catch (Exception e) {
                redisError = true;
            }

            long latency = System.currentTimeMillis() - startTime;

            if (redisError) {
                if ("open".equalsIgnoreCase(failMode)) {
                    allowed++;
                    statsTracker.record(request.getClientId(), request.getRoute(), true, 200, latency);
                } else {
                    blocked++;
                    statsTracker.record(request.getClientId(), request.getRoute(), false, 503, latency);
                }
            } else {
                if (isAllowed) {
                    allowed++;
                    statsTracker.record(request.getClientId(), request.getRoute(), true, 200, latency);
                } else {
                    blocked++;
                    statsTracker.record(request.getClientId(), request.getRoute(), false, 429, latency);
                }
            }
        }

        return ResponseEntity.ok(Map.of(
            "allowed", allowed,
            "blocked", blocked,
            "total", request.getCount()
        ));
    }

    public static class StressRequest {
        private String clientId;
        private String route;
        private int count;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getRoute() { return route; }
        public void setRoute(String route) { this.route = route; }

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
}
