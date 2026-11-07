package trady.com.backend.watchlist.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import trady.com.backend.global.response.TradyResponse;
import trady.com.backend.watchlist.dto.WatchlistResponse;
import trady.com.backend.watchlist.service.WatchlistQueryService;

@RestController
@RequestMapping("/trady/v1/stocks")
@RequiredArgsConstructor
public class WatchlistController {
    private final WatchlistQueryService watchlistQueryService;

    @GetMapping
    public ResponseEntity<TradyResponse<WatchlistResponse>> getWatchlist() {

        WatchlistResponse result = watchlistQueryService.getWatchlist();

        TradyResponse<WatchlistResponse> response = TradyResponse.success(
                "관심 종목 목록 조회 성공",
                result
        );
        return ResponseEntity.ok(response);


    }
}
