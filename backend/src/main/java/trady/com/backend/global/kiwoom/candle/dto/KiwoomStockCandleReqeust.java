package trady.com.backend.global.kiwoom.candle.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomStockCandleReqeust(
        @JsonProperty("stk_cd") String stockCode,
        @JsonProperty("tic_scope") String interval,
        @JsonProperty("upd_stkpc_tp") String type,
        @JsonProperty("base_dt") String baseDt
        ) {
    public KiwoomStockCandleReqeust(@JsonProperty("stk_cd") String stockCode, @JsonProperty("tic_scope") String interval, @JsonProperty("base_dt") String baseDt) {
        this(stockCode, interval, "0", baseDt);
    }
}

