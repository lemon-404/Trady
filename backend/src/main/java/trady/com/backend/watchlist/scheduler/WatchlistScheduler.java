package trady.com.backend.watchlist.scheduler;


import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import trady.com.backend.global.kiwoom.watchlist.WatchlistService;
import trady.com.backend.global.market.MarketCalendar;

@Component
@RequiredArgsConstructor
public class WatchlistScheduler {

    private final WatchlistService watchlistService;
    private final MarketCalendar marketCalendar;

    @Scheduled(cron = "0,30 * * * * *", zone = "Asia/Seoul")
    public void refreshWatchlist() {
        if (!marketCalendar.isMarketOpen()) {
            return;
        }

        watchlistService.persistWatchlist();
    }
}
