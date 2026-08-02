# Technical Documentation: Distributed Rate Limiter Service

---

## 1. Executive Summary

This project implements a high-performance, distributed rate limiter service designed to protect APIs from resource abuse, brute-force attacks, and DDoS attempts. The core architecture replicates production-grade rate limiting systems used by industry leaders like Stripe, GitHub, and Cloudflare. 

Instead of tracking requests in-memory (which fails when scaling horizontally), this service delegates state management to **Redis**. To guarantee thread safety and eliminate race conditions under high concurrency, all rate-limiting checks and write operations are executed server-side as atomic **Redis Lua scripts**.

The system features three swappable algorithms (Fixed Window, Token Bucket, and Sliding Window Log), a servlet filter integration, resilience handles (Fail-Open and Fail-Closed modes), a native concurrent load-testing tool, and a real-time dark-themed monitoring console.

---

## 2. System Architecture

The request processing pipeline is designed to be lightweight, intercepting and evaluating incoming calls before they hit heavy business logic controllers.

```
       [ Client Request: k6 / Browser / LoadTestRunner ]
                             │
                             ▼  HTTP Request (Header: X-Client-Id)
                  ┌──────────────────────┐
                  │  Spring Boot Filter  │ 
                  │  (RateLimitFilter)   │ 
                  └───┬──────────────┬───┘
                      │              │
    (Execute Lua)     │              │ (Allowed -> Pass Chain)
    Atomically        ▼              ▼
           ┌─────────────┐        ┌──────────────────┐
           │ Redis Cache │        │  Mock Controller │ (Endpoints:
           │  (Local)    │        │  (DemoApiCtrl)   │  /api/login, /api/search)
           └─────────────┘        └──────────────────┘
```

### Request Lifecycle
1. **Intercept**: The `RateLimitFilter` intercepts all incoming requests to the `/api/**` namespace.
2. **Identification**: The filter extracts the client identity from the `X-Client-Id` header (with a fallback query parameter or default identifier).
3. **Lookup**: The filter retrieves route configuration limits (defined in `limits.json`) for the matching endpoint.
4. **Atomic Evaluation**: The filter calls the active `RateLimiter` bean, executing the matching Redis Lua script.
5. **Enforcement**:
   - **Allowed**: The filter forwards the request to the controller. The mock API returns `200 OK`.
   - **Blocked**: The filter halts the request chain, sets status `429 Too Many Requests`, and returns a structured JSON payload.
6. **Telemetry**: Both allowed and blocked outcomes, along with processing latencies, are logged in `StatsTracker` to feed the dashboard.

---

## 3. Rate Limiting Algorithms Deep-Dive

The service implements three classic rate-limiting algorithms, each representing different design trade-offs:

### 1. Fixed Window Limiter
- **Mechanism**: Divides time into windows (e.g. 1 minute) and increments a counter. The key format includes a time stamp: `rl:fixed:{clientId}:{route}:{windowStartMinute}`.
- **Accuracy**: Low. It suffers from a "boundary burst" vulnerability where a client can send its limit at the end of window $N$ and another burst at the start of window $N+1$, effectively allowing double the rate limit in a short transition span.
- **Memory Cost**: Extremely Low (a single integer key per client/route/window).
- **Smoothing**: Poor. Traffic can arrive in bursts.

### 2. Token Bucket Limiter
- **Mechanism**: The bucket has a maximum capacity $C$ and refills continuously at a rate $r$ tokens/second. The state is tracked as a Redis hash: `rl:bucket:{clientId}:{route}` storing `{tokens, lastRefillTimestamp}`. Refill calculations are performed dynamically on each request inside the Lua script:
  $$\text{refilled\_tokens} = \min(C, \text{current\_tokens} + \Delta t \times r)$$
- **Accuracy**: High. The time delta $\Delta t$ is computed in milliseconds.
- **Memory Cost**: Medium (one Redis hash key per client/route).
- **Smoothing**: Excellent. It accommodates short bursts up to capacity $C$ while enforcing the steady-state rate $r$.

### 3. Sliding Window Log Limiter
- **Mechanism**: Tracks the exact timestamp of every request in a Redis Sorted Set (ZSET): `rl:sliding:{clientId}:{route}`. When a request arrives, the Lua script trims elements older than the sliding window boundary ($t - \text{window\_size}$), counts the remaining elements via `ZCARD`, and inserts the new request timestamp if under the limit.
- **Accuracy**: Perfect. 100% accurate rate enforcement.
- **Memory Cost**: High (requires storing a unique member and score for *every* request inside the sliding window).
- **Smoothing**: Good. Enforces a strict, moving window.

---

## 4. Redis Lua Scripting & Concurrency

### The Race Condition Problem
A naive rate limiting implementation in Java uses a read-then-write pattern:
```java
// PSEUDOCODE — DO NOT USE IN PRODUCTION
int count = redis.get(key);
if (count < limit) {
    redis.incr(key);
    return true; // allowed
}
return false; // blocked
```
In a distributed environment, if two threads (or two separate backend servers) execute this check at the exact same millisecond when the count is `limit - 1`, both will read `count < limit` as true, both will approve the request, and both will increment the counter. The limit is breached.

Using transactions (`MULTI/EXEC`) does not solve this because Redis queues transactions blindly. You cannot read a value *inside* a transaction and use it to conditionally branching logic (like `if/else`) server-side.

### The Lua Script Solution
Redis Lua scripts are executed **atomically and single-threaded** on the Redis server engine. This guarantees that no other command can run in the middle of our script, resolving the concurrency race condition. 

#### Example: Fixed Window Script (`fixed_window.lua`)
```lua
local current = tonumber(redis.call("GET", KEYS[1]) or "0")

if current + 1 > tonumber(ARGV[1]) then
  return 0 -- blocked
else
  local val = redis.call("INCR", KEYS[1])
  if val == 1 then
    redis.call("EXPIRE", KEYS[1], ARGV[2])
  end
  return 1 -- allowed
end
```

---

## 5. Resilience & Outage Handling

A rate limiter sits directly in the critical path of all API calls. If the rate limiter database (Redis) goes down, the application must handle the failure gracefully. The service provides two configurable modes:

```yaml
# application.yml
rate-limiter:
  fail-mode: open # Options: open | closed
```

### Fail-Open (`open`)
- **Philosophy**: Prioritizes **availability** over correctness.
- **Action**: If a connection timeout or Redis connection failure occurs, the limiter catches the exception, logs a warning, and allows the request through.
- **Observability**: Increments the `failedBypassCount` metric so operators can monitor the volume of requests bypassing security gates during an outage.

### Fail-Closed (`closed`)
- **Philosophy**: Prioritizes **security and resource protection**.
- **Action**: Blocks incoming requests immediately with an HTTP `503 Service Unavailable` response, ensuring target application servers are not overwhelmed during rate-limiter downtime.

---

## 6. Telemetry & Load Testing

### Performance Benchmarking (LoadTestRunner)
The system includes a native concurrent load tester ([`LoadTestRunner.java`](file:///C:/Users/HP/.gemini/antigravity/scratch/rate-limiter/backend/src/main/java/com/ratelimiter/loadtest/LoadTestRunner.java)) built using Java's standard `java.net.http.HttpClient` and `ExecutorService` thread pooling. It simulates high concurrency by firing hundreds of requests in parallel and collecting precise latency percentiles.

#### Execution Command
```bash
java -cp target/rate-limiter-0.0.1-SNAPSHOT.jar com.ratelimiter.loadtest.LoadTestRunner --requests 1000 --concurrency 20 --endpoint /api/search
```

#### Results Report Structure
- **Totals**: Total Requests, Allowed Requests, Blocked (429) Requests, Network Errors.
- **Latency**: p50 (median), p99 (tail-end latency), and maximum latency.
- **Wall Time**: Total execution time for the concurrent batch.

---

## 7. Technical Configuration Reference

### Backend Configuration (`limits.json`)
Per-route limits loaded at startup:
```json
{
  "/api/login":    { "limit": 5,   "windowSeconds": 60 },
  "/api/search":   { "limit": 100, "windowSeconds": 60 },
  "/api/payment":  { "limit": 20,  "windowSeconds": 60 },
  "/api/products": { "limit": 50,  "windowSeconds": 60 }
}
```

### Environment Variables (.env)
Required configurations for production deployment:
```env
VITE_API_URL=https://your-backend-url.onrender.com
```
