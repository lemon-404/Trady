package trady.com.backend.global.kiwoom.candle.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleResponse;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.stock.repository.StockQueryRepository;

import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RealtimeCandleSchedulerTest {
    private final StockQueryRepository stocks = mock(StockQueryRepository.class);
    private final RealtimeCandleClient client = mock(RealtimeCandleClient.class);
    private final RealtimeCandleStore store = mock(RealtimeCandleStore.class);
    private final AtomicLong nanos = new AtomicLong();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-24T00:00:00Z"));

    @Test void initializesOnceAndWaitsUntilInitializationBeforeTicking() {
        setup();
        var scheduler = scheduler("005930");
        scheduler.tick();
        verifyNoInteractions(client);
        scheduler.run(new DefaultApplicationArguments());
        scheduler.run(new DefaultApplicationArguments());
        scheduler.tick();
        verifyNoInteractions(client);
        scheduler.startCollection();
        scheduler.startCollection();
        scheduler.tick();
        verify(stocks, times(1)).findAllByCodeIn(List.of("005930"));
        verify(client, times(9)).request(any(), anyString(), eq("N"));
        scheduler.stop();
        clock.now.set(clock.instant().plusSeconds(10));
        scheduler.tick();
        verify(client, times(9)).request(any(), anyString(), eq("N"));
    }

    @Test void performsFinalLookupAndWaitsForNextStartSignal() {
        setup();
        var scheduler = scheduler("005930");
        scheduler.run(new DefaultApplicationArguments());
        scheduler.startCollection();
        scheduler.tick();
        clock.now.set(Instant.parse("2026-11-24T06:31:00Z"));
        nanos.set(60_000_000_000L);
        scheduler.beginFinalCollection();
        verify(client, times(9)).request(any(), anyString(), eq("N"));
        scheduler.tick();
        scheduler.tick();
        clock.now.set(Instant.parse("2026-11-25T00:00:00Z"));
        scheduler.tick();
        verify(client, times(18)).request(any(), anyString(), eq("N"));
        nanos.set(86_400_000_000_000L);
        scheduler.startCollection();
        scheduler.tick();
        verify(client, times(27)).request(any(), anyString(), eq("N"));
    }

    @Test void finalLookupWaitsForPendingRegularResponse() {
        setup();
        var pending = Sinks.<ResponseEntity<CandleResponse>>one();
        when(client.request(any(), anyString(), anyString())).thenReturn(pending.asMono());
        var scheduler = scheduler("005930");
        scheduler.run(new DefaultApplicationArguments());
        scheduler.startCollection();
        scheduler.tick();
        clock.now.set(Instant.parse("2026-11-24T06:31:00Z"));
        nanos.set(60_000_000_000L);
        scheduler.beginFinalCollection();
        scheduler.tick();
        verify(client, times(9)).request(any(), anyString(), anyString());
        when(client.request(any(), anyString(), anyString())).thenReturn(Mono.just(response()));
        pending.tryEmitValue(response());
        scheduler.tick();
        verify(client, times(18)).request(any(), anyString(), anyString());
    }

    @Test void missedStartIsNotAutomaticallyRecovered() {
        setup();
        clock.now.set(Instant.parse("2026-11-24T01:00:00Z"));
        var scheduler = scheduler("005930");
        scheduler.startCollection(); // 초기화 전 신호는 무시
        scheduler.run(new DefaultApplicationArguments());
        scheduler.tick();
        scheduler.beginFinalCollection(); // 시작하지 않은 회차는 마지막 조회도 하지 않음
        scheduler.tick();
        verifyNoInteractions(client);
    }

    @Test void cronSchedulesUseSeoul0900And1531() throws Exception {
        var startSchedule = RealtimeCandleScheduler.class.getMethod("startCollection").getAnnotation(Scheduled.class);
        var finalSchedule = RealtimeCandleScheduler.class.getMethod("beginFinalCollection").getAnnotation(Scheduled.class);
        assertThat(startSchedule.zone()).isEqualTo("Asia/Seoul");
        assertThat(finalSchedule.zone()).isEqualTo("Asia/Seoul");
        var midnight = LocalDate.of(2026, 11, 24).atStartOfDay(ZoneId.of("Asia/Seoul"));
        assertThat(CronExpression.parse(startSchedule.cron()).next(midnight).toInstant())
                .isEqualTo(Instant.parse("2026-11-24T00:00:00Z"));
        assertThat(CronExpression.parse(finalSchedule.cron()).next(midnight).toInstant())
                .isEqualTo(Instant.parse("2026-11-24T06:31:00Z"));
    }

    @Test void rejectsMissingDuplicateAndUnknownTargets() {
        setup();
        assertThatThrownBy(() -> scheduler("").run(new DefaultApplicationArguments())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scheduler("005930,005930").run(new DefaultApplicationArguments())).isInstanceOf(IllegalArgumentException.class);
        when(stocks.findAllByCodeIn(anyList())).thenReturn(List.of());
        assertThatThrownBy(() -> scheduler("005930").run(new DefaultApplicationArguments())).isInstanceOf(IllegalArgumentException.class);
    }

    private void setup() {
        when(stocks.findAllByCodeIn(anyList())).thenReturn(List.of(Stock.builder().code("005930").build()));
        when(client.request(any(), anyString(), anyString())).thenReturn(Mono.just(response()));
    }

    private RealtimeCandleScheduler scheduler(String codes) {
        return new RealtimeCandleScheduler(codes, stocks, new RealtimeCandleRateLimiter(nanos::get), client,
                store, Schedulers.immediate(), clock);
    }

    private ResponseEntity<CandleResponse> response() {
        return ResponseEntity.ok(new ObjectMapper().readValue(
                "{\"return_code\":0,\"stk_cd\":\"005930\",\"stk_min_pole_chart_qry\":[]}", CandleResponse.class));
    }

    private static class MutableClock extends Clock {
        final AtomicReference<Instant> now;
        final ZoneId zone;
        MutableClock(Instant instant) { this(new AtomicReference<>(instant), ZoneOffset.UTC); }
        MutableClock(AtomicReference<Instant> now, ZoneId zone) { this.now = now; this.zone = zone; }
        public ZoneId getZone() { return zone; }
        public Clock withZone(ZoneId zone) { return new MutableClock(now, zone); }
        public Instant instant() { return now.get(); }
    }
}
