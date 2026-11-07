package trady.com.backend.watchlist.dto;

import java.math.BigDecimal;
import java.util.List;

public record WatchlistResponse (
        List<WatchlistItemResponse> stocks
        )
{
    public record WatchlistItemResponse(
            String stockName,
            Long basePrice,
            BigDecimal rate){ }
}
