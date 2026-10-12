package trady.com.backend.candle.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record CandleContinuousQueryResponse(
        LocalDateTime nextCursor,
        @JsonProperty("cont-yn") String contYn,
        List<CandleItem> candles
        ) {
    public record CandleItem(
            @JsonFormat(pattern = "yyyyMMddHHmmss") LocalDateTime time,
            @JsonProperty("open_price") int open,
            @JsonProperty("high_price") int high,
            @JsonProperty("low_price") int low,
            @JsonProperty("close_price") int close,
            @JsonProperty("volume") long volume
    ){}
}

