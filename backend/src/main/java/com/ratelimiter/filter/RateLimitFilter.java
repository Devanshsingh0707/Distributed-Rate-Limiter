package com.ratelimiter.filter;

import com.ratelimiter.limiter.RateLimiter;
import com.ratelimiter.model.RouteLimitConfig;
import com.ratelimiter.model.RouteLimitConfigRegistry;
import com.ratelimiter.service.ActiveAlgorithmHolder;
import com.ratelimiter.service.RedisSimulator;
import com.ratelimiter.service.StatsTracker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ActiveAlgorithmHolder algorithmHolder;
    private final RouteLimitConfigRegistry configRegistry;
    private final StatsTracker statsTracker;
    private final RedisSimulator redisSimulator;

    public RateLimitFilter(ActiveAlgorithmHolder algorithmHolder,
                           RouteLimitConfigRegistry configRegistry,
                           StatsTracker statsTracker,
                           RedisSimulator redisSimulator) {
        this.algorithmHolder = algorithmHolder;
        this.configRegistry = configRegistry;
        this.statsTracker = statsTracker;
        this.redisSimulator = redisSimulator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Only apply rate limiting to /api/** endpoints
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();
        
        // 1. Extract Client ID
        String clientId = request.getHeader("X-Client-Id");
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = request.getParameter("clientId");
        }
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = "anonymous";
        }

        // 2. Lookup Route Configuration
        RouteLimitConfig config = configRegistry.getConfig(path);
        if (config == null) {
            // Unconfigured path, pass through
            filterChain.doFilter(request, response);
            return;
        }

        boolean allowed = false;
        boolean redisErrorOccurred = false;

        // If the dashboard is simulating a Redis outage, skip the call entirely
        if (redisSimulator.isSimulatingDown()) {
            log.warn("[redis-simulator] Treating request as Redis failure (simulation active)");
            redisErrorOccurred = true;
        } else {
            try {
                RateLimiter activeLimiter = algorithmHolder.getActiveLimiter();
                allowed = activeLimiter.isAllowed(clientId, path, config);
            } catch (Exception e) {
                log.error("Redis connection error during rate limiting evaluation: {}", e.getMessage());
                redisErrorOccurred = true;
            }
        }

        long latency = System.currentTimeMillis() - startTime;

        if (redisErrorOccurred) {
            if ("open".equalsIgnoreCase(redisSimulator.getFailMode())) {
                // Fail-open: allow request, but track it as a bypass so the outage is observable
                log.warn("[fail-open] Redis unreachable — allowing request to {} without rate-limit check", path);
                filterChain.doFilter(request, response);
                statsTracker.recordBypass(clientId, path, System.currentTimeMillis() - startTime);
            } else {
                // Fail-closed: reject with 503 so the API is not unguarded during outage
                log.warn("[fail-closed] Redis unreachable — blocking request to {} with 503", path);
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limiter unavailable (Redis unreachable). Failing closed.\",\"status\":503}");
                statsTracker.record(clientId, path, false, HttpServletResponse.SC_SERVICE_UNAVAILABLE, System.currentTimeMillis() - startTime);
            }
            return;
        }

        if (allowed) {
            // Allowed: pass through the filter chain
            filterChain.doFilter(request, response);
            long totalLatency = System.currentTimeMillis() - startTime;
            statsTracker.record(clientId, path, true, response.getStatus(), totalLatency);
        } else {
            // Blocked: return 429 Too Many Requests
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(String.format(
                "{\"error\":\"Too Many Requests\",\"limit\":%d,\"windowSeconds\":%d,\"status\":429}",
                config.getLimit(),
                config.getWindowSeconds()
            ));
            long totalLatency = System.currentTimeMillis() - startTime;
            statsTracker.record(clientId, path, false, 429, totalLatency);
        }
    }
}
