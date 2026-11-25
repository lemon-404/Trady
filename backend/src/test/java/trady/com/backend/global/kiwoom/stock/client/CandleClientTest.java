package trady.com.backend.global.kiwoom.stock.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import trady.com.backend.global.kiwoom.auth.KiwoomTokenProvider;
import trady.com.backend.global.kiwoom.candle.historical.CandleClient;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleRequest;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleResponse;
import trady.com.backend.global.kiwoom.error.KiwoomErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 실제 서버에 접속하지 않고 차트 요청의 경로·인증·연속 조회 헤더를 검증한다. */
class CandleClientTest {
    @Test
    void sendsFirstPageRequestWithTokenAndChartTr() {
        request("N", "ka11080", "1", "N", "");
    }

    @Test
    void sendsContinuationCursorUsingTheSameChartEndpoint() {
        request("page-2", "ka11082", "1w", "Y", "page-2");
    }

    private void request(String nextKey, String tr, String interval, String expectedContinuation, String expectedKey) {
        var provider = mock(KiwoomTokenProvider.class);
        when(provider.getToken()).thenReturn("test-token");
        var webClient = WebClient.builder().baseUrl("https://kiwoom.test")
                .exchangeFunction(request -> {
                    assertThat(request.url().getPath()).isEqualTo("/api/dostk/chart");
                    assertThat(request.headers().getFirst("Authorization")).isEqualTo("Bearer test-token");
                    assertThat(request.headers().getFirst("api-id")).isEqualTo(tr);
                    assertThat(request.headers().getFirst("cont-yn")).isEqualTo(expectedContinuation);
                    assertThat(request.headers().getFirst("next-key")).isEqualTo(expectedKey);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body("{\"return_code\":0,\"stk_cd\":\"005930\",\"stk_min_pole_chart_qry\":[]}")
                            .build());
                }).build();
        var client = new CandleClient(webClient, provider, new KiwoomErrorHandler(new ObjectMapper()));
        var response = client.request(new CandleRequest("005930", "20261125", interval), nextKey, tr, CandleResponse.class);
        assertThat(response.getBody().getReturnCode()).isZero();
        assertThat(response.getBody().getStockCode()).isEqualTo("005930");
    }
}
