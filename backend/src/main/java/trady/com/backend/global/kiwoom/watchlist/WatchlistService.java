package trady.com.backend.global.kiwoom.watchlist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import trady.com.backend.global.kiwoom.watchlist.dto.KiwoomWatchlistRequest;
import trady.com.backend.global.kiwoom.watchlist.dto.KiwoomWatchlistResponse;
import trady.com.backend.stock.repository.StockQueryRepository;
import trady.com.backend.watchlist.entity.Watchlist;
import trady.com.backend.watchlist.service.WatchlistQueryService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistService {
    private final WatchlistClient watchlistClient;
    private final WatchlistQueryService watchlistQueryService;

    private final StockQueryRepository stockQueryRepository;

    public void persistWatchlist() {
        LocalDateTime collectedAt = LocalDateTime.now();
        List<String> stockCodes = stockQueryRepository.findAllCodes();
        String nextKey = "N";
        int index = 0;

        while (index < stockCodes.size()) {

            int endIndex = Math.min(index + 20, stockCodes.size());
            List<String> batch = stockCodes.subList(index, endIndex);
            KiwoomWatchlistRequest request = new KiwoomWatchlistRequest(batch.toArray(String[]::new));


            ResponseEntity<KiwoomWatchlistResponse> response = watchlistClient.getWatchlist(request, nextKey);

            Set<Watchlist> watchlist = response.getBody().getWatchlist().stream()
                    .map(item -> item.toResponseDto(item, collectedAt))
                    .collect(Collectors.toSet());

            checkMissingWatchlistStocks(response, batch);

            watchlistQueryService.saveAll(watchlist);

            index = endIndex;

        }

    }

    private void checkMissingWatchlistStocks(ResponseEntity<KiwoomWatchlistResponse> response, List<String> batch) {
        List<KiwoomWatchlistResponse.WatchlistItem> items =
                response.getBody().getWatchlist();

        Set<String> receivedCodes = items.stream()
                .map(KiwoomWatchlistResponse.WatchlistItem::stockCode)
                .collect(Collectors.toSet());

        List<String> missingCodes = batch.stream()
                .filter(code -> !receivedCodes.contains(code))
                .toList();

        if (!missingCodes.isEmpty()) {
            log.warn("관심 종목 시세 수집 누락: {}", missingCodes);
        }
    }


}
