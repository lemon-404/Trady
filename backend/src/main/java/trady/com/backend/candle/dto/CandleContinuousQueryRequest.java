package trady.com.backend.candle.dto;

import java.time.LocalDateTime;

public record CandleContinuousQueryRequest(
     String stockCode,
     String interval,
     LocalDateTime cursor

) {

}

