package trady.com.backend.global.kiwoom.stock.client;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import trady.com.backend.global.kiwoom.candle.KiwoomStockCandleClient;
import trady.com.backend.global.kiwoom.candle.dto.KiwoomStockCandleReqeust;
import trady.com.backend.global.kiwoom.candle.dto.KiwoomStockCandleResponse;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@SpringBootTest
class KiwoomStockCandleClientTest {

    @Autowired
    private KiwoomStockCandleClient client;

    private KiwoomStockCandleReqeust reqeust1;
    private KiwoomStockCandleReqeust reqeust5;
    private KiwoomStockCandleReqeust reqeust15;

    @BeforeEach
    void setup() {
        reqeust1 = new KiwoomStockCandleReqeust(
                "005930",
                "1",
                "20261009"
        );
        reqeust5 = new KiwoomStockCandleReqeust(
                "005930",
                "5",
                "20261009"
        );
        reqeust15 = new KiwoomStockCandleReqeust(
                "005930",
                "15",
                "20261009"
        );
    }

    @AfterEach
    void end() throws InterruptedException {
        Thread.sleep(1000);
    }
    @Test
    @DisplayName("1분_단일_요청_응답시간_측정")
    void get1mSingleKiwoomStockCandle() throws InterruptedException {
        long start = System.nanoTime();

        KiwoomStockCandleResponse response = client.getKiwoomStockCandle(reqeust1, "N").getBody();

        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(response);

        log.info("1회 요청 소요 시간: {}ms", elapsedMs);
        log.info("응답 객체 크기: {}",response.list().size());
        log.info("응답 : {}",response);

    }

    @Test
    @DisplayName("5분_단일_요청_응답시간_측정")
    void get5mSingleKiwoomStockCandle() throws InterruptedException {
        long start = System.nanoTime();

        KiwoomStockCandleResponse response = client.getKiwoomStockCandle(reqeust5, "N").getBody();

        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(response);

        log.info("1회 요청 소요 시간: {}ms", elapsedMs);
        log.info("응답 객체 크기: {}",response.list().size());
        log.info("응답 : {}",response);

    }

    @Test
    @DisplayName("15분_단일_요청_응답시간_측정")
    void get15mSingleKiwoomStockCandle() throws InterruptedException {
        long start = System.nanoTime();

        KiwoomStockCandleResponse response = client.getKiwoomStockCandle(reqeust15, "N").getBody();

        double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;

        assertNotNull(response);

        log.info("1회 요청 소요 시간: {}ms", elapsedMs);
        log.info("응답 객체 크기: {}",response.list().size());
        log.info("응답 : {}",response);

    }

    @Test
    @DisplayName("15분_초당_1회_요청_응답시간_측정")
    void get15mSinglePerSecondKiwoomStockCandle() throws InterruptedException {
        measureRequests(10, 1000);

    }

    @Test
    @DisplayName("15분_초당_2회_요청_응답시간_측정")
    void get15mMultiKiwoomStockCandle() throws InterruptedException {
        assertThrows(WebClientResponseException.TooManyRequests.class,
                () -> measureRequests(10, 500));

    }

    private void measureRequests(int count, long intervalMs)
            throws InterruptedException {

        double sum = 0;
        long previousStart = 0;

        for (int i = 0; i < count; i++) {
            // 이전 요청 시작 시점부터 지정한 간격만큼 기다린다.
            if (i > 0) {
                long remainingNanos = intervalMs * 1_000_000L
                        - (System.nanoTime() - previousStart);

                if (remainingNanos > 0) {
                    TimeUnit.NANOSECONDS.sleep(remainingNanos);
                }
            }

            long start = System.nanoTime();
            previousStart = start;

            KiwoomStockCandleResponse response =
                    client.getKiwoomStockCandle(reqeust15, "N").getBody();

            double elapsedMs =
                    (System.nanoTime() - start) / 1_000_000.0;

            assertNotNull(response);
            sum += elapsedMs;

            log.info("{}회 요청 소요 시간: {}ms", i + 1, elapsedMs);
            log.info("응답 객체 크기: {}",response.list().size());
        }

        log.info("{}회 평균 요청 소요 시간: {}ms", count, sum / count);
    }

}