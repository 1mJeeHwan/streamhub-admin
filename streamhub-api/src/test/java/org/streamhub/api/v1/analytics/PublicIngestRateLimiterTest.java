package org.streamhub.api.v1.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PublicIngestRateLimiter} — the per-IP token bucket guarding the public
 * analytics ingest endpoint. A controllable monotonic clock drives refill so the time-dependent
 * behaviour is deterministic (no sleeping).
 */
class PublicIngestRateLimiterTest {

    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    @DisplayName("버킷 용량까지는 허용하고, 초과분은 차단한다")
    @Test
    void allowsUpToCapacityThenThrottles() {
        AtomicLong now = new AtomicLong(0L);
        PublicIngestRateLimiter limiter = new PublicIngestRateLimiter(now::get);

        for (int i = 0; i < PublicIngestRateLimiter.CAPACITY; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4")).as("token %s within capacity", i).isTrue();
        }
        assertThat(limiter.tryAcquire("1.2.3.4")).as("over capacity").isFalse();
    }

    @DisplayName("시간이 지나면 설정된 비율로 토큰이 보충된다")
    @Test
    void refillsOverTime() {
        AtomicLong now = new AtomicLong(0L);
        PublicIngestRateLimiter limiter = new PublicIngestRateLimiter(now::get);

        for (int i = 0; i < PublicIngestRateLimiter.CAPACITY; i++) {
            limiter.tryAcquire("9.9.9.9");
        }
        assertThat(limiter.tryAcquire("9.9.9.9")).isFalse();

        // Advance one second: REFILL_PER_SECOND tokens become available.
        now.addAndGet(NANOS_PER_SECOND);
        int refilled = (int) PublicIngestRateLimiter.REFILL_PER_SECOND;
        for (int i = 0; i < refilled; i++) {
            assertThat(limiter.tryAcquire("9.9.9.9")).as("refilled token %s", i).isTrue();
        }
        assertThat(limiter.tryAcquire("9.9.9.9")).as("drained again").isFalse();
    }

    @DisplayName("IP별로 독립적인 버킷을 사용한다")
    @Test
    void bucketsAreIsolatedPerClient() {
        AtomicLong now = new AtomicLong(0L);
        PublicIngestRateLimiter limiter = new PublicIngestRateLimiter(now::get);

        for (int i = 0; i < PublicIngestRateLimiter.CAPACITY; i++) {
            limiter.tryAcquire("10.0.0.1");
        }
        assertThat(limiter.tryAcquire("10.0.0.1")).as("first IP throttled").isFalse();
        assertThat(limiter.tryAcquire("10.0.0.2")).as("second IP unaffected").isTrue();
    }

    @DisplayName("null/빈 IP는 공유 버킷으로 처리되어 우회되지 않는다")
    @Test
    void nullAndBlankKeysShareTheUnknownBucket() {
        AtomicLong now = new AtomicLong(0L);
        PublicIngestRateLimiter limiter = new PublicIngestRateLimiter(now::get);

        for (int i = 0; i < PublicIngestRateLimiter.CAPACITY; i++) {
            assertThat(limiter.tryAcquire(null)).isTrue();
        }
        // Blank key maps to the same "unknown" bucket, which is now drained.
        assertThat(limiter.tryAcquire("   ")).isFalse();
    }
}
