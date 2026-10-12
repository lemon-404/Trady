package trady.com.backend.global.kiwoom.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.stock.Enum.StockMarket;

import java.util.List;

public record KiwoomStockListResponse(
        @JsonProperty("list") List<StockInfo> stockInfoList
) {
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
