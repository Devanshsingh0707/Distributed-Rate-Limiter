package com.ratelimiter.loadtest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * Standalone load test for the rate limiter.
 *
 * Usage (run from the project root or paste the class into the backend src):
 *
 *   java -cp <classpath> com.ratelimiter.loadtest.LoadTestRunner [options]
 *
 * Options (all optional — defaults shown):
 *   --host        http://localhost:8080   Target host
 *   --endpoint    /api/search            Endpoint to hit
 *   --client-id   load-test-client       X-Client-Id header value
 *   --requests    1000                   Total number of requests
 *   --concurrency 20                     Number of parallel threads
 *   --output      load-test-results.txt  File to write results (omit to skip)
 *
 * Example:
 *   java ... LoadTestRunner --requests 500 --concurrency 10 --endpoint /api/login
 */
public class LoadTestRunner {

    // ── defaults ──────────────────────────────────────────────────────────────
    private static String host        = "http://localhost:8080";
    private static String endpoint    = "/api/search";
    private static String clientId    = "load-test-client";
    private static int    totalReqs   = 1000;
    private static int    concurrency = 20;
    private static String outputFile  = "load-test-results.txt";

    public static void main(String[] args) throws Exception {
        parseArgs(args);

        String url = host + endpoint;
        System.out.println("=== Rate Limiter Load Test ===");
        System.out.println("Target     : " + url);
        System.out.println("Client ID  : " + clientId);
        System.out.println("Requests   : " + totalReqs);
        System.out.println("Concurrency: " + concurrency);
        System.out.println();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        LongAdder allowed     = new LongAdder();
        LongAdder blocked     = new LongAdder();
        LongAdder errors      = new LongAdder();
        List<Long> latencies  = Collections.synchronizedList(new ArrayList<>(totalReqs));

        // Build request tasks
        List<Callable<Void>> tasks = new ArrayList<>(totalReqs);
        for (int i = 0; i < totalReqs; i++) {
            tasks.add(() -> {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("X-Client-Id", clientId)
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .build();
                long t0 = System.currentTimeMillis();
                try {
                    HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
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

        // Fire all requests
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        Instant wallStart    = Instant.now();
        List<Future<Void>> futures = pool.invokeAll(tasks);
        for (Future<Void> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        Duration wall = Duration.between(wallStart, Instant.now());

        // Compute percentiles
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        long p50  = percentile(sorted, 50);
        long p99  = percentile(sorted, 99);
        long max  = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1);
        long total = allowed.sum() + blocked.sum() + errors.sum();

        String report = buildReport(url, total, allowed.sum(), blocked.sum(),
                                    errors.sum(), p50, p99, max, wall);

        System.out.println(report);

        if (outputFile != null && !outputFile.isEmpty()) {
            Files.writeString(Path.of(outputFile), report);
            System.out.println("Results written to: " + outputFile);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static long percentile(List<Long> sorted, int pct) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    private static String buildReport(String url, long total, long allowed, long blocked,
                                      long errors, long p50, long p99, long max, Duration wall) {
        return String.format("""
                === Load Test Results ===
                Target     : %s
                Client ID  : %s

                Total sent : %d
                Allowed    : %d  (%.1f%%)
                Blocked    : %d  (%.1f%%)
                Errors     : %d

                Latency (ms)
                  p50      : %d ms
                  p99      : %d ms
                  max      : %d ms

                Wall time  : %d ms
                """,
                url,
                clientId,
                total,
                allowed, pct(allowed, total),
                blocked, pct(blocked, total),
                errors,
                p50, p99, max,
                wall.toMillis());
    }

    private static double pct(long part, long total) {
        return total == 0 ? 0.0 : (part * 100.0 / total);
    }

    private static void parseArgs(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--host"        -> host        = args[++i];
                case "--endpoint"    -> endpoint    = args[++i];
                case "--client-id"   -> clientId    = args[++i];
                case "--requests"    -> totalReqs   = Integer.parseInt(args[++i]);
                case "--concurrency" -> concurrency = Integer.parseInt(args[++i]);
                case "--output"      -> outputFile  = args[++i];
            }
        }
    }
}
