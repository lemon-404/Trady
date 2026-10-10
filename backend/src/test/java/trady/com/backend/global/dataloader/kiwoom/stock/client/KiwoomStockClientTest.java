package trady.com.backend.global.dataloader.kiwoom.stock.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.ObjectMapper;
import trady.com.backend.global.dataloader.kiwoom.stock.dto.KiwoomStockResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KiwoomStockClientTest {

    private HttpServer server;
    private KiwoomStockClient client;
    private final AtomicReference<Request> request = new AtomicReference<>();
    private int status;
    private String responseBody;

    @BeforeEach
    void setUp() throws IOException {
        status = 200;
        responseBody = """
                {"code":"005930","name":"삼성전자","listCount":"5969782550",
                 "auditInfo":"정상","regDay":"19750611","lastPrice":"70000",
                 "state":"정상","marketCode":"0","marketName":"거래소",
                 "upName":"전기전자","upSizeName":"대형주","companyClassName":"",
                 "orderWarning":"0","nxtEnable":"Y","return_code":0,"return_msg":"정상 처리",
                 "additional_field":"ignored"}
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/dostk/stkinfo", exchange -> {
            request.set(new Request(exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("api-id"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            if (status == 204) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        client = new KiwoomStockClient(WebClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("종목 코드와 인증 헤더를 전송하고 외부 종목 응답을 매핑한다")
    void requestsAndMapsStockInfo() {
        KiwoomStockResponse response = client.getStockInfo("005930", "test-token");

        Request sent = request.get();
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.authorization()).isEqualTo("Bearer test-token");
        assertThat(sent.apiId()).isEqualTo("ka10100");
        assertThat(sent.contentType()).startsWith("application/json");
        assertThat(new ObjectMapper().readTree(sent.body()).get("stk_cd").asText())
                .isEqualTo("005930");
        assertThat(response.stockCode()).isEqualTo("005930");
        assertThat(response.stockName()).isEqualTo("삼성전자");
        assertThat(response.listCount()).isEqualTo("5969782550");
        assertThat(response.auditInfo()).isEqualTo("정상");
        assertThat(response.regDay()).isEqualTo("19750611");
        assertThat(response.lastPrice()).isEqualTo("70000");
        assertThat(response.state()).isEqualTo("정상");
        assertThat(response.marketCode()).isEqualTo("0");
        assertThat(response.marketName()).isEqualTo("거래소");
        assertThat(response.upName()).isEqualTo("전기전자");
        assertThat(response.upSizeName()).isEqualTo("대형주");
        assertThat(response.companyClassName()).isEmpty();
        assertThat(response.orderWarning()).isEqualTo("0");
        assertThat(response.nxtEnable()).isEqualTo("Y");
        assertThat(response.returnCode()).isZero();
    }

    @Test
    @DisplayName("HTTP 200의 업무 오류를 정상 종목으로 오인하지 않도록 응답 정보를 보존한다")
    void preservesBusinessError() {
        responseBody = "{\"return_code\":8005,\"return_msg\":\"접근 토큰이 만료되었습니다\"}";

        KiwoomStockResponse response = client.getStockInfo("005930", "expired-test-token");

        assertThat(response.returnCode()).isEqualTo(8005);
        assertThat(response.returnMsg()).contains("토큰");
        assertThat(response.stockCode()).isNull();
    }

    @Test
    @DisplayName("HTTP 인증 오류는 상태 코드를 보존하여 전달한다")
    void propagatesHttpError() {
        status = 401;
        responseBody = "{}";

        assertThatThrownBy(() -> client.getStockInfo("005930", "invalid-test-token"))
                .isInstanceOf(WebClientResponseException.Unauthorized.class);
    }

    @Test
    @DisplayName("응답이 비어 있으면 실패로 처리한다")
    void rejectsEmptyResponse() {
        status = 204;

        assertThatThrownBy(() -> client.getStockInfo("005930", "test-token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("종목 코드나 토큰이 비어 있으면 외부 요청을 보내지 않는다")
    void rejectsMissingInput() {
        assertThatThrownBy(() -> client.getStockInfo(" ", "test-token"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getStockInfo("005930", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(request.get()).isNull();
    }

    private record Request(String method, String authorization, String apiId,
                           String contentType, String body) {
    }
}
