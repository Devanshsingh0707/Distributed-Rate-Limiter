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
    private final RedisSimulator redisSimulator;

    public StatsController(ActiveAlgorithmHolder algorithmHolder,
                           StatsTracker statsTracker,
                           RouteLimitConfigRegistry configRegistry,
                           StringRedisTemplate redisTemplate,
                           RedisSimulator redisSimulator) {
        this.algorithmHolder = algorithmHolder;
        this.statsTracker = statsTracker;
        this.configRegistry = configRegistry;
        this.redisTemplate = redisTemplate;
        this.redisSimulator = redisSimulator;
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
            "redisConnected",    connected && !redisSimulator.isSimulatingDown(),
            "failMode",         redisSimulator.getFailMode(),
            "failedBypassCount", bypass,
            "simulatingDown",    redisSimulator.isSimulatingDown(),
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
        return ResponseEntity.ok(Map.of("redisConnected", connected && !redisSimulator.isSimulatingDown()));
    }

    @PostMapping("/redis-simulation")
    public ResponseEntity<Map<String, Boolean>> setRedisSimulation(@RequestBody Map<String, Boolean> request) {
        Boolean simulatingDown = request.get("simulatingDown");
        if (simulatingDown == null) {
            return ResponseEntity.badRequest().build();
        }
        redisSimulator.setSimulatingDown(simulatingDown);
        return ResponseEntity.ok(Map.of("simulatingDown", redisSimulator.isSimulatingDown()));
    }

    @PostMapping("/fail-mode")
    public ResponseEntity<Map<String, String>> setFailMode(@RequestBody Map<String, String> request) {
        String failMode = request.get("failMode");
        if (failMode == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "failMode is required"));
        }
        try {
            redisSimulator.setFailMode(failMode);
            return ResponseEntity.ok(Map.of("failMode", redisSimulator.getFailMode()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/load-test")
    public ResponseEntity<Map<String, Object>> runLoadTest(jakarta.servlet.http.HttpServletRequest httpRequest,
                                                           @RequestBody Map<String, Object> body) {
        int requests = ((Number) body.getOrDefault("requests", 1000)).intValue();
        int concurrency = ((Number) body.getOrDefault("concurrency", 20)).intValue();
        String targetRoute = (String) body.getOrDefault("endpoint", "/api/search");
        
        int port = httpRequest.getLocalPort();
        String targetUrl = "http://localhost:" + port + targetRoute;
        
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(2))
                    .build();

            java.util.concurrent.atomic.LongAdder allowed = new java.util.concurrent.atomic.LongAdder();
            java.util.concurrent.atomic.LongAdder blocked = new java.util.concurrent.atomic.LongAdder();
            java.util.concurrent.atomic.LongAdder errors = new java.util.concurrent.atomic.LongAdder();
            java.util.List<Long> latencies = java.util.Collections.synchronizedList(new java.util.ArrayList<>(requests));

            java.util.List<java.util.concurrent.Callable<Void>> tasks = new java.util.ArrayList<>(requests);
            for (int i = 0; i < requests; i++) {
                tasks.add(() -> {
                    java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(targetUrl))
                            .header("X-Client-Id", "dashboard-load-test")
                            .GET()
                            .timeout(java.time.Duration.ofSeconds(5))
                            .build();
                    long t0 = System.currentTimeMillis();
                    try {
                        java.net.http.HttpResponse<Void> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.discarding());
                        long latency = System.currentTimeMillis() - t0;
                        latencies.add(latency);
                        if (resp.statusCode() == 200) {
                            allowed.increment();
                        } else if (resp.statusCode() == 429) {
                            blocked.increment();
                        } else {
                            errors.increment();
                        }
                    } catch (Exception e) {
                        latencies.add(System.currentTimeMillis() - t0);
                        errors.increment();
                    }
                    return null;
                });
            }

            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(concurrency);
            long wallStart = System.currentTimeMillis();
            java.util.List<java.util.concurrent.Future<Void>> futures = pool.invokeAll(tasks);
            for (java.util.concurrent.Future<Void> f : futures) {
                try { f.get(); } catch (Exception ignored) {}
            }
            pool.shutdown();
            pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
            long wallTime = System.currentTimeMillis() - wallStart;

            java.util.List<Long> sorted = new java.util.ArrayList<>(latencies);
            java.util.Collections.sort(sorted);
            long p50 = percentile(sorted, 50);
            long p99 = percentile(sorted, 99);
            long max = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1);

            return ResponseEntity.ok(Map.of(
                "totalSent", requests,
                "allowed", allowed.sum(),
                "blocked", blocked.sum(),
                "errors", errors.sum(),
                "p50", p50,
                "p99", p99,
                "max", max,
                "wallTimeMs", wallTime,
                "url", targetUrl
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private static long percentile(java.util.List<Long> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
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
