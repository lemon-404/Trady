package trady.com.backend.global.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import trady.com.backend.global.kiwoom.candle.service.StockCandleSyncService;
import trady.com.backend.global.kiwoom.stock.service.StockSyncService;

@Slf4j
@Order(1)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "candle.sync-setup",
        havingValue = "true"
)
public class MarketDataSyncRunner implements ApplicationRunner {

    private final StockSyncService stockSyncService;
    private final StockCandleSyncService stockCandleSyncService;

    @Override
    public void run(ApplicationArguments args) {
        stockSyncService.syncStockInfoList();

        long start = System.nanoTime();

        stockCandleSyncService.syncStockCandleList();
        double elapsedSeconds =
                (System.nanoTime() - start) / 1_000_000_000.0;

        log.info("종목·캔들 동기화 완료. 총 소요 시간: {}초", elapsedSeconds);
        log.info("종목 정보 및 과거 캔들 동기화 완료");
    }
}