package trady.com.backend.global.kiwoom.stock.client;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import trady.com.backend.global.kiwoom.stock.dto.KiwoomStockListResponse;

@Slf4j
@SpringBootTest
class KiwoomStockClientTest {

    @Autowired
    private KiwoomStockClient client;

    @Test
    void 종목_리스트_확인() throws InterruptedException {
        ResponseEntity<KiwoomStockListResponse> stockList =
                client.getStockList(null);

        while (true) {
            log.info(stockList.getHeaders().getFirst("cont-yn"));
            log.info(stockList.getHeaders().getFirst("next-key"));
            log.info(String.valueOf(

                    stockList.getBody().stockInfoList().stream()
                            .filter(stock -> stock.auditInfo().equals("정상"))
                            .filter(stock -> stock.marketCode().equals("0"))
                            .filter(stock -> stock.marketName().equals("거래소"))
                            .count()
            ));

            log.info(String.valueOf(
                    stockList.getBody().stockInfoList().stream()
                            .filter(stock -> stock.auditInfo().equals("정상"))
                            .filter(stock -> stock.marketCode().equals("0"))
                            .filter(stock -> stock.marketName().equals("거래소"))
                            .toList()
            ));
            String contYn = stockList.getHeaders().getFirst("next-key");
            if (!contYn.equals("Y")) break;

            Thread.sleep(1000);

            stockList = client.getStockList(contYn);

        }
    }

}
