package trady.com.backend.watchlist.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import trady.com.backend.watchlist.dto.WatchlistResponse;
import trady.com.backend.watchlist.entity.Watchlist;
import trady.com.backend.watchlist.repostiory.WatchlistRepository;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class WatchlistQueryService {

    private final WatchlistRepository repository;

    public void saveAll(Set<Watchlist> watchlist) {
        repository.saveAll(watchlist);
    }


    public WatchlistResponse getWatchlist() {

        return new WatchlistResponse(repository.findAllWatchlistItems());
    }
}
