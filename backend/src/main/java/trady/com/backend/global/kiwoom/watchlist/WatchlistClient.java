package trady.com.backend.global.kiwoom.watchlist;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import trady.com.backend.global.kiwoom.auth.KiwoomTokenProvider;
import trady.com.backend.global.kiwoom.error.HandleKiwoomError;
import trady.com.backend.global.kiwoom.error.KiwoomApiException;
import trady.com.backend.global.kiwoom.error.KiwoomErrorHandler;
import trady.com.backend.global.kiwoom.watchlist.dto.KiwoomWatchlistRequest;
import trady.com.backend.global.kiwoom.watchlist.dto.KiwoomWatchlistResponse;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class WatchlistClient {

    private final WebClient webClient;
    private final KiwoomTokenProvider provider;

    private final KiwoomErrorHandler errorHandler;

    public WatchlistClient(@Qualifier("kiwoomWebClient") WebClient webClient, KiwoomTokenProvider provider, KiwoomErrorHandler errorHandler) {
        this.webClient = webClient;
        this.provider = provider;
        this.errorHandler = errorHandler;
    }

    @HandleKiwoomError
    public ResponseEntity<KiwoomWatchlistResponse> getWatchlist(
            KiwoomWatchlistRequest request,
            String nextKey
    ) {

        ResponseEntity<KiwoomWatchlistResponse> response;

        response = webClient.post()
                .uri("/api/dostk/stkinfo")
                .headers(httpHeaders ->
                        httpHeaders.setBearerAuth(provider.getToken()))
                .header("api-id", "ka10095")
                .header("cont-yn", nextKey.equals("N") ? "N" : "Y")
                .header("next-key", nextKey.equals("N") ? "" : nextKey)
                .contentType(MediaType.parseMediaType("application/json;charset=UTF-8"))
                .bodyValue(request)
                .retrieve()
                .toEntity(KiwoomWatchlistResponse.class)
                .timeout(Duration.ofSeconds(1))
                .onErrorMap(WebClientResponseException.class, errorHandler::convert)
                .onErrorMap(
                        TimeoutException.class,
                        e -> new KiwoomApiException(
                                null, null, "키움 API 응답 시간이 초과되었습니다.", e
                        )
                )
                .onErrorMap(
                        WebClientRequestException.class,
                        e -> new KiwoomApiException(
                                null, null, "키움 API 연결에 실패했습니다.", e
                        )
                )
                .block();

        if (response == null || response.getBody() == null) {
            throw new KiwoomApiException(
                    null, null, "키움 종목 목록 응답이 비어 있습니다.", null
            );
        }
        KiwoomWatchlistResponse body = response.getBody();
        errorHandler.validate(body.getReturnCode(), body.getReturnMsg());

        return response;

    }
}
