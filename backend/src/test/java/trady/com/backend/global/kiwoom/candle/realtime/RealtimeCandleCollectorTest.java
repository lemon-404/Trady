package trady.com.backend.global.kiwoom.candle.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleResponse;
import trady.com.backend.stock.entity.Stock;

import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RealtimeCandleCollectorTest {
    private final Instant start = Instant.parse("2026-11-24T23:30:00Z");
    private final AtomicLong nanos = new AtomicLong();
    private final RealtimeCandleClient client = mock(RealtimeCandleClient.class);
    private final RealtimeCandleStore store = mock(RealtimeCandleStore.class);
    private final RealtimeCandleDispatcher dispatcher = new RealtimeCandleDispatcher(
            List.of(Stock.builder().code("005930").build()), new RealtimeCandleRateLimiter(nanos::get), start);
    private final RealtimeCandleCollector collector = new RealtimeCandleCollector(
            dispatcher, client, store, Schedulers.immediate(), Clock.fixed(start, ZoneOffset.UTC));

    @Test
    void databaseSaveRunsOnDedicatedThreadAndKeepsTaskBusyUntilItReturns() throws Exception {
        var database = new RealtimeCandleDbConfig().realtimeCandleDbScheduler();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var others = new CountDownLatch(8);
        var target = new RealtimeCandleCollector(dispatcher, client, store, database, Clock.fixed(start, ZoneOffset.UTC));
        when(client.request(any(), anyString(), anyString())).thenReturn(Mono.just(response()));
        when(store.save(any(), anyList(), anyBoolean())).thenAnswer(call -> {
            assertThat(Thread.currentThread().getName()).startsWith("realtime-candle-db-");
            if (call.<RealtimeCandleDispatcher.Task>getArgument(0).scope() == RealtimeCandleScope.MINUTE_1) {
                entered.countDown();
                release.await(5, TimeUnit.SECONDS);
            } else others.countDown();
            return 1;
        });
        try {
            target.collect();
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(others.await(3, TimeUnit.SECONDS)).isTrue();
            nanos.set(4_000_000_000L);
            assertThat(dispatcher.dispatch(start.plusSeconds(4))).isEmpty();
        } finally {
            release.countDown();
            database.dispose();
        }
    }

    @Test
    void databaseErrorReleasesTaskWithoutRetry() {
        when(client.request(any(), anyString(), anyString())).thenReturn(Mono.just(response()));
        when(store.save(any(), anyList(), anyBoolean())).thenThrow(new IllegalStateException("database"));
        collector.collect();
        verify(store, times(9)).save(any(), anyList(), anyBoolean());
        nanos.set(4_000_000_000L);
        assertThat(dispatcher.dispatch(start.plusSeconds(4))).hasSize(1);
    }

    @Test
    void sendsSelectedTasksWithSeoulDateAndReleasesCompletedTasks() {
        when(client.request(any(), anyString(), anyString())).thenReturn(Mono.just(response()));

        collector.collect();

        verify(client, times(9)).request(argThat(request -> request.getStockCode().equals("005930")
                && request.getBaseDt().equals("20261125") && request.getType().equals("0")), anyString(), eq("N"));
        verify(client).request(argThat(request -> request.getInterval().equals("1")), eq("ka10080"), eq("N"));
        nanos.set(4_000_000_000L);
        assertThat(dispatcher.dispatch(start.plusSeconds(4))).singleElement()
                .extracting(RealtimeCandleDispatcher.Task::scope).isEqualTo(RealtimeCandleScope.MINUTE_1);
    }

    @Test
    void onePendingResponseDoesNotBlockOtherScopesAndItsKeyRemainsBusy() {
        var pending = Sinks.<ResponseEntity<CandleResponse>>one();
        when(client.request(any(), anyString(), anyString())).thenAnswer(call ->
                call.<String>getArgument(1).equals("ka10080")
                        && call.<trady.com.backend.global.kiwoom.candle.historical.dto.CandleRequest>getArgument(0).getInterval().equals("1")
                        ? pending.asMono() : Mono.just(response()));

        collector.collect();

        verify(client, times(9)).request(any(), anyString(), eq("N"));
        nanos.set(4_000_000_000L);
        assertThat(dispatcher.dispatch(start.plusSeconds(4))).isEmpty();
        pending.tryEmitValue(response());
        assertThat(dispatcher.dispatch(start.plusSeconds(4))).hasSize(1);
    }

    @Test
    void asyncErrorsAreNotRetriedAndReleaseEveryTask() {
        when(client.request(any(), anyString(), anyString())).thenReturn(Mono.error(new IllegalStateException("network")));

        collector.collect();

        verify(client, times(9)).request(any(), anyString(), anyString());
        nanos.set(4_000_000_000L);
        assertThat(dispatcher.dispatch(start.plusSeconds(4))).hasSize(1);
    }

    @Test
    void synchronousClientExceptionDoesNotLeakReservationOrStopRemainingTasks() {
        when(client.request(any(), anyString(), anyString())).thenThrow(new IllegalStateException("client"));

        collector.collect();

        verify(client, times(9)).request(any(), anyString(), anyString());
        nanos.set(4_000_000_000L);
        assertThat(dispatcher.dispatch(start.plusSeconds(4))).hasSize(1);
    }

    private ResponseEntity<CandleResponse> response() {
        return ResponseEntity.ok(new ObjectMapper().readValue(
                "{\"return_code\":0,\"stk_cd\":\"005930\",\"stk_min_pole_chart_qry\":[]}", CandleResponse.class));
    }
}
