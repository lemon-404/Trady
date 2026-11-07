package trady.com.backend.global.kiwoom.watchlist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import trady.com.backend.global.kiwoom.error.KiwoomApiException;
import trady.com.backend.global.kiwoom.watchlist.dto.KiwoomWatchlistRequest;
import trady.com.backend.global.kiwoom.watchlist.dto.KiwoomWatchlistResponse;
import trady.com.backend.stock.repository.StockQueryRepository;
import trady.com.backend.watchlist.entity.Watchlist;
import trady.com.backend.watchlist.service.WatchlistQueryService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock private WatchlistClient watchlistClient;
    @Mock private WatchlistQueryService watchlistQueryService;
    @Mock private StockQueryRepository stockQueryRepository;
    @InjectMocks private WatchlistService service;

    @Captor private ArgumentCaptor<KiwoomWatchlistRequest> requestCaptor;
    @Captor private ArgumentCaptor<Set<Watchlist>> watchlistCaptor;

    @Test
    void 정상_응답의_종목코드_현재가_등락률_수집시각을_저장한다() {
        // given: 저장된 종목코드로 조회할 시세 응답을 준비한다.
        when(stockQueryRepository.findAllCodes())
                .thenReturn(List.of("005930", "000660"));
        var items = List.of(
                new KiwoomWatchlistResponse.WatchlistItem(
                        "005930", 75000L, new BigDecimal("1.35")),
                new KiwoomWatchlistResponse.WatchlistItem(
                        "000660", 182000L, new BigDecimal("-0.82"))
        );
        var response = ResponseEntity.ok(
                new KiwoomWatchlistResponse("0", "정상적으로 처리되었습니다", items));
        when(watchlistClient.getWatchlist(any(), eq("N")))
                .thenReturn(response);
        LocalDateTime before = LocalDateTime.now();

        // when
        service.persistWatchlist();

        LocalDateTime after = LocalDateTime.now();

        // then: 대상 종목코드를 키움에 요청한 뒤 저장하는 순서를 확인한다.
        var order = inOrder(stockQueryRepository, watchlistClient, watchlistQueryService);
        order.verify(stockQueryRepository).findAllCodes();
        order.verify(watchlistClient).getWatchlist(requestCaptor.capture(), eq("N"));
        order.verify(watchlistQueryService).saveAll(watchlistCaptor.capture());
        order.verifyNoMoreInteractions();
        assertThat(requestCaptor.getValue().stockCodes())
                .containsExactly("005930", "000660");

        Set<Watchlist> saved = watchlistCaptor.getValue();
        assertThat(saved).extracting(Watchlist::getCode)
                .containsExactlyInAnyOrder("005930", "000660");
        for (var item : items) {
            assertThat(saved).filteredOn(quote -> quote.getCode().equals(item.stockCode()))
                    .singleElement().satisfies(quote -> {
                        assertThat(quote.getBasePrice()).isEqualTo(item.basePrice());
                        assertThat(quote.getRate()).isEqualByComparingTo(item.rate());
                        assertThat(quote.getCollectedAt()).isBetween(before, after);
                    });
        }
    }

    @Test
    void 인증_실패시_예외를_전파하고_추가_요청과_저장을_하지_않는다() {
        // given: 첫 배치 이후에도 요청할 종목이 있도록 50개를 준비한다.
        List<String> stockCodes = IntStream.range(0, 50)
                .mapToObj(i -> String.format("%06d", i))
                .toList();
        when(stockQueryRepository.findAllCodes()).thenReturn(stockCodes);

        KiwoomApiException exception = new KiwoomApiException(
                "3", "8005", "인증에 실패했습니다[8005:Token이 유효하지 않습니다]", null
        );
        when(watchlistClient.getWatchlist(any(), eq("N")))
                .thenThrow(exception);

        // when & then: 클라이언트에서 발생한 예외를 그대로 전달한다.
        assertThatThrownBy(service::persistWatchlist)
                .isSameAs(exception);

        // 첫 요청이 실패하면 나머지 배치를 요청하지 않는다.
        verify(watchlistClient).getWatchlist(any(), eq("N"));
        verifyNoMoreInteractions(watchlistClient);

        // 수집에 실패했으므로 저장을 시도하지 않는다.
        verifyNoInteractions(watchlistQueryService);
    }
}
