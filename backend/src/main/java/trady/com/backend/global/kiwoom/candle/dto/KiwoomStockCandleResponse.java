package trady.com.backend.global.kiwoom.candle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import trady.com.backend.candle.entity.Candle;
import trady.com.backend.stock.entity.Stock;

import trady.com.backend.global.util.KiwoomDateTimeFormats;

import java.time.LocalDateTime;

import java.util.List;

public record KiwoomStockCandleResponse(
        @JsonProperty("stk_cd") String stockCode,
        @JsonProperty("stk_min_pole_chart_qry") List<CandleItem> list

) {
    public record CandleItem(
            @JsonProperty("open_pric") int open,
            @JsonProperty("high_pric") int high,
            @JsonProperty("low_pric") int low,
            @JsonProperty("cur_prc") int close,
            @JsonProperty("trde_qty") long quantity,
            @JsonProperty("cntr_tm") String time  //20261002195000

    ) {
    }

    public static Candle toResponseDto(CandleItem dto, Stock stock, String interval) {
        return new Candle(
                stock,
                interval,
                LocalDateTime.parse(dto.time, KiwoomDateTimeFormats.DATE_TIME),
                Math.abs(dto.open),
                Math.abs(dto.high),
                Math.abs(dto.low),
                Math.abs(dto.close),
                dto.quantity
        );
    }
}
