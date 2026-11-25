package trady.com.backend.global.kiwoom.candle.realtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeCandleRateLimiterTest {
    @Test
    void sharesBudgetForSameTrAndSeparatesOtherTrs() {
        var limiter = new RealtimeCandleRateLimiter(() -> 0L);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire(RealtimeCandleScope.MINUTE_1.tr())).isTrue();
        }

        assertThat(limiter.tryAcquire(RealtimeCandleScope.MINUTE_5.tr())).isFalse();
        assertThat(limiter.tryAcquire(RealtimeCandleScope.DAY.tr())).isTrue();
    }

    @Test
    void expiresEachRequestExactlyOneSecondAfterItsAdmission() {
        var now = new AtomicLong();
        var limiter = new RealtimeCandleRateLimiter(now::get);
        assertThat(limiter.tryAcquire("ka10080")).isTrue();
        now.set(500_000_000L);
        for (int i = 0; i < 4; i++) assertThat(limiter.tryAcquire("ka10080")).isTrue();

        now.set(999_999_999L);
        assertThat(limiter.tryAcquire("ka10080")).isFalse();
        now.set(1_000_000_000L);
        assertThat(limiter.tryAcquire("ka10080")).isTrue();
        assertThat(limiter.tryAcquire("ka10080")).isFalse();
        now.set(1_500_000_000L);
        for (int i = 0; i < 4; i++) assertThat(limiter.tryAcquire("ka10080")).isTrue();
        assertThat(limiter.tryAcquire("ka10080")).isFalse();
    }

    @Test
    void concurrentRequestsCannotExceedFiveAdmissions() throws Exception {
        var limiter = new RealtimeCandleRateLimiter(() -> 0L);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> requests = IntStream.range(0, 100)
                    .mapToObj(i -> (Callable<Boolean>) () -> limiter.tryAcquire("ka10080")).toList();
            int accepted = 0;
            for (var result : executor.invokeAll(requests, 5, TimeUnit.SECONDS)) {
                if (result.get()) accepted++;
            }
            assertThat(accepted).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }
}
