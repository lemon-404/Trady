package trady.com.backend.global.kiwoom.stock.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import trady.com.backend.global.kiwoom.auth.KiwoomTokenProvider;
import trady.com.backend.global.kiwoom.error.KiwoomApiException;
import trady.com.backend.global.kiwoom.error.KiwoomErrorHandler;
import trady.com.backend.global.kiwoom.stock.dto.KiwoomStockInfoResponse;
import trady.com.backend.global.kiwoom.stock.dto.KiwoomStockListResponse;

import java.time.Duration;
import java.util.Map;

@Component
public class KiwoomStockClient {

    private final WebClient webClient;
    private final KiwoomTokenProvider provider;

    private final KiwoomErrorHandler errorHandler;

    public KiwoomStockClient(@Qualifier("kiwoomWebClient") WebClient webClient, KiwoomTokenProvider provider, KiwoomErrorHandler errorHandler) {
        this.webClient = webClient;
        this.provider = provider;
        this.errorHandler = errorHandler;
    }

    /**
     * 종목정보(ka10100)를 조회한다. 토큰은 Provider에서 가져온다.
     * HTTP 200에도 업무 오류가 있으므로 호출 측은 returnCode를 확인해야 한다.
     */
    public KiwoomStockInfoResponse getStockInfo(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("종목 코드가 필요합니다.");
        }
        validateAccessToken();
        String accessToken = provider.getToken();

        KiwoomStockInfoResponse response = webClient.post()
                .uri("/api/dostk/stkinfo")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .header("api-id", "ka10100")
                .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                .bodyValue(Map.of("stk_cd", stockCode))
                .retrieve()
                .bodyToMono(KiwoomStockInfoResponse.class)
                .block(Duration.ofSeconds(2));

        if (response == null) {
            throw new IllegalStateException("키움 종목 조회 응답이 비어 있습니다.");
        }
        return response;
    }

    public ResponseEntity<KiwoomStockListResponse> getStockList(String nextKey) {
        validateAccessToken();

        ResponseEntity<KiwoomStockListResponse> response;
        try {
            response = webClient.post()
                    .uri("/api/dostk/stkinfo")
                    .headers(headers -> headers.setBearerAuth(provider.getToken()))
                    .header("api-id", "ka10099")
                    .header("cont-yn", nextKey.equals("N") ? "N" : "Y")
                    .header("next-key", nextKey.equals("N") ? "" : nextKey)
                    .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                    .bodyValue(Map.of("mrkt_tp", "0"))
                    .retrieve()
                    .toEntity(KiwoomStockListResponse.class)
                    .block(Duration.ofSeconds(2));
        } catch (WebClientResponseException e) {
            throw errorHandler.convert(e);
        }

        if (response == null || response.getBody() == null) {
            throw new KiwoomApiException(
                    null, null, "키움 종목 목록 응답이 비어 있습니다.", null
            );
        }
        KiwoomStockListResponse body = response.getBody();
        errorHandler.validate(body.returnCode(), body.returnMsg());

        return response;
    }


    private void validateAccessToken() {
        if (provider.getToken() == null || provider.getToken().isBlank()) {
            throw new IllegalArgumentException("접근 토큰이 필요합니다.");
        }
    }
}
