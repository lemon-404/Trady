package trady.com.backend.global.kiwoom.candle;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import trady.com.backend.global.kiwoom.auth.KiwoomTokenProvider;
import trady.com.backend.global.kiwoom.candle.historical.CandleClient;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleRequest;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleResponse;
import trady.com.backend.global.kiwoom.error.KiwoomApiException;
import trady.com.backend.global.kiwoom.error.KiwoomErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CandleClientErrorTest {
    @Test
    void rejectsBusinessFailureEvenWithHttp200() {
        var client = client(HttpStatus.OK, MediaType.APPLICATION_JSON,
                "{\"return_code\":2,\"return_msg\":\"[1700: 요청 제한]\"}");

        var error = catchThrowableOfType(KiwoomApiException.class, () -> request(client));

        assertThat(error.getReturnCode()).isEqualTo("2");
        assertThat(error.getErrorCode()).isEqualTo("1700");
    }

    @Test
    void preservesHttpFailureWhenBodyIsHtml() {
        var client = client(HttpStatus.BAD_GATEWAY, MediaType.TEXT_HTML, "<html>error</html>");

        var error = catchThrowableOfType(KiwoomApiException.class, () -> request(client));

        assertThat(error.getCause()).isInstanceOf(WebClientResponseException.class);
        assertThat(((WebClientResponseException) error.getCause()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(error.getErrorCode()).isNull();
    }

    @Test
    void returnsSuccessfulBodyAndContinuationHeaders() {
        var client = client(HttpStatus.OK, MediaType.APPLICATION_JSON,
                "{\"return_code\":0,\"return_msg\":\"정상\",\"stk_cd\":\"005930\",\"stk_min_pole_chart_qry\":[]}");

        var response = request(client);

        assertThat(response.getBody().getReturnCode()).isZero();
        assertThat(response.getBody().getList()).isEmpty();
        assertThat(response.getHeaders().getFirst("cont-yn")).isEqualTo("N");
    }

    private CandleClient client(HttpStatus status, MediaType contentType, String body) {
        var provider = mock(KiwoomTokenProvider.class);
        when(provider.getToken()).thenReturn("test-token");
        var webClient = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(status)
                        .header("Content-Type", contentType.toString())
                        .header("cont-yn", "N")
                        .body(body).build())).build();
        return new CandleClient(webClient, provider, new KiwoomErrorHandler(new ObjectMapper()));
    }

    private org.springframework.http.ResponseEntity<CandleResponse> request(CandleClient client) {
        return client.request(new CandleRequest("005930", "20261112", "1"),
                "N", "ka10080", CandleResponse.class);
    }
}
