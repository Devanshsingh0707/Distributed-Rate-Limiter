# rate-limiter

A Spring Boot service that enforces per-client, per-route request limits using atomic Redis Lua scripts. Three algorithms are implemented and switchable at runtime without restart. A minimal React dashboard (Vite) monitors decisions live.

---

## Architecture

```
Browser / k6 / LoadTestRunner
        │
        ▼  HTTP  (header: X-Client-Id)
┌───────────────────────────────────┐
│  RateLimitFilter  (servlet filter)│  intercepts every /api/** request
│  1. extract clientId              │
│  2. look up limits.json config    │
│  3. run active limiter            │
└──────────────┬────────────────────┘
               │ RedisTemplate.execute(LuaScript)  ← atomic, single round-trip
               ▼
         [ Redis ]
               │
       ┌───────┴────────┐
       │ allowed (200)  │ blocked (429 / 503)
       ▼                ▼
  DemoApiController   write JSON error, stop chain
  (mock endpoints)
```

`StatsTracker` (in-memory, `LongAdder`) records every decision.  
`StatsController` exposes `/stats/**` for the dashboard and load tests.

---

## Algorithm comparison

| Algorithm | Redis memory | Accuracy | Burst at boundary | Smoothing |
|---|---|---|---|---|
| **Fixed Window** | 1 key (integer) | Low — boundary burst possible | Up to 2× limit can pass at window edge | None |
| **Token Bucket** | 1 hash (2 fields) | High | Handled — bucket refills continuously | Yes, natural |
| **Sliding Window Log** | 1 sorted set (1 entry / request) | Exact | None — every request timestamped | Yes, by definition |

**Default algorithm: Fixed Window.** It is the simplest to reason about, uses the least Redis memory, and is accurate enough for demo and interview purposes. Switch to Token Bucket for smoother enforcement or Sliding Window Log for exact correctness under burst load.

Change at runtime (no restart needed):
```
POST /stats/algorithm   {"algorithm": "token"}   # or "fixed" / "sliding"
```

---

## Fail-open vs fail-closed

When Redis is unreachable, the filter has two options, controlled by:

```yaml
# application.yml
rate-limiter:
  fail-mode: open   # or: closed
```

| Mode | Behaviour | When to use |
|---|---|---|
| `open` | Allow the request through, increment a `failedBypassCount` metric | Prefer for `/api/search` — users should not be blocked by an infra outage |
| `closed` | Reject with HTTP 503, log the reason | Prefer for `/api/payment` — better to fail than to allow unguarded access |

**The trade-off:** `open` prioritises availability over correctness — an attacker can hammer the API while Redis is down. `closed` prioritises correctness over availability — legitimate traffic is also blocked. Pick based on what failing silent costs in your context.

Check current status and bypass count at any time:
```
GET /stats/redis-status
```
Returns: `redisConnected`, `failMode`, `failedBypassCount`, and a plain-text note.

---

## Load test results

Run the load test after starting the backend (`mvn spring-boot:run`):

```powershell
# compile once, then run
mvn -q package -DskipTests
java -cp target/rate-limiter-0.0.1-SNAPSHOT.jar \
     com.ratelimiter.loadtest.LoadTestRunner \
     --endpoint /api/search --requests 1000 --concurrency 20
```

Sample output (fixed-window, limit 100/min, 1000 requests, 20 threads):

```
=== Load Test Results ===
Target     : http://localhost:8080/api/search
Client ID  : load-test-client

Total sent : 1000
Allowed    : 100  (10.0%)
Blocked    : 900  (90.0%)
Errors     : 0

Latency (ms)
  p50      : 3 ms
  p99      : 18 ms
  max      : 41 ms

Wall time  : 1823 ms
```

*(Results vary by machine. Replace with your own numbers before sharing.)*

---

## How to run locally

```
# terminal 1 — Redis
redis-server          # or: sudo service redis-server start  (WSL/Ubuntu)

# terminal 2 — backend
cd backend
mvn spring-boot:run

# terminal 3 — dashboard
cd frontend
npm install && npm run dev
# open http://localhost:5173
```

**Two-instance proof** (shows Redis-shared state, not per-process memory):
```
java -jar target/rate-limiter-0.0.1-SNAPSHOT.jar --server.port=8081
java -jar target/rate-limiter-0.0.1-SNAPSHOT.jar --server.port=8082
```
Fire requests for the same `clientId` split across both ports. The combined allowed count stays at the configured limit — not doubled.

---

## Known limitations

- **Redis is a single point of failure.** This project uses one Redis node. A production system needs Redis Sentinel or Redis Cluster. This was left out intentionally to keep the scope clear.
- **Sliding Window Log memory.** At high request rates the sorted set can grow large. A production deployment would set a max-memory policy or switch to the approximate sliding window counter variant.
- **In-memory stats are per-instance.** `StatsTracker` data is not replicated across instances. The dashboard shows stats for whichever backend instance it is pointed at.
