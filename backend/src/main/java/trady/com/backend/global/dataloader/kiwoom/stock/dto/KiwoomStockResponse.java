package trady.com.backend.global.dataloader.kiwoom.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KiwoomStockResponse(
        @JsonProperty("code") String stockCode,
        @JsonProperty("name") String stockName,
        String listCount,
        String auditInfo,
        String regDay,
        String lastPrice,
        String state,
        String marketCode,
        String marketName,
        String upName,
        String upSizeName,
        String companyClassName,
        String orderWarning,
        String nxtEnable,
        @JsonProperty("return_code") Integer returnCode,
        @JsonProperty("return_msg") String returnMsg
) {
}
