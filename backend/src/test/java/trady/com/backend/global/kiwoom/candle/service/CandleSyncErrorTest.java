package trady.com.backend.global.kiwoom.candle.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import trady.com.backend.candle.service.CandleQueryService;
import trady.com.backend.candle.domain.CandleInterval;
import trady.com.backend.global.kiwoom.candle.historical.CandleClient;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleRequest;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleResponse;
import trady.com.backend.global.kiwoom.candle.historical.service.CandleSyncService;
import trady.com.backend.global.kiwoom.error.KiwoomApiException;
import trady.com.backend.global.kiwoom.error.KiwoomErrorDispatcher;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.stock.repository.StockQueryRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CandleSyncErrorTest {
    private final CandleClient client = mock(CandleClient.class);
    private final CandleQueryService saveService = mock(CandleQueryService.class);
    private final KiwoomErrorDispatcher dispatcher = spy(new KiwoomErrorDispatcher());
    private final CandleSyncService service = new CandleSyncService(
            mock(StockQueryRepository.class), client, saveService, dispatcher);
    private final Stock stock = new Stock("005930", "삼성전자", "코스피");
    private final CandleRequest request = new CandleRequest("005930", "20261112", "1");

    @Test
    void retriesWrappedHttpFailureWithoutDispatchingRecoveredError() {
        var error = new KiwoomApiException(null, null, "502", new WebClientResponseException(
                502, "Bad Gateway", null, null, null));
        var body = mock(CandleResponse.class);
        when(body.getList()).thenReturn(List.of());
        when(client.request(request, "N", "ka11080", CandleResponse.class))
                .thenThrow(error)
                .thenReturn(ResponseEntity.ok().header("cont-yn", "N").body(body));

        collect();

        verify(client, times(2)).request(request, "N", "ka11080", CandleResponse.class);
        verify(dispatcher, never()).handle(any());
        verify(saveService).savePage(stock, CandleInterval.MINUTE_1, List.of());
    }

    @Test
    void dispatchesFinalFailureOnceAndPropagatesIt() {
        var error = new KiwoomApiException("2", "8005", "인증 실패", null);
        when(client.request(request, "N", "ka11080", CandleResponse.class)).thenThrow(error);

        assertThatThrownBy(this::collect).isSameAs(error);

        verify(client).request(request, "N", "ka11080", CandleResponse.class);
        verify(dispatcher).handle(error);
        verifyNoInteractions(saveService);
    }

    private void collect() {
        ReflectionTestUtils.invokeMethod(service, "fetchCandlePages", CandleInterval.MINUTE_1, stock, request);
    }

    @ParameterizedTest
    @EnumSource(CandleInterval.class)
    void collectionUsesSharedMappingAndSavesDomainInterval(CandleInterval interval) {
        var body = mock(CandleResponse.class);
        when(body.getList()).thenReturn(List.of());
        when(client.request(any(CandleRequest.class), eq("N"), anyString(), eq(CandleResponse.class)))
                .thenReturn(ResponseEntity.ok().header("cont-yn", "N").body(body));

        ReflectionTestUtils.invokeMethod(service, "syncCandlesForInterval", interval, stock);

        verify(saveService).savePage(stock, interval, List.of());
        verify(client).request(argThat(value -> interval.isMinute()
                        ? Integer.toString(interval.minutes()).equals(value.getInterval())
                        : value.getInterval() == null), eq("N"), anyString(), eq(CandleResponse.class));
    }
}
