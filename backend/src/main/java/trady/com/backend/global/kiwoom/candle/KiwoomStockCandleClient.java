package trady.com.backend.global.kiwoom.candle;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import trady.com.backend.global.kiwoom.auth.KiwoomTokenProvider;
import trady.com.backend.global.kiwoom.candle.dto.KiwoomStockCandleReqeust;
import trady.com.backend.global.kiwoom.candle.dto.KiwoomStockCandleResponse;

import java.time.Duration;

@Component
public class KiwoomStockCandleClient {


    private WebClient webClient;
    private final KiwoomTokenProvider provider;

    public KiwoomStockCandleClient(@Qualifier("kiwoomWebClient") WebClient webClient, KiwoomTokenProvider provider) {
        this.webClient = webClient;
        this.provider = provider;
    }

    public ResponseEntity<KiwoomStockCandleResponse> getKiwoomStockCandle(
            KiwoomStockCandleReqeust reqeust,
            String nextKey) {

        ResponseEntity<KiwoomStockCandleResponse> response = webClient.post()
                .uri("/api/dostk/chart")
                .headers(httpHeaders ->
                        httpHeaders.setBearerAuth(provider.getToken()))
                .header("api-id", "ka10080")
                .header("cont-yn", nextKey.equals("N") ? "N" : "Y")
                .header("next-key", nextKey.equals("N") ? "" : nextKey)
                .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                .bodyValue(reqeust)
                .retrieve()
                .toEntity(KiwoomStockCandleResponse.class)
                .block(Duration.ofSeconds(2));
        return response;

    }
}
