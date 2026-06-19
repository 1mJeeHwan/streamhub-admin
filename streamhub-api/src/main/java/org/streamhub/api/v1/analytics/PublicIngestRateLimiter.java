package org.streamhub.api.v1.analytics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Lightweight, dependency-free per-client (IP) throttle for the public analytics ingest endpoint.
 *
 * <p>The endpoint is unauthenticated and hit directly by the browser, so it is trivially floodable —
 * one DB insert per call. This guards it with an in-memory token bucket per client IP: each IP gets a
 * bucket of {@value #CAPACITY} tokens that refills continuously at {@value #REFILL_PER_SECOND}
 * tokens/second. A request consumes one token; when the bucket is empty the request is denied and the
 * caller is expected to silently drop the event (never 500, never throw to the browser).
 *
 * <p>State is intentionally process-local and best-effort (no Redis dependency) — adequate for
 * abuse-dampening on a single node. Idle buckets are evicted lazily once they have refilled to full
 * capacity to bound memory under a churn of distinct IPs.
 */
@Component
public class PublicIngestRateLimiter {

    /** Maximum burst an IP may send before being throttled. */
    static final long CAPACITY = 60L;

    /** Sustained steady-state rate per IP (tokens replenished each second). */
    static final double REFILL_PER_SECOND = 5.0;

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final java.util.function.LongSupplier clock;

    /** Production constructor: uses {@link System#nanoTime()} as the clock. */
    public PublicIngestRateLimiter() {
        this(System::nanoTime);
    }

    /**
     * Test seam constructor.
     *
     * @param clock monotonic nanosecond clock (e.g. {@link System#nanoTime()})
     */
    PublicIngestRateLimiter(java.util.function.LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Attempts to consume a single token for the given client key.
     *
     * @param clientKey stable per-client key, typically the client IP; blank/null keys are treated as
     *                  a shared "unknown" bucket so a missing IP cannot bypass the limit
     * @return {@code true} if the request is allowed, {@code false} if the client is over its limit
     */
    public boolean tryAcquire(String clientKey) {
        return tryAcquire(clientKey, 1L);
    }

    /**
     * Attempts to consume {@code cost} tokens at once for the given client key, so a single request
     * that does proportionally more work (e.g. a batch ingest of N events) is charged N tokens
     * instead of one. The cost is clamped to at least one token (a request always costs something)
     * and to at most the bucket {@value #CAPACITY} (a single oversized batch can drain, but not
     * over-debit, the bucket). Acquisition is all-or-nothing: nothing is consumed unless the full
     * cost fits.
     *
     * @param clientKey stable per-client key, typically the client IP; blank/null keys are treated as
     *                  a shared "unknown" bucket so a missing IP cannot bypass the limit
     * @param cost      number of tokens this request should consume (clamped to {@code [1, CAPACITY]})
     * @return {@code true} if the request is allowed, {@code false} if the client is over its limit
     */
    public boolean tryAcquire(String clientKey, long cost) {
        long tokens = Math.min(CAPACITY, Math.max(1L, cost));
        String key = (clientKey == null || clientKey.isBlank()) ? "unknown" : clientKey;
        long now = clock.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(now));
        boolean allowed = bucket.tryConsume(now, tokens);
        evictIfReplenished(key, bucket, now);
        return allowed;
    }

    /** Drops a bucket once it has fully refilled, so quiescent IPs do not accumulate forever. */
    private void evictIfReplenished(String key, Bucket bucket, long now) {
        if (bucket.isFull(now)) {
            buckets.remove(key, bucket);
        }
    }

    /** A continuously-refilling token bucket guarded by its own monitor. */
    private static final class Bucket {

        private double tokens;
        private long lastRefillNanos;

        private Bucket(long now) {
            this.tokens = CAPACITY;
            this.lastRefillNanos = now;
        }

        private synchronized boolean tryConsume(long now, long cost) {
            refill(now);
            if (tokens >= cost) {
                tokens -= cost;
                return true;
            }
            return false;
        }

        private synchronized boolean isFull(long now) {
            refill(now);
            return tokens >= CAPACITY;
        }

        private void refill(long now) {
            long elapsed = now - lastRefillNanos;
            if (elapsed <= 0) {
                return;
            }
            double refilled = (elapsed / (double) NANOS_PER_SECOND) * REFILL_PER_SECOND;
            tokens = Math.min(CAPACITY, tokens + refilled);
            lastRefillNanos = now;
        }
    }
}
