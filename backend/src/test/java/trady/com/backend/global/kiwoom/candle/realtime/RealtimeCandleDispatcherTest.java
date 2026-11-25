package trady.com.backend.global.kiwoom.candle.realtime;

import org.junit.jupiter.api.Test;
import trady.com.backend.stock.entity.Stock;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

class RealtimeCandleDispatcherTest {
    private final Instant start = Instant.parse("2026-11-24T00:00:00Z");

    @Test
    void selectsDueTasksAndSkipsThemUntilCompletion() {
        var nanos = new AtomicLong();
        var dispatcher = dispatcher(1, nanos);
        var initial = dispatcher.dispatch(start);
        assertThat(initial).hasSize(9);
        nanos.set(10_000_000_000L);
        assertThat(dispatcher.dispatch(start.plusSeconds(10))).isEmpty();

        var minute = initial.stream().filter(task -> task.scope() == RealtimeCandleScope.MINUTE_1).findFirst().orElseThrow();
        dispatcher.complete(minute);
        assertThat(dispatcher.dispatch(start.plusSeconds(10))).containsExactly(minute);
    }

    @Test
    void completingEarlyDoesNotMakeTaskDueBeforeItsRefreshInterval() {
        var nanos = new AtomicLong();
        var dispatcher = dispatcher(1, nanos);
        dispatcher.dispatch(start).forEach(dispatcher::complete);
        nanos.set(3_000_000_000L);
        assertThat(dispatcher.dispatch(start.plusSeconds(3))).isEmpty();
        nanos.set(4_000_000_000L);
        assertThat(dispatcher.dispatch(start.plusSeconds(4))).singleElement()
                .extracting(RealtimeCandleDispatcher.Task::scope).isEqualTo(RealtimeCandleScope.MINUTE_1);
    }

    @Test
    void distributesTenStocksFairlyAndRefreshesShortScopesMoreOften() {
        var nanos = new AtomicLong();
        var dispatcher = dispatcher(10, nanos);
        var byScope = new EnumMap<RealtimeCandleScope, Integer>(RealtimeCandleScope.class);
        var byStock = new HashMap<String, Integer>();
        for (int second = 0; second < 120; second++) {
            nanos.set(second * 1_000_000_000L);
            var tasks = dispatcher.dispatch(start.plusSeconds(second));
            var perTr = new HashMap<String, Integer>();
            for (var task : tasks) {
                perTr.merge(task.scope().tr(), 1, Integer::sum);
                byScope.merge(task.scope(), 1, Integer::sum);
                if (task.scope() == RealtimeCandleScope.MINUTE_1) byStock.merge(task.stock().getCode(), 1, Integer::sum);
                dispatcher.complete(task);
            }
            assertThat(perTr.values()).allMatch(count -> count <= 5);
        }
        assertThat(byScope.get(RealtimeCandleScope.MINUTE_1)).isBetween(280, 300);
        assertThat(byScope.get(RealtimeCandleScope.MINUTE_5)).isBetween(90, 100);
        assertThat(byStock).hasSize(10);
        assertThat(Collections.max(byStock.values()) - Collections.min(byStock.values())).isLessThanOrEqualTo(1);
    }

    @Test
    void rateLimitedTasksRemainDueAndOtherTrsCanProceed() {
        var nanos = new AtomicLong();
        var limiter = new RealtimeCandleRateLimiter(nanos::get);
        for (int i = 0; i < 5; i++) limiter.tryAcquire("ka11080");
        var dispatcher = new RealtimeCandleDispatcher(stocks(1), limiter, start);

        assertThat(dispatcher.dispatch(start)).hasSize(4).allMatch(task -> !task.scope().minute());
        nanos.set(1_000_000_000L);
        assertThat(dispatcher.dispatch(start.plusSeconds(1))).hasSize(5).allMatch(task -> task.scope().minute());
    }

    @Test
    void doesNotReplayEveryMissedCycleAfterLongDelay() {
        var nanos = new AtomicLong();
        var dispatcher = dispatcher(1, nanos);
        dispatcher.dispatch(start).forEach(dispatcher::complete);
        nanos.set(600_000_000_000L);
        var late = dispatcher.dispatch(start.plusSeconds(600));
        assertThat(late).hasSize(9);
        late.forEach(dispatcher::complete);
        nanos.addAndGet(1_000_000_000L);
        assertThat(dispatcher.dispatch(start.plusSeconds(601))).isEmpty();
    }

    @Test
    void rejectsDuplicateOrExcessiveTargets() {
        var limiter = new RealtimeCandleRateLimiter(() -> 0L);
        assertThatThrownBy(() -> new RealtimeCandleDispatcher(List.of(), limiter, start)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RealtimeCandleDispatcher(stocks(11), limiter, start)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RealtimeCandleDispatcher(List.of(stocks(1).get(0), stocks(1).get(0)), limiter, start))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void finalRequestsWaitForRegularWorkAndRunOnceRegardlessOfRefreshInterval() {
        var nanos = new AtomicLong();
        var dispatcher = dispatcher(1, nanos);
        var regular = dispatcher.dispatch(start);
        dispatcher.requestFinalCollection();
        assertThat(dispatcher.dispatch(start)).isEmpty();
        assertThat(dispatcher.isFinished()).isFalse();
        regular.forEach(dispatcher::complete);
        nanos.set(1_000_000_000L);
        var last = dispatcher.dispatch(start.plusSeconds(1));
        assertThat(last).hasSize(9);
        last.forEach(dispatcher::complete);
        dispatcher.requestFinalCollection();
        assertThat(dispatcher.isFinished()).isTrue();
        assertThat(dispatcher.dispatch(start.plusSeconds(100))).isEmpty();
    }

    @Test
    void finalRequestsAreSpreadAcrossTicksWithinTrBudget() {
        var nanos = new AtomicLong();
        var dispatcher = dispatcher(10, nanos);
        dispatcher.requestFinalCollection();
        var completed = new HashSet<RealtimeCandleDispatcher.Task>();
        for (int second = 0; second < 12; second++) {
            nanos.set(second * 1_000_000_000L);
            var tasks = dispatcher.dispatch(start.plusSeconds(second));
            var counts = new HashMap<String, Integer>();
            for (var task : tasks) {
                assertThat(completed.add(task)).isTrue();
                counts.merge(task.scope().tr(), 1, Integer::sum);
                dispatcher.complete(task);
            }
            assertThat(counts.values()).allMatch(count -> count <= 5);
        }
        assertThat(completed).hasSize(90);
        assertThat(dispatcher.isFinished()).isTrue();
    }

    @Test
    void stoppedDispatcherDoesNotSelectNewWork() {
        var dispatcher = dispatcher(1, new AtomicLong());
        dispatcher.stop();
        assertThat(dispatcher.dispatch(start)).isEmpty();
        dispatcher.requestFinalCollection();
        assertThat(dispatcher.dispatch(start)).isEmpty();
    }

    private RealtimeCandleDispatcher dispatcher(int count, AtomicLong nanos) {
        return new RealtimeCandleDispatcher(stocks(count), new RealtimeCandleRateLimiter(nanos::get), start);
    }

    private List<Stock> stocks(int count) {
        return IntStream.range(0, count).mapToObj(i -> Stock.builder().code("stock-" + i).build()).toList();
    }
}
