package trady.com.backend.global.kiwoom.candle.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import trady.com.backend.candle.entity.Candle;
import trady.com.backend.global.response.KiwoomBaseResponse;
import trady.com.backend.global.util.KiwoomDateTimeFormats;
import trady.com.backend.stock.entity.Stock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@ToString
public class CandleResponse extends KiwoomBaseResponse {
        @JsonProperty("stk_cd")
        private String stockCode;
        @JsonProperty("stk_min_pole_chart_qry") // 분봉
        @JsonAlias({
                "stk_dt_pole_chart_qry",  // 일봉
                "stk_stk_pole_chart_qry", // 주봉
                "stk_mth_pole_chart_qry", // 월봉
                "stk_yr_pole_chart_qry"   // 연봉
        })
        private List<CandleItem> list;

    public record CandleItem(
            @JsonProperty("open_pric") int open,
            @JsonProperty("high_pric") int high,
            @JsonProperty("low_pric") int low,
            @JsonProperty("cur_prc") int close,
            @JsonProperty("trde_qty") long quantity,
            @JsonProperty("cntr_tm") // 분봉: YYYYMMDDHHmmss
            @JsonAlias("dt") //일/주/월/년 봉: YYYYMMDD
            String time

    ) {
    }

    public static Candle toResponseDto(CandleItem dto, Stock stock, String interval) {

        if (dto.time() == null || dto.time().isBlank()) {
            throw new IllegalArgumentException(
                    "캔들 날짜가 없습니다. stock=" + stock.getCode()
                            + ", interval=" + interval
            );
        }


        String rawTime = dto.time();

        if (rawTime == null || rawTime.isBlank()) {
            throw new IllegalArgumentException("캔들 날짜가 없습니다.");
        }

        LocalDateTime time;

        if (rawTime.length() == 14) {
            // 분봉: yyyyMMddHHmmss
            time = LocalDateTime.parse(
                    rawTime,
                    KiwoomDateTimeFormats.DATE_TIME
            );
        } else if (rawTime.length() == 8) {
            // 일·주·월·연봉: yyyyMMdd
            time = LocalDate.parse(
                    rawTime,
                    DateTimeFormatter.BASIC_ISO_DATE
            ).atStartOfDay();
        } else {
            throw new IllegalArgumentException(
                    "지원하지 않는 캔들 날짜 형식: " + rawTime
            );
        }

        return new Candle(
                stock,
                interval,
                time,
                Math.abs(dto.open),
                Math.abs(dto.high),
                Math.abs(dto.low),
                Math.abs(dto.close),
                dto.quantity
        );
    }
}
