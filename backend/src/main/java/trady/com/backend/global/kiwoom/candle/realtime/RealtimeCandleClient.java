package trady.com.backend.global.kiwoom.candle.realtime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import trady.com.backend.global.kiwoom.auth.KiwoomTokenProvider;
import trady.com.backend.global.kiwoom.candle.historical.dto.*;
import trady.com.backend.global.kiwoom.error.*;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class RealtimeCandleClient {
    private final WebClient webClient;
    private final KiwoomTokenProvider tokens;
    private final KiwoomErrorHandler errors;

    public RealtimeCandleClient(@Qualifier("kiwoomWebClient") WebClient webClient,
                               KiwoomTokenProvider tokens, KiwoomErrorHandler errors) {
        this.webClient = webClient;
        this.tokens = tokens;
        this.errors = errors;
    }

    public Mono<ResponseEntity<CandleResponse>> request(CandleRequest request, String tr, String nextKey) {
        return Mono.defer(() -> webClient.post().uri("/api/dostk/chart")
                        .headers(headers -> headers.setBearerAuth(tokens.getToken()))
                        .header("api-id", tr).header("cont-yn", "N".equals(nextKey) ? "N" : "Y")
                        .header("next-key", "N".equals(nextKey) ? "" : nextKey)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(request)
                        .retrieve().toEntity(CandleResponse.class))
                .timeout(Duration.ofSeconds(1))
                .doOnNext(response -> {
                    var body = response.getBody();
                    if (body == null) throw new KiwoomApiException(null, null, "키움 캔들 응답 누락", null);
                    errors.validate(body.getReturnCode() == null ? null : body.getReturnCode().toString(), body.getReturnMsg());
                    if (body.getList() == null || !request.getStockCode().equals(body.getStockCode())) {
                        throw new KiwoomApiException(null, null, "캔들 목록 누락 또는 종목 불일치", null);
                    }
                })
                .onErrorMap(WebClientResponseException.class, errors::convert)
                .onErrorMap(DecodingException.class, e -> new KiwoomApiException(null, null, "키움 응답 형식 오류", e))
                .onErrorMap(TimeoutException.class, e -> new KiwoomApiException(null, null, "키움 응답 시간 초과", e))
                .onErrorMap(WebClientRequestException.class, e -> new KiwoomApiException(null, null, "키움 연결 실패", e));
    }
}
