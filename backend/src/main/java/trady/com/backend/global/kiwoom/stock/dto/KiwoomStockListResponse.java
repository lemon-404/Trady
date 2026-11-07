package trady.com.backend.global.kiwoom.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import trady.com.backend.global.kiwoom.dto.KiwoomResponse;
import trady.com.backend.stock.Enum.StockMarket;
import trady.com.backend.stock.entity.Stock;

import java.util.List;


@Getter
@NoArgsConstructor
public class KiwoomStockListResponse extends KiwoomResponse {

    @JsonProperty("list")
    private List<StockInfo> stockInfoList;

    public KiwoomStockListResponse(
            List<StockInfo> stockInfoList,
            String returnCode,
            String returnMsg
    ) {
        super(returnCode, returnMsg);
        this.stockInfoList = stockInfoList;
    }

    public record StockInfo(
            String code,
            String name,
            String auditInfo,
            String marketCode,
            String marketName
    ) {
        public static Stock toResponseDto(StockInfo dto) {
            return new Stock(
                    dto.code(),
                    dto.name(),
                    StockMarket.fromCode(dto.marketCode()).getName()
            );
        }
    }
}
