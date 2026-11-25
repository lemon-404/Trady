package trady.com.backend.global.kiwoom.candle.realtime;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import trady.com.backend.stock.repository.StockQueryRepository;
import trady.com.backend.stock.entity.Stock;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/** 초기화 후 대기하며, 시작·종료 cron 신호에 따라 거래일별 수집을 실행한다. */
@Slf4j
@Component
@Order(2)
@ConditionalOnProperty(name = "kiwoom.candle.realtime.enabled", havingValue = "true")
public class RealtimeCandleScheduler implements ApplicationRunner {
    private final String stockCodes;
    private final StockQueryRepository stocks;
    private final RealtimeCandleRateLimiter rateLimiter;
    private final RealtimeCandleClient client;
    private final RealtimeCandleStore store;
    private final Scheduler databaseScheduler;
    private final Clock clock;

    private RealtimeCandleDispatcher dispatcher;
    private RealtimeCandleCollector collector;
    private List<Stock> targets;
    private boolean initialized;
    private boolean running;
    private boolean stopped;

    public RealtimeCandleScheduler(@Value("${kiwoom.candle.realtime.stock-codes:}") String stockCodes,
            StockQueryRepository stocks, RealtimeCandleRateLimiter rateLimiter, RealtimeCandleClient client,
            RealtimeCandleStore store, @Qualifier("realtimeCandleDbScheduler") Scheduler databaseScheduler, Clock clock) {
        this.stockCodes = stockCodes;
        this.stocks = stocks;
        this.rateLimiter = rateLimiter;
        this.client = client;
        this.store = store;
        this.databaseScheduler = databaseScheduler;
        this.clock = clock.withZone(ZoneId.of("Asia/Seoul"));
    }

    @Override
    public synchronized void run(ApplicationArguments args) {
        if (initialized || stopped) return;
        var codes = Arrays.stream(stockCodes.split(",")).map(String::trim).toList();
        if (codes.isEmpty() || codes.size() > 10 || codes.stream().anyMatch(String::isBlank)
                || codes.stream().distinct().count() != codes.size()) {
            throw new IllegalArgumentException("수집할 종목 코드를 중복 없이 1~10개 지정해야 합니다.");
        }
        var found = stocks.findAllByCodeIn(codes);
        targets = codes.stream().map(code -> found.stream().filter(stock -> code.equals(stock.getCode()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("DB에 없는 수집 종목: " + code))).toList();

        initialized = true;
        log.info("실시간 차트 수집 준비 완료: 종목 수={}, 09:00 시작 대기", targets.size());
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public synchronized void startCollection() {
        if (!initialized || stopped || running) return;
        // 전 회차의 마지막 조회 상태를 재사용하지 않고 매일 새로운 수집 회차를 만든다.
        dispatcher = new RealtimeCandleDispatcher(targets, rateLimiter, clock.instant());
        collector = new RealtimeCandleCollector(dispatcher, client, store, databaseScheduler, clock);
        running = true;
        log.info("당일 실시간 차트 수집 시작");
    }

    @Scheduled(cron = "0 31 15 * * *", zone = "Asia/Seoul")
    public synchronized void beginFinalCollection() {
        if (!running) return;
        dispatcher.requestFinalCollection();
        log.info("당일 마지막 조회 모드 전환");
    }

    @Scheduled(fixedRate = 1000)
    public synchronized void tick() {
        // ApplicationRunner의 인증·기존 초기 수집·수집기 생성이 끝나기 전에는 실행하지 않는다.
        if (!running) return;
        collector.collect();
        if (dispatcher.isFinished()) {
            running = false;
            log.info("당일 실시간 차트 수집 종료: 마지막 조회 처리 완료");
        }
    }

    @PreDestroy
    public synchronized void stop() {
        stopped = true;
        running = false;
        if (dispatcher != null) dispatcher.stop();
        // DB 실행기의 수명은 RealtimeCandleDbConfig의 Spring 빈이 관리한다.
    }
}
