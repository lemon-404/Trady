package trady.com.backend.global.kiwoom.stock.dto;

public record KiwoomStockRequest (
        String method,
        String authorization,
        String apiId,
        String contentType,
        String body) {

}
