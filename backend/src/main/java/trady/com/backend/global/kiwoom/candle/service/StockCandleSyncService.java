package trady.com.backend.global.kiwoom.candle.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import trady.com.backend.candle.service.CandleService;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.global.kiwoom.candle.KiwoomStockCandleClient;
import trady.com.backend.global.kiwoom.candle.dto.KiwoomStockCandleReqeust;
import trady.com.backend.global.kiwoom.candle.dto.KiwoomStockCandleResponse;
import trady.com.backend.global.resource.StockTarget;
import trady.com.backend.stock.repository.StockQueryRepository;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockCandleSyncService {
    private final StockQueryRepository stockQueryRepository;

    private final KiwoomStockCandleClient candleClient;
    private final CandleService candleSaveService;

    private String[] intervals = {"1", "5", "15"};
    private String baseDt = "20261009";

    public void syncStockCandleList() {
        List<String> codes = null;
        try {
            codes = readStockTargets().stream()
                    .map(StockTarget::stockCode)
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<Stock> stocks = stockQueryRepository.findAllByCodeIn(codes);
        syncStockCandleList(stocks, intervals, Integer.MAX_VALUE);
    }

    // 대상·봉 주기·최대 페이지를 지정해 필요한 범위만 수집한다.
    void syncStockCandleList(List<Stock> stocks, String[] intervals, int maxPages) {

        //1. 종목 50개
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            //2. 틱 1, 5, 15분 봉
            for (int j = 0; j < intervals.length; j++) {
                String nextKey = "N";
                // 3. 종목별 틱별 1년치 CANDLE DATA 저장
                for (int page = 0; page < maxPages; page++) {
                    String stockCode = stock.getCode();
                    String interval = intervals[j];
                    ResponseEntity<KiwoomStockCandleResponse> response = candleClient.getKiwoomStockCandle(
                            new KiwoomStockCandleReqeust(
                                    stockCode,
                                    interval,
                                    baseDt
                            ), nextKey);
                    // 외부 API 응답을 받은 뒤 한 페이지의 DB 작업만 트랜잭션으로 처리한다.
                    candleSaveService.savePage(stock, interval, response.getBody().list());
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
    }


    private List<StockTarget> readStockTargets() throws IOException {
        byte[] json = new ClassPathResource("stock-targets.json")
                .getContentAsByteArray();

        StockTarget[] targets =
                new ObjectMapper().readValue(json, StockTarget[].class);

        return Arrays.asList(targets);
    }
}
