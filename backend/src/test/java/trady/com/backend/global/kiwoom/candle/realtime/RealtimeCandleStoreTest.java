package trady.com.backend.global.kiwoom.candle.realtime;

import org.junit.jupiter.api.Test;
import trady.com.backend.candle.domain.CandleInterval;
import org.springframework.data.domain.Pageable;
import trady.com.backend.candle.entity.Candle;
import trady.com.backend.candle.repository.CandleQueryRepository;
import trady.com.backend.candle.service.CandleQueryService;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleResponse.CandleItem;
import trady.com.backend.stock.entity.Stock;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RealtimeCandleStoreTest {
    private final CandleQueryRepository repository = mock(CandleQueryRepository.class);
    private final CandleQueryService saver = mock(CandleQueryService.class);
    private final RealtimeCandleStore store = new RealtimeCandleStore(repository, saver, new RealtimeCandleProcessor());
    private final Stock stock = Stock.builder().code("005930").build();
    private final RealtimeCandleDispatcher.Task task = new RealtimeCandleDispatcher.Task(stock, RealtimeCandleScope.MINUTE_1);

    @Test void initializesFromDatabaseAndAdvancesOnlyAfterSuccessfulSave() {
        when(repository.findInitialCandles(anyString(), any(CandleInterval.class), any(Pageable.class)))
                .thenReturn(List.of(Candle.builder().interval(CandleInterval.MINUTE_1).time(time(2)).build()));
        assertThat(store.save(task, bars(0, 1, 2, 3, 4), false)).isEqualTo(4);
        verify(saver).savePage(eq(stock), eq(CandleInterval.MINUTE_1), argThat(items -> items.size() == 4));

        store.save(task, bars(0, 1, 2, 3, 4, 5), false);
        verify(saver).savePage(eq(stock), eq(CandleInterval.MINUTE_1), argThat(items -> items.size() == 3 && items.get(0).time().equals("20261124090300")));
        verify(repository, times(1)).findInitialCandles(eq("005930"), eq(CandleInterval.MINUTE_1), any(Pageable.class));
    }

    @Test void commitFailureDoesNotAdvanceCheckpoint() {
        when(repository.findInitialCandles(anyString(), any(CandleInterval.class), any(Pageable.class)))
                .thenReturn(List.of(Candle.builder().interval(CandleInterval.MINUTE_1).time(time(1)).build()));
        doThrow(new IllegalStateException("commit failed")).doNothing().when(saver).savePage(any(), any(CandleInterval.class), anyList());
        assertThatThrownBy(() -> store.save(task, bars(0, 1, 2, 3), false)).isInstanceOf(IllegalStateException.class);
        store.save(task, bars(0, 1, 2, 3), false);
        verify(saver, times(2)).savePage(eq(stock), eq(CandleInterval.MINUTE_1), argThat(items -> items.size() == 4));
    }

    @Test void emptyResponseDoesNotSaveAndBootstrapKeepsLatestTwo() {
        assertThat(store.save(task, List.of(), false)).isZero();
        verifyNoInteractions(saver);
        assertThat(store.save(task, bars(0, 1, 2, 3), false)).isEqualTo(2);
        verify(saver).savePage(eq(stock), eq(CandleInterval.MINUTE_1), argThat(items -> items.get(0).time().equals("20261124090200")));
    }

    @Test void incompleteFirstPageDoesNotSkipMissingHistory() {
        when(repository.findInitialCandles(anyString(), any(CandleInterval.class), any(Pageable.class)))
                .thenReturn(List.of(Candle.builder().interval(CandleInterval.MINUTE_1).time(time(0)).build()));
        assertThatThrownBy(() -> store.save(task, bars(3, 4, 5), true)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(saver);
    }

    @Test void excludedTimestampCannotBeForgottenByAdvancingCheckpoint() {
        when(repository.findInitialCandles(anyString(), any(CandleInterval.class), any(Pageable.class)))
                .thenReturn(List.of(Candle.builder().interval(CandleInterval.MINUTE_1).time(time(0)).build()));
        var invalid = new CandleItem(10, 9, 8, 11, 1, "20261124090100");
        assertThatThrownBy(() -> store.save(task, List.of(bars(0).get(0), invalid, bars(2).get(0)), false))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(saver);
    }

    private LocalDateTime time(int minute) { return LocalDateTime.of(2026, 11, 24, 9, minute); }
    private List<CandleItem> bars(int... minutes) {
        return java.util.Arrays.stream(minutes).mapToObj(minute ->
                new CandleItem(10, 12, 9, 11, 100, "2026112409%02d00".formatted(minute))).toList();
    }
}
