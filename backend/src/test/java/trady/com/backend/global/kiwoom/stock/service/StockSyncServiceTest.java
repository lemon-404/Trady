package trady.com.backend.global.kiwoom.stock.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;
import trady.com.backend.global.kiwoom.error.KiwoomApiException;
import trady.com.backend.global.kiwoom.stock.client.KiwoomStockClient;
import trady.com.backend.global.kiwoom.stock.dto.KiwoomStockListResponse;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.stock.service.StockService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockSyncServiceTest  {

    @Mock
    private KiwoomStockClient stockClient;

    @Mock
    private StockService stockService;

    @InjectMocks
    private StockSyncService stockSyncService;

    @Captor
    private ArgumentCaptor<List<Stock>> stocksCaptor;

    @Test
    @DisplayName("정상_케이스")
    void test() {
        // given
        KiwoomStockListResponse.StockInfo stockInfo = new KiwoomStockListResponse.StockInfo(
                "005930",
                "삼성전자",
                "정상",
                "0",
                "거래소"
        );

        KiwoomStockListResponse body = new KiwoomStockListResponse(
                List.of(stockInfo),
                "0",
                "정상적으로 처리되었습니다"
               );

        ResponseEntity<KiwoomStockListResponse> response =
                ResponseEntity.ok()
                        .header("cont-yn", "N")
                        .body(body);
        //when
        when(stockClient.getStockList("N"))
                .thenReturn(response);

        stockSyncService.syncStockInfoList();

        //then
        verify(stockService).savePage(stocksCaptor.capture());

        List<Stock> savedStocks = stocksCaptor.getValue();

        assertThat(savedStocks).hasSize(1);

        Stock stock = savedStocks.get(0);
        assertThat(stock.getCode()).isEqualTo("005930");
        assertThat(stock.getName()).isEqualTo("삼성전자");
        assertThat(stock.getMarket()).isEqualTo("코스피");

        verify(stockClient).getStockList("N");
    }

    @Test
    @DisplayName("키움_API_인증이_실패한다면")
    void ex1_stockInfoCollectTest() {
        // given: 수집 서비스의 로그를 캡처
        Logger logger = (Logger) LoggerFactory.getLogger(StockSyncService.class);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            KiwoomApiException exception = new KiwoomApiException(
                    "3",
                    "8005",
                    "인증에 실패했습니다[8005:Token이 유효하지 않습니다]",
                    null
            );

            when(stockClient.getStockList("N"))
                    .thenThrow(exception);

            // when & then: 예외를 삼키지 않고 수집 중단
            assertThatThrownBy(() -> stockSyncService.syncStockInfoList())
                    .isSameAs(exception);

            // 추가 API 호출 및 저장 요청 없음
            verify(stockClient).getStockList("N");
            verifyNoMoreInteractions(stockClient);
            verifyNoInteractions(stockService);

            // 인증 실패 내역 기록
            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage())
                        .contains("종목 수집 인증 실패")
                        .contains("8005");
            });
        } finally {
            // 다른 테스트에 영향을 주지 않도록 로그 캡처 해제
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void 종목명이_누락된_종목은_제외하고_정상_종목만_저장한다() {
        // given
        var normalStock = new KiwoomStockListResponse.StockInfo(
                "005930", "삼성전자", "정상", "0", "거래소"
        );

        var invalidStock = new KiwoomStockListResponse.StockInfo(
                "000660", null, "정상", "0", "거래소"
        );

        var body = new KiwoomStockListResponse(
                List.of(normalStock, invalidStock),
                "0",
                "정상적으로 처리되었습니다"
        );

        var response = ResponseEntity.ok()
                .header("cont-yn", "N")
                .body(body);

        when(stockClient.getStockList("N")).thenReturn(response);

        // when: 누락된 종목 때문에 전체 수집이 중단되면 테스트 실패
        stockSyncService.syncStockInfoList();

        // then
        verify(stockService).savePage(stocksCaptor.capture());

        List<Stock> savedStocks = stocksCaptor.getValue();

        assertThat(savedStocks).singleElement().satisfies(stock -> {
            assertThat(stock.getCode()).isEqualTo("005930");
            assertThat(stock.getName()).isEqualTo("삼성전자");
            assertThat(stock.getMarket()).isEqualTo("코스피");
        });
    }

    @Test
    void DB_저장_실패시_예외를_전파한다() {
        // given: 정상 수집 응답
        var stockInfo = new KiwoomStockListResponse.StockInfo(
                "005930", "삼성전자", "정상", "0", "거래소"
        );

        var body = new KiwoomStockListResponse(
                List.of(stockInfo),
                "0",
                "정상적으로 처리되었습니다"
        );

        var response = ResponseEntity.ok()
                .header("cont-yn", "N")
                .body(body);

        when(stockClient.getStockList("N")).thenReturn(response);

        // 저장 단계에서 DB 장애 발생
        var exception =
                new DataAccessResourceFailureException("DB 연결 실패");

        doThrow(exception)
                .when(stockService).savePage(anyList());

        // when & then: 저장 실패를 호출자에게 전달
        assertThatThrownBy(() -> stockSyncService.syncStockInfoList())
                .isSameAs(exception);

        // 정상 수집된 종목으로 저장을 시도했는지 확인
        verify(stockService).savePage(stocksCaptor.capture());

        assertThat(stocksCaptor.getValue())
                .singleElement()
                .satisfies(stock ->
                        assertThat(stock.getCode()).isEqualTo("005930")
                );
    }


}