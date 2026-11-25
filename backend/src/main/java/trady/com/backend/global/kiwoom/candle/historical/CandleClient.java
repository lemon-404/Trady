package trady.com.backend.global.kiwoom.candle.historical;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import trady.com.backend.global.kiwoom.auth.KiwoomTokenProvider;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleRequest;
import trady.com.backend.global.kiwoom.error.KiwoomApiException;
import trady.com.backend.global.kiwoom.error.KiwoomErrorHandler;
import trady.com.backend.global.response.KiwoomBaseResponse;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class CandleClient {


    private WebClient webClient;
    private final KiwoomTokenProvider provider;
    private final KiwoomErrorHandler errorHandler;

    public CandleClient(@Qualifier("kiwoomWebClient") WebClient webClient,
                        KiwoomTokenProvider provider, KiwoomErrorHandler errorHandler) {
        this.webClient = webClient;
        this.provider = provider;
        this.errorHandler = errorHandler;
    }

    public <T extends KiwoomBaseResponse> ResponseEntity<T> request(
            CandleRequest request,
            String nextKey,
            String tr,
            Class<T> responseType
    ) {
        boolean continued = !"N".equals(nextKey);

        ResponseEntity<T> response = webClient.post()
                .uri("/api/dostk/chart")
                .headers(headers ->
                        headers.setBearerAuth(provider.getToken()))
                .header("api-id", tr)
                .header("cont-yn", continued ? "Y" : "N")
                .header("next-key", continued ? nextKey : "")
                .contentType(
                        MediaType.parseMediaType("application/json;charset=UTF-8"))
                .bodyValue(request)
                .retrieve()
                .toEntity(responseType)
                .timeout(Duration.ofSeconds(1))
                .onErrorMap(WebClientResponseException.class, errorHandler::convert)
                .onErrorMap(TimeoutException.class,
                        e -> new KiwoomApiException(
                                null, null, "키움 API 응답 시간이 초과되었습니다.", e))
                .onErrorMap(WebClientRequestException.class,
                        e -> new KiwoomApiException(
                                null, null, "키움 API 연결에 실패했습니다.", e))
                .block();

        if (response == null || response.getBody() == null) {
            throw new KiwoomApiException(
                    null, null, "키움 캔들 응답이 비어 있습니다.", null);
        }
        T body = response.getBody();
        Integer code = body.getReturnCode();
        errorHandler.validate(code == null ? null : code.toString(), body.getReturnMsg());
        return response;
    }


}
