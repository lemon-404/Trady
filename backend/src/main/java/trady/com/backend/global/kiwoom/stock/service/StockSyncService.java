package trady.com.backend.global.kiwoom.stock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import trady.com.backend.global.kiwoom.stock.client.KiwoomStockClient;
import trady.com.backend.global.kiwoom.stock.dto.KiwoomStockListResponse;
import trady.com.backend.global.resource.StockTarget;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.stock.service.StockService;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * mark = 코스피
 * auditInfo = 정상
 * marketCode = 0
 * marketName = 거래소
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockSyncService {
    private final KiwoomStockClient stockClient;
    private final StockService stockService;

    public void syncStockInfoList() {
        List<Stock> stocks = new ArrayList<>();
        String nextKey = "N";

        Set<String> targetCodes = getTargetCodes();

        while (true) {
            ResponseEntity<KiwoomStockListResponse> response = stockClient.getStockList(nextKey);

            stocks.addAll(response.getBody().getStockInfoList().stream()
                    .filter(stock -> {
                        if (!hasRequiredData(stock)) {
                            log.error("종목 필수 데이터 누락: {}", stock);
                            return false;
                        }
                        return true;
                    })
                    .filter(stock -> stock.auditInfo().equals("정상"))
                    .filter(stock -> stock.marketCode().equals("0"))
                    .filter(stock -> stock.marketName().equals("거래소"))
                    .map(KiwoomStockListResponse.StockInfo::toResponseDto)
                    .toList());
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

        List<Stock> filteredStocks = stocks.stream()
                .filter(stock -> targetCodes.contains(stock.getCode()))
                .toList();

        Set<String> filteredStockCodes = filteredStocks.stream()
                .map(Stock::getCode)
                .collect(Collectors.toSet());

        HashSet<String> missingCodes = new HashSet<>(targetCodes);
        missingCodes.removeAll(filteredStockCodes);
        if (!missingCodes.isEmpty()) {
            log.warn(
                    "종목 수집 일부 누락: 대상={}개, 수집={}개, 누락={}개, 누락코드={}",
                    targetCodes.size(),
                    filteredStockCodes.size(),
                    missingCodes.size(),
                    missingCodes);
        }

        stockService.savePage(filteredStocks);

    }

    private @NonNull Set<String> getTargetCodes() {
        Set<String> targetCodes;
        try {
            targetCodes = readStockTargets().stream()
                    .map(StockTarget::stockCode)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return targetCodes;
    }

    private List<StockTarget> readStockTargets() throws IOException {
        byte[] json = new ClassPathResource("stock-targets.json")
                .getContentAsByteArray();

        StockTarget[] targets =
                new ObjectMapper().readValue(json, StockTarget[].class);

        return Arrays.asList(targets);
    }

    private boolean hasRequiredData(
            KiwoomStockListResponse.StockInfo stock
    ) {
        return stock != null
                && stock.code() != null && !stock.code().isBlank()
                && stock.name() != null && !stock.name().isBlank()
                && stock.marketCode() != null && !stock.marketCode().isBlank();
    }

}
