package trady.com.backend.global.kiwoom.candle.historical.service;

import dev.failsafe.Failsafe;
import dev.failsafe.RateLimiter;
import dev.failsafe.RetryPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import trady.com.backend.candle.service.CandleQueryService;
import trady.com.backend.global.kiwoom.candle.historical.CandleClient;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleRequest;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleResponse;
import trady.com.backend.global.kiwoom.error.KiwoomApiException;
import trady.com.backend.global.kiwoom.error.KiwoomErrorDispatcher;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.stock.repository.StockQueryRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleSyncService {
    private final StockQueryRepository stockQueryRepository;

    private final CandleClient candleClient;
    private final CandleQueryService candleSaveService;
    private final KiwoomErrorDispatcher errorDispatcher;

    private final RateLimiter<ResponseEntity<CandleResponse>> rateLimiter =
            RateLimiter
                    .<ResponseEntity<CandleResponse>>smoothBuilder(
                            5, Duration.ofSeconds(1))
                    .withMaxWaitTime(Duration.ofSeconds(5))
                    .build();

    private RetryPolicy<ResponseEntity<CandleResponse>> createRetryPolicy(
            Stock stock, String interval, int page
    ) {
        return RetryPolicy.<ResponseEntity<CandleResponse>>builder()
                    .handleIf(this::isRetryable)
                    .withBackoff(Duration.ofSeconds(1), Duration.ofSeconds(8))
                    .withMaxRetries(3) // 최초 요청 포함 최대 4번
                    .onRetry(event ->
                            log.warn("키움 요청 재시도: 종목={}, 주기={}, 페이지={}, 오류={}",
                                    stock.getCode(), interval, page,
                                    event.getLastException().getMessage()))
                    .build();
    }

    private boolean isRetryable(Throwable error) {
        if (!(error instanceof KiwoomApiException exception)) {
            return false;
        }
        Throwable cause = exception.getCause();
        return errorDispatcher.isRateLimited(exception)
                || cause instanceof TimeoutException
                || cause instanceof WebClientRequestException
                || (cause instanceof WebClientResponseException response
                    && response.getStatusCode().is5xxServerError());
    }

    private String[] minuteIntervals = {"1", "5", "15", "30", "60"};
    private String[] intervals = {"1d", "1w", "1M", "1y"};

    private String baseDt = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

    public void syncStockCandleList() {

        List<Stock> stocks = stockQueryRepository.findAll();
        syncCandlesForStocks(stocks);
    }

    // 지정한 종목들의 모든 봉 주기를 수집한다.
    void syncCandlesForStocks(List<Stock> stocks) {

        //1. 종목 50개
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);

            syncMinuteCandles("ka10080", minuteIntervals, stock); //분
            syncCandlesForInterval("ka10081", intervals[0], stock); //일
            syncCandlesForInterval("ka10082", intervals[1], stock); //주
            syncCandlesForInterval("ka10083", intervals[2], stock); //월
            syncCandlesForInterval("ka10094", intervals[3], stock); //년
        }
    }

    // 멀티 봉 주기 수집 (분봉)
    private void syncMinuteCandles(
            String tr,
            String[] intervals,
            Stock stock
    ) {
        for (String interval : intervals) {
            syncCandlesForInterval(tr, interval, stock);
        }
    }

    // 단일 봉 주기의 요청을 생성하고 수집한다.
    private void syncCandlesForInterval(
            String tr,
            String interval,
            Stock stock
    ) {
        CandleRequest request = new CandleRequest(
                stock.getCode(),
                baseDt,
                interval
        );
        fetchCandlePages(tr, interval, stock, request);
    }

    private void fetchCandlePages(
            String tr,
            String interval,
            Stock stock,
            CandleRequest request
    ) {
        String nextKey = "N";
        String contYn;
        int page = 1;

        do {
            // 람다에서 사용하므로 현재 페이지의 키를 복사
            String requestNextKey = nextKey;
            log.info("키움 페이지 수집 시작: 종목={}, 주기={}, 페이지={}",
                    stock.getCode(), interval, page);
            long requestStarted = System.nanoTime();

            ResponseEntity<CandleResponse> response;
            try {
                response = Failsafe.with(createRetryPolicy(stock, interval, page), rateLimiter)
                            .get(() -> candleClient.request(
                                    request,
                                    requestNextKey,
                                    tr,
                                    CandleResponse.class
                            ));
            } catch (KiwoomApiException exception) {
                log.error("키움 페이지 수집 실패: 종목={}, 주기={}, 페이지={}, 반환코드={}, 오류코드={}",
                        stock.getCode(), interval, page,
                        exception.getReturnCode(), exception.getErrorCode());
                errorDispatcher.handle(exception);
                throw exception;
            }

            double requestMs =
                    (System.nanoTime() - requestStarted) / 1_000_000.0;

            CandleResponse body = response.getBody();

            if (body == null || body.getList() == null) {
                throw new IllegalStateException(
                        "캔들 목록 누락: stock=" + stock.getCode()
                                + ", tr=" + tr
                                + ", page=" + page
                );
            }

            long dbStarted = System.nanoTime();

            candleSaveService.savePage(stock, interval, body.getList());

            double dbMs = (System.nanoTime() - dbStarted) / 1_000_000.0;

            log.info(
                    "키움 페이지 저장 완료: 종목={}, 주기={}, 페이지={}: 요청 전체={}ms, DB={}ms, 캔들={}개",
                    stock.getCode(), interval, page,
                    requestMs, dbMs, body.getList().size()
            );

            contYn = response.getHeaders().getFirst("cont-yn");
            nextKey = response.getHeaders().getFirst("next-key");


            if ("Y".equals(contYn) && (nextKey == null || nextKey.isBlank())) {
                throw new IllegalStateException("연속조회 next-key 누락");
            }

            page++;
        } while ("Y".equals(contYn));
    }




}
