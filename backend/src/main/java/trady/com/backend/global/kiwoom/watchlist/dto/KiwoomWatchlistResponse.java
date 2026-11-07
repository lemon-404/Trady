package trady.com.backend.global.kiwoom.watchlist.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import trady.com.backend.global.kiwoom.dto.KiwoomResponse;
import trady.com.backend.watchlist.entity.Watchlist;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class KiwoomWatchlistResponse extends KiwoomResponse{

    @JsonProperty("atn_stk_infr")
    List<WatchlistItem> watchlist;

    public KiwoomWatchlistResponse(String returnCode, String returnMsg, List<WatchlistItem> watchlist) {
        super(returnCode, returnMsg);
        this.watchlist = watchlist;
    }

    public KiwoomWatchlistResponse(List<WatchlistItem> watchlist) {
        this.watchlist = watchlist;
    }

    public record WatchlistItem(
            @JsonProperty("stk_cd") String stockCode,
            @JsonProperty("base_pric") Long basePrice,
            @JsonProperty("flu_rt") BigDecimal rate
    ){
        public static Watchlist toResponseDto(WatchlistItem item, LocalDateTime collectedAt) {
            return new Watchlist(
                    item.stockCode,
                    item.basePrice,
                    item.rate,
                    collectedAt
            );
        }
    }
}
