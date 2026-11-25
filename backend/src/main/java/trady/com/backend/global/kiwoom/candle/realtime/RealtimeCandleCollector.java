package trady.com.backend.global.kiwoom.candle.realtime;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import trady.com.backend.global.kiwoom.candle.KiwoomCandleMapping;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** 배분된 작업의 비동기 요청과 봉 저장을 연결한다. */
@Slf4j
public class RealtimeCandleCollector {
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Seoul");

    private final RealtimeCandleDispatcher dispatcher;
    private final RealtimeCandleClient client;
    private final RealtimeCandleStore store;
    private final Scheduler databaseScheduler;
    private final Clock clock;

    public RealtimeCandleCollector(RealtimeCandleDispatcher dispatcher, RealtimeCandleClient client,
                                  RealtimeCandleStore store, Scheduler databaseScheduler, Clock clock) {
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.client = Objects.requireNonNull(client);
        this.store = Objects.requireNonNull(store);
        this.databaseScheduler = Objects.requireNonNull(databaseScheduler);
        this.clock = Objects.requireNonNull(clock);
    }

    public void collect() {
        var now = clock.instant();
        String baseDate = LocalDate.ofInstant(now, MARKET_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE);

        // 작업마다 독립적으로 구독하여 느린 응답이 다른 종목·주기의 요청을 막지 않게 한다.
        for (var task : dispatcher.dispatch(now)) {
            request(task, baseDate);
        }
    }

    public void collectFinal() {
        dispatcher.requestFinalCollection();
        collect();
    }

    private void request(RealtimeCandleDispatcher.Task task, String baseDate) {
        // 클라이언트 메서드 자체에서 예외가 발생해도 Reactor 오류 경로로 처리한다.
        Mono.defer(() -> client.request(
                        KiwoomCandleMapping.request(task.stock().getCode(), baseDate, task.scope().interval()),
                        task.scope().tr(),
                        "N"))
                .flatMap(response -> Mono.fromCallable(() -> store.save(task, response.getBody().getList(),
                                "Y".equals(response.getHeaders().getFirst("cont-yn"))))
                        .subscribeOn(databaseScheduler))
                // 구독을 외부에 노출하지 않는다. 취소 신호만으로 저장 중인 키를 해제하지 않는다.
                .subscribe(savedCount -> log.info("실시간 봉 저장 완료: 종목={}, 주기={}, 봉 개수={}",
                                task.stock().getCode(), task.scope().interval(), savedCount),
                        error -> {
                            log.warn("실시간 봉 수집·저장 실패: 종목={}, 주기={}, 오류={}",
                                    task.stock().getCode(), task.scope().interval(), error.toString());
                            dispatcher.complete(task);
                        },
                        () -> dispatcher.complete(task));
    }
}
