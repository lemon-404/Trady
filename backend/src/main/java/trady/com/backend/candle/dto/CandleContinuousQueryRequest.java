package trady.com.backend.candle.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record CandleContinuousQueryRequest(
     @NotBlank(message = "종목 코드를 입력해 주세요.") String stockCode,
     String interval,
     LocalDateTime cursor

) {

}

