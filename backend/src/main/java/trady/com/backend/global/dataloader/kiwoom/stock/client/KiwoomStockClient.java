package trady.com.backend.global.dataloader.kiwoom.stock.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import trady.com.backend.global.dataloader.kiwoom.stock.dto.KiwoomStockResponse;

import java.time.Duration;
import java.util.Map;

@Component
public class KiwoomStockClient {

    private final WebClient webClient;

    public KiwoomStockClient(@Qualifier("kiwoomWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 종목정보(ka10100)를 조회한다. 토큰은 호출 측에서 준비한다.
     * HTTP 200에도 업무 오류가 있으므로 호출 측은 returnCode를 확인해야 한다.
     */
    public KiwoomStockResponse getStockInfo(String stockCode, String accessToken) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("종목 코드가 필요합니다.");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("접근 토큰이 필요합니다.");
        }

        KiwoomStockResponse response = webClient.post()
                .uri("/api/dostk/stkinfo")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .header("api-id", "ka10100")
                .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                .bodyValue(Map.of("stk_cd", stockCode))
                .retrieve()
                .bodyToMono(KiwoomStockResponse.class)
                .block(Duration.ofSeconds(10));

        if (response == null) {
            throw new IllegalStateException("키움 종목 조회 응답이 비어 있습니다.");
        }
        return response;
    }
}
