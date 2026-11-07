package trady.com.backend.watchlist.repostiory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import trady.com.backend.watchlist.dto.WatchlistResponse;
import trady.com.backend.watchlist.entity.Watchlist;

import java.util.List;

public interface WatchlistRepository extends JpaRepository<Watchlist, String> {

    @Query("""
    select new trady.com.backend.watchlist.dto.WatchlistResponse$WatchlistItemResponse(
        s.name, w.basePrice, w.rate
    )
    from Watchlist w
    join w.stock s
    """)
    List<WatchlistResponse.WatchlistItemResponse> findAllWatchlistItems();
}
