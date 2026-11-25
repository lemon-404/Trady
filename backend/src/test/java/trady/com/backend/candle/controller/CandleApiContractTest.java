package trady.com.backend.candle.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import trady.com.backend.candle.support.CandleDataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import trady.com.backend.candle.entity.Candle;
import trady.com.backend.candle.domain.CandleInterval;
import trady.com.backend.candle.repository.CandleQueryRepository;
import trady.com.backend.candle.service.CandleQueryService;
import trady.com.backend.global.exception.GlobalExceptionHandler;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.stock.repository.StockQueryRepository;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@CandleDataJpaTest
@Import({CandleController.class, CandleQueryService.class})
class CandleApiContractTest {
    @Autowired CandleController controller;
    @Autowired StockQueryRepository stocks;
    @Autowired CandleQueryRepository candles;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new CandleExceptionHandler(), new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"1m,1m", "5m,5m", "15m,15m", "30m,30m", "60m,60m", "1d,1d", "1w,1w", "1M,1M", "1y,1y"})
    void supportsDocumentedIntervalsAndWrapsResponse(String apiInterval, String storedInterval) throws Exception {
        Stock stock = stocks.save(new Stock("005930", "삼성전자", "코스피"));
        candles.saveAndFlush(new Candle(stock, CandleInterval.fromCode(storedInterval), LocalDateTime.of(2026, 11, 2, 9, 0),
                78000, 78500, 77800, 78200, 125000L));

        mvc.perform(post("/trady/v1/stock/candle").header("cont-yn", "N")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("005930", apiInterval, "null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("차트 조회 성공"))
                .andExpect(jsonPath("$.data.nextCursor").isEmpty())
                .andExpect(jsonPath("$.data['cont-yn']").value("N"))
                .andExpect(jsonPath("$.data.candles.length()").value(1))
                .andExpect(jsonPath("$.data.candles[0].time").value("20261102090000"))
                .andExpect(jsonPath("$.data.candles[0].open_price").value(78000));
    }

    @Test
    void existingStockWithoutCandlesReturnsEmptySuccess() throws Exception {
        stocks.saveAndFlush(new Stock("005930", "삼성전자", "코스피"));
        mvc.perform(post("/trady/v1/stock/candle").header("cont-yn", "N")
                        .contentType(MediaType.APPLICATION_JSON).content(body("005930", "1m", "null")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.candles").isEmpty())
                .andExpect(jsonPath("$.data.nextCursor").isEmpty())
                .andExpect(jsonPath("$.data['cont-yn']").value("N"));
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 101})
    void defaultPageSizeIs100(int count) throws Exception {
        Stock stock = stocks.save(new Stock("005930", "삼성전자", "코스피"));
        LocalDateTime first = LocalDateTime.of(2026, 11, 2, 9, 0);
        for (int i = 0; i < count; i++) {
            candles.save(new Candle(stock, CandleInterval.MINUTE_1, first.plusMinutes(i), 100, 120, 90, 110, 50L));
        }
        candles.flush();
        var result = mvc.perform(post("/trady/v1/stock/candle").header("cont-yn", "N")
                        .contentType(MediaType.APPLICATION_JSON).content(body("005930", "1m", "null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candles.length()").value(100));
        if (count > 100) {
            result.andExpect(jsonPath("$.data['cont-yn']").value("Y"))
                    .andExpect(jsonPath("$.data.nextCursor").value("2026-11-02T09:01:00"));
        } else {
            result.andExpect(jsonPath("$.data['cont-yn']").value("N"))
                    .andExpect(jsonPath("$.data.nextCursor").isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void databaseFailureReturnsDocumented500WithoutInternalDetails(boolean connectionFailure) throws Exception {
        CandleQueryService failingService = mock(CandleQueryService.class);
        RuntimeException failure = connectionFailure
                ? new CannotCreateTransactionException("private database details")
                : new DataAccessResourceFailureException("private database details");
        when(failingService.getCandles(any())).thenThrow(failure);
        MockMvc failingMvc = MockMvcBuilders.standaloneSetup(new CandleController(failingService))
                .setControllerAdvice(new CandleExceptionHandler(), new GlobalExceptionHandler()).build();
        failingMvc.perform(post("/trady/v1/stock/candle").header("cont-yn", "N")
                        .contentType(MediaType.APPLICATION_JSON).content(body("005930", "1m", "null")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(9000))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                .andExpect(jsonPath("$.errors[0].field").isEmpty())
                .andExpect(jsonPath("$.errors[0].message").value("차트 데이터 조회 중 오류가 발생했습니다."));
    }

    @ParameterizedTest
    @CsvSource({"1m,1m", "5m,5m", "15m,15m", "30m,30m", "60m,60m", "1d,1d", "1w,1w", "1M,1M", "1y,1y"})
    void continuousQueryExcludesCursorForEveryInterval(String apiInterval, String storedInterval) throws Exception {
        Stock stock = stocks.save(new Stock("005930", "삼성전자", "코스피"));
        LocalDateTime oldest = LocalDateTime.of(2026, 11, 2, 9, 0);
        for (int i = 0; i < 3; i++) {
            candles.save(new Candle(stock, CandleInterval.fromCode(storedInterval), oldest.plusDays(i), 100, 120, 90, 110, 50L));
        }
        candles.flush();
        mvc.perform(post("/trady/v1/stock/candle").header("cont-yn", "Y")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("005930", apiInterval, "\"2026-11-03T09:00:00\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candles.length()").value(1))
                .andExpect(jsonPath("$.data.candles[0].time").value("20261102090000"))
                .andExpect(jsonPath("$.data['cont-yn']").value("N"));
    }

    @Test
    void missingStockReturnsDocumented404() throws Exception {
        mvc.perform(post("/trady/v1/stock/candle").header("cont-yn", "N")
                        .contentType(MediaType.APPLICATION_JSON).content(body("999999", "1m", "null")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(3000))
                .andExpect(jsonPath("$.message").value("종목 정보를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.errors[0].field").isEmpty())
                .andExpect(jsonPath("$.errors[0].message").value("요청한 종목 정보가 존재하지 않습니다."));
    }

    @ParameterizedTest
    @CsvSource({"2m", "1", "1Y"})
    void invalidIntervalReturnsFieldError(String interval) throws Exception {
        mvc.perform(post("/trady/v1/stock/candle").header("cont-yn", "N")
                        .contentType(MediaType.APPLICATION_JSON).content(body("005930", interval, "null")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("올바르지 않은 요청입니다."))
                .andExpect(jsonPath("$.errors[0].field").value("interval"))
                .andExpect(jsonPath("$.errors[0].message").value("지원하지 않는 봉 단위입니다."));
    }

    @ParameterizedTest
    @CsvSource({"'\"not-a-date\"'", "'\"2026-02-30T09:00:00\"'", "'null'"})
    void invalidContinuousCursorReturnsFieldError(String cursor) throws Exception {
        mvc.perform(post("/trady/v1/stock/candle").header("cont-yn", "Y")
                        .contentType(MediaType.APPLICATION_JSON).content(body("005930", "1m", cursor)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("올바르지 않은 요청입니다."))
                .andExpect(jsonPath("$.errors[0].field").value("cursor"))
                .andExpect(jsonPath("$.errors[0].message").value("올바르지 않은 조회 기준 시각입니다."));
    }

    private String body(String stockCode, String interval, String cursor) {
        return """
                {"stockCode":"%s","interval":"%s","cursor":%s}
                """.formatted(stockCode, interval, cursor);
    }
}
