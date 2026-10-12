package trady.com.backend.global.kiwoom.stock.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import trady.com.backend.entity.stock.Stock;
import trady.com.backend.global.kiwoom.stock.client.KiwoomStockClient;
import trady.com.backend.global.kiwoom.stock.dto.KiwoomStockListResponse;
import trady.com.backend.stock.service.StockService;

import java.util.List;

/**
 * mark = 코스피
 * auditInfo = 정상
 * marketCode = 0
 * marketName = 거래소
 *
 */

@Service
@RequiredArgsConstructor
public class StockSyncService {
    private final KiwoomStockClient stockClient;
    private final StockService stockService;

    public void syncStockInfoList() {
        String nextKey = "N";
        while (true) {
            ResponseEntity<KiwoomStockListResponse> response =
                    stockClient.getStockList(nextKey);
            List<Stock> stocks = response.getBody().stockInfoList().stream()
                    .filter(stock -> stock.auditInfo().equals("정상"))
                    .filter(stock -> stock.marketCode().equals("0"))
                    .filter(stock -> stock.marketName().equals("거래소"))
                    .map(KiwoomStockListResponse.StockInfo::toResponseDto)
                    .toList();
            stockService.savePage(stocks);

            String contYn = response.getHeaders().getFirst("cont-yn");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            if (!"Y".equals(contYn)) {
                break;
            }

            nextKey = response.getHeaders().getFirst("next-key");
        }

    }
}
