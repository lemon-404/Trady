package trady.com.backend.global.kiwoom.candle.historical.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class CandleRequest {

    @JsonProperty("stk_cd")
    String stockCode;

    @JsonProperty("upd_stkpc_tp")
    String type;

    @JsonProperty("tic_scope")
    String interval;

    @JsonProperty("base_dt")
    String baseDt;

    public CandleRequest(String stockCode, String type,String baseDt, String interval) {
        this.stockCode = stockCode;
        this.type = type;
        this.baseDt = baseDt;
        this.interval = interval;
    }

    public CandleRequest(@JsonProperty("stk_cd") String stockCode,
                         @JsonProperty("base_dt") String baseDt,
                         @JsonProperty("tic_scope") String interval) {
        this(stockCode, "0", baseDt, interval);
    }

    @Override
    public String toString() {
        return "CandleRequest{" +
                "stockCode='" + stockCode + '\'' +
                ", type='" + type + '\'' +
                ", baseDt='" + baseDt + '\'' +
                '}';
    }
}
