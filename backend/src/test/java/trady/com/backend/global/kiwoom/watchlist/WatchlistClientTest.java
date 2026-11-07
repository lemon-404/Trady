package trady.com.backend.global.kiwoom.watchlist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import trady.com.backend.global.kiwoom.auth.KiwoomTokenProvider;
import trady.com.backend.global.kiwoom.error.KiwoomApiException;
import trady.com.backend.global.kiwoom.error.KiwoomErrorHandler;
import trady.com.backend.global.kiwoom.watchlist.dto.KiwoomWatchlistRequest;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchlistClientTest {

    private static final String AUTH_ERROR = """
            {
                "return_code": 3,
                "return_msg": "인증에 실패했습니다[8005:Token이 유효하지 않습니다]"
            }
            """;

    @Mock private ExchangeFunction exchangeFunction;
    @Mock private KiwoomTokenProvider provider;
    private WatchlistClient client;

    @BeforeEach
    void setUp() {
        // HTTP 전송만 모킹하고 응답 변환·오류 검증은 실제 코드를 실행한다.
        WebClient webClient = WebClient.builder()
                .baseUrl("https://kiwoom.test")
                .exchangeFunction(exchangeFunction)
                .build();
        client = new WatchlistClient(webClient, provider,
                new KiwoomErrorHandler(new ObjectMapper()));
        when(provider.getToken()).thenReturn("test-token");
    }

    @Test
    void 정상_응답을_관심_종목_시세로_변환한다() {
        respond(HttpStatus.OK, """
                {
                    "return_code": 0,
                    "return_msg": "정상적으로 처리되었습니다",
                    "atn_stk_infr": [
                        {"stk_cd": "005930", "base_pric": 75000, "flu_rt": 1.35},
                        {"stk_cd": "000660", "base_pric": 182000, "flu_rt": -0.82}
                    ]
                }
                """);

        var response = client.getWatchlist(request(), "N");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        var body = response.getBody();
        assertThat(body.getReturnCode()).isEqualTo("0");
        assertThat(body.getReturnMsg()).isEqualTo("정상적으로 처리되었습니다");
        assertThat(body.getWatchlist()).hasSize(2);
        var samsung = body.getWatchlist().get(0);
        assertThat(samsung.stockCode()).isEqualTo("005930");
        assertThat(samsung.basePrice()).isEqualTo(75000L);
        assertThat(samsung.rate()).isEqualByComparingTo("1.35");
        var sk = body.getWatchlist().get(1);
        assertThat(sk.stockCode()).isEqualTo("000660");
        assertThat(sk.basePrice()).isEqualTo(182000L);
        assertThat(sk.rate()).isEqualByComparingTo("-0.82");

        var captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction).exchange(captor.capture());
        ClientRequest sent = captor.getValue();
        assertThat(sent.method()).isEqualTo(HttpMethod.POST);
        assertThat(sent.url().getPath()).isEqualTo("/api/dostk/stkinfo");
        assertThat(sent.headers().getFirst("Authorization")).isEqualTo("Bearer test-token");
        assertThat(sent.headers().getFirst("api-id")).isEqualTo("ka10095");
        assertThat(sent.headers().getFirst("cont-yn")).isEqualTo("N");
        assertThat(sent.headers().getFirst("next-key")).isEmpty();
        assertThat(sent.headers().getContentType()).isEqualTo(
                MediaType.parseMediaType("application/json;charset=UTF-8"));
    }

    @Test
    void HTTP_401의_인증_실패를_키움_예외로_변환한다() {
        respond(HttpStatus.UNAUTHORIZED, AUTH_ERROR);

        assertThatThrownBy(() -> client.getWatchlist(request(), "N"))
                .isInstanceOfSatisfying(KiwoomApiException.class, exception -> {
                    assertThat(exception.getReturnCode()).isEqualTo("3");
                    assertThat(exception.getErrorCode()).isEqualTo("8005");
                    assertThat(exception.getMessage()).contains("인증에 실패했습니다");
                    assertThat(exception.getCause()).isInstanceOfSatisfying(
                            WebClientResponseException.class,
                            cause -> assertThat(cause.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
                });
    }

    @Test
    void HTTP_200의_업무_인증_실패도_키움_예외로_변환한다() {
        respond(HttpStatus.OK, AUTH_ERROR);

        assertThatThrownBy(() -> client.getWatchlist(request(), "N"))
                .isInstanceOfSatisfying(KiwoomApiException.class, exception -> {
                    assertThat(exception.getReturnCode()).isEqualTo("3");
                    assertThat(exception.getErrorCode()).isEqualTo("8005");
                    assertThat(exception.getMessage()).contains("인증에 실패했습니다");
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    void 응답_본문이_비어있으면_키움_예외를_발생시킨다() {
        respond(HttpStatus.OK, "");

        assertThatThrownBy(() -> client.getWatchlist(request(), "N"))
                .isInstanceOfSatisfying(KiwoomApiException.class, exception -> {
                    assertThat(exception.getReturnCode()).isNull();
                    assertThat(exception.getErrorCode()).isNull();
                    assertThat(exception.getMessage()).contains("응답이 비어 있습니다");
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    void 응답_대기시간을_초과하면_타임아웃_원인을_보존한_키움_예외로_변환한다() {
        // TimeoutException을 직접 주입하지 않고, 응답이 도착하지 않게 한다.
        // 클라이언트에 설정된 대기시간 제한이 실제로 동작하는지 검증한다.
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(Mono.never());

        assertThatThrownBy(() -> client.getWatchlist(request(), "N"))
                .isInstanceOfSatisfying(KiwoomApiException.class, exception -> {
                    assertThat(exception.getReturnCode()).isNull();
                    assertThat(exception.getErrorCode()).isNull();
                    assertThat(exception.getMessage()).contains("응답 시간이 초과되었습니다");
                    assertThat(exception.getCause()).isInstanceOf(TimeoutException.class);
                });
    }

    private KiwoomWatchlistRequest request() {
        return new KiwoomWatchlistRequest(new String[]{"005930", "000660"});
    }

    private void respond(HttpStatus status, String body) {
        ClientResponse.Builder builder = ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if (!body.isEmpty()) {
            builder.body(body);
        }
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(Mono.just(builder.build()));
    }
}
