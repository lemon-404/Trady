package trady.com.backend.global.kiwoom.stock.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KiwoomStockClientTest {
    private static final String AUTH_ERROR = """
            {
                "return_msg": "인증에 실패했습니다[8005:Token이 유효하지 않습니다]",
                "return_code": 3
            }
            """;

    @Mock
    private ExchangeFunction exchangeFunction;
    @Mock
    private KiwoomTokenProvider provider;
    private KiwoomStockClient client;

    @BeforeEach
    void setUp() {
        // HTTP 전송만 모킹하고 클라이언트와 오류 처리기는 실제로 실행한다.
        WebClient webClient = WebClient.builder()
                .baseUrl("https://kiwoom.test")
                .exchangeFunction(exchangeFunction)
                .build();
        client = new KiwoomStockClient(webClient, provider,
                new KiwoomErrorHandler(new ObjectMapper()));
        when(provider.getToken()).thenReturn("test-token");
    }

    @Test
    void 정상_응답을_종목_목록으로_변환한다() {
        respond(HttpStatus.OK, """
                {
                    "return_code": 0,
                    "return_msg": "정상적으로 처리되었습니다",
                    "list": [{
                        "code": "005930", "name": "삼성전자",
                        "auditInfo": "정상", "marketCode": "0", "marketName": "거래소"
                    }]
                }
                """);

        var response = client.getStockList("N");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("cont-yn")).isEqualTo("N");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().returnCode()).isEqualTo("0");
        assertThat(response.getBody().stockInfoList()).singleElement().satisfies(stock -> {
            assertThat(stock.code()).isEqualTo("005930");
            assertThat(stock.name()).isEqualTo("삼성전자");
            assertThat(stock.marketCode()).isEqualTo("0");
        });

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction).exchange(captor.capture());
        ClientRequest request = captor.getValue();
        assertThat(request.method()).isEqualTo(org.springframework.http.HttpMethod.POST);
        assertThat(request.url().getPath()).isEqualTo("/api/dostk/stkinfo");
        assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer test-token");
        assertThat(request.headers().getFirst("api-id")).isEqualTo("ka10099");
        assertThat(request.headers().getFirst("cont-yn")).isEqualTo("N");
        assertThat(request.headers().getFirst("next-key")).isEmpty();
    }

    @Test
    void HTTP_인증_실패를_키움_예외로_변환한다() {
        respond(HttpStatus.UNAUTHORIZED, AUTH_ERROR);

        assertThatThrownBy(() -> client.getStockList("N"))
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
    void HTTP_200의_업무_인증_오류도_키움_예외로_변환한다() {
        respond(HttpStatus.OK, AUTH_ERROR);

        assertThatThrownBy(() -> client.getStockList("N"))
                .isInstanceOfSatisfying(KiwoomApiException.class, exception -> {
                    assertThat(exception.getReturnCode()).isEqualTo("3");
                    assertThat(exception.getErrorCode()).isEqualTo("8005");
                    assertThat(exception.getMessage()).contains("인증에 실패했습니다");
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    void 오류_본문이_JSON이_아니어도_HTTP_오류를_유지한다() {
        respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");

        assertThatThrownBy(() -> client.getStockList("N"))
                .isInstanceOfSatisfying(KiwoomApiException.class, exception -> {
                    assertThat(exception.getReturnCode()).isNull();
                    assertThat(exception.getErrorCode()).isNull();
                    assertThat(exception.getMessage()).contains("500");
                    assertThat(exception.getCause()).isInstanceOf(WebClientResponseException.class);
                });
    }

    @Test
    void 응답_본문이_비어있으면_실패한다() {
        respond(HttpStatus.OK, "");

        assertThatThrownBy(() -> client.getStockList("N"))
                .isInstanceOf(KiwoomApiException.class)
                .hasMessage("키움 종목 목록 응답이 비어 있습니다.");
    }

    private void respond(HttpStatus status, String body) {
        ClientResponse.Builder builder = ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("cont-yn", "N");
        if (!body.isEmpty()) {
            builder.body(body);
        }
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(Mono.just(builder.build()));
    }
}
