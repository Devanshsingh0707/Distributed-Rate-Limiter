package com.ratelimiter.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

@Service
public class StatsTracker {

    private final LongAdder allowed = new LongAdder();
    private final LongAdder blocked = new LongAdder();
    // Counts requests that bypassed the limiter because Redis was unreachable (fail-open)
    private final LongAdder failedBypass = new LongAdder();
    
    // Circular buffer for latency (last 100 requests)
    private final Queue<Long> recentLatencies = new ConcurrentLinkedQueue<>();
    private static final int MAX_LATENCY_SAMPLES = 100;

    // Sliding window timestamps for RPS calculation (last 5 seconds)
    private final ConcurrentLinkedDeque<Long> requestTimestamps = new ConcurrentLinkedDeque<>();
    private static final long RPS_WINDOW_MS = 5000L;

    // Activity feed: last 50 requests
    private final List<LogEntry> activityFeed = new LinkedList<>();
    private static final int MAX_FEED_SIZE = 50;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter
            .ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    public static class LogEntry {
        private final String timestamp;
        private final String clientId;
        private final String route;
        private final boolean allowed;
        private final int statusCode;
        private final long latencyMs;

        public LogEntry(String timestamp, String clientId, String route, boolean allowed, int statusCode, long latencyMs) {
            this.timestamp = timestamp;
            this.clientId = clientId;
            this.route = route;
            this.allowed = allowed;
            this.statusCode = statusCode;
            this.latencyMs = latencyMs;
        }

        public String getTimestamp() { return timestamp; }
        public String getClientId() { return clientId; }
        public String getRoute() { return route; }
        public boolean isAllowed() { return allowed; }
        public int getStatusCode() { return statusCode; }
        public long getLatencyMs() { return latencyMs; }
    }

    /**
     * Records a request decision made when Redis was unreachable and fail-mode=open.
     * Increments the failedBypass counter so outage impact is observable.
     */
    public void recordBypass(String clientId, String route, long latencyMs) {
        failedBypass.increment();
        record(clientId, route, true, 200, latencyMs);
    }

    public void record(String clientId, String route, boolean allowedRequest, int statusCode, long latencyMs) {
        if (allowedRequest) {
            allowed.increment();
        } else {
            blocked.increment();
        }

        // Latency tracking
        recentLatencies.offer(latencyMs);
        while (recentLatencies.size() > MAX_LATENCY_SAMPLES) {
            recentLatencies.poll();
        }

        // RPS tracking
        long now = System.currentTimeMillis();
        requestTimestamps.offerLast(now);
        pruneRpsTimestamps(now);

        // Activity feed logging
        String formattedTime = timeFormatter.format(Instant.ofEpochMilli(now));
        LogEntry entry = new LogEntry(formattedTime, clientId, route, allowedRequest, statusCode, latencyMs);
        synchronized (activityFeed) {
            activityFeed.add(0, entry); // insert newest first
            if (activityFeed.size() > MAX_FEED_SIZE) {
                activityFeed.remove(activityFeed.size() - 1);
            }
        }
    }

    private void pruneRpsTimestamps(long now) {
        long boundary = now - RPS_WINDOW_MS;
        while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() < boundary) {
            requestTimestamps.pollFirst();
        }
    }

    public synchronized void reset() {
        allowed.reset();
        blocked.reset();
        failedBypass.reset();
        recentLatencies.clear();
        requestTimestamps.clear();
        synchronized (activityFeed) {
            activityFeed.clear();
        }
    }

    public Summary getSummary() {
        long now = System.currentTimeMillis();
        pruneRpsTimestamps(now);

        long allowedCount = allowed.sum();
        long blockedCount = blocked.sum();
        long total = allowedCount + blockedCount;

        // RPS: total requests in last 5 seconds divided by 5
        double rps = requestTimestamps.size() / (RPS_WINDOW_MS / 1000.0);

        // Avg Latency
        double avgLatency = 0.0;
        int size = recentLatencies.size();
        if (size > 0) {
            long sum = 0;
            for (Long lat : recentLatencies) {
                sum += lat;
            }
            avgLatency = (double) sum / size;
        }

        return new Summary(allowedCount, blockedCount, total, rps, avgLatency, failedBypass.sum());
    }

    public List<LogEntry> getFeed() {
        synchronized (activityFeed) {
            return new ArrayList<>(activityFeed); // return copy
        }
    }

    public static class Summary {
        private final long allowed;
        private final long blocked;
        private final long totalRequests;
        private final double currentRPS;
        private final double avgLatencyMs;
        // Requests allowed through because Redis was down (fail-open bypasses)
        private final long failedBypassCount;

        public Summary(long allowed, long blocked, long totalRequests, double currentRPS, double avgLatencyMs, long failedBypassCount) {
            this.allowed = allowed;
            this.blocked = blocked;
            this.totalRequests = totalRequests;
            this.currentRPS = currentRPS;
            this.avgLatencyMs = avgLatencyMs;
            this.failedBypassCount = failedBypassCount;
        }

        public long getAllowed() { return allowed; }
        public long getBlocked() { return blocked; }
        public long getTotalRequests() { return totalRequests; }
        public double getCurrentRPS() { return currentRPS; }
        public double getAvgLatencyMs() { return avgLatencyMs; }
        public long getFailedBypassCount() { return failedBypassCount; }
    }
}
