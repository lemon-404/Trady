package trady.com.backend.candle.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;
import trady.com.backend.candle.dto.CandleContinuousQueryRequest;
import trady.com.backend.candle.dto.CandleContinuousQueryResponse;
import trady.com.backend.candle.config.CandleQueryProperties;

import static org.assertj.core.api.Assertions.assertThat;

/** TRADY_DB_TESTS=true일 때 실제 DB의 기존 데이터를 조회하고 응답 JSON을 출력한다. */
@EnabledIfEnvironmentVariable(named = "TRADY_DB_TESTS", matches = "true")
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CandleQueryService.class)
class CandleQueryServiceV1Test {
    private static final String STOCK_CODE = "005930";
    private static final String INTERVAL = "15m";

    @Autowired CandleQueryService service;
    @Autowired CandleQueryProperties properties;

    @Test
    @DisplayName("실제 DB 최초 조회 결과를 출력한다")
    void returnsLatestCandles() {
        var response = service.getCandles(new CandleContinuousQueryRequest(STOCK_CODE, INTERVAL, null));

        printResponse("최초 조회", response);
        assertPage(response);
    }

    @Test
    @DisplayName("실제 DB에서 최초 응답의 커서로 연속 조회한 결과를 출력한다")
    void returnsRemainingCandlesBeforeCursor() {
        var initial = service.getCandles(new CandleContinuousQueryRequest(STOCK_CODE, INTERVAL, null));
        printResponse("최초 조회", initial);
        assertPage(initial);
        assertThat(initial.nextCursor()).as("연속 조회를 위해 페이지 크기보다 많은 15분봉이 필요합니다.").isNotNull();
        var response = service.getContinuousCandles(
                new CandleContinuousQueryRequest(STOCK_CODE, INTERVAL, initial.nextCursor()));

        printResponse("연속 조회", response);
        assertPage(response);
        assertThat(response.candles()).allSatisfy(item ->
                assertThat(item.time()).isBefore(initial.nextCursor()));
    }

    private void assertPage(CandleContinuousQueryResponse response) {
        assertThat(response.candles()).as("조회 대상의 기존 DB 데이터가 필요합니다.")
                .isNotEmpty().hasSizeLessThanOrEqualTo(properties.pageSize());
        assertThat(response.candles().stream().map(CandleContinuousQueryResponse.CandleItem::time).toList())
                .isSortedAccordingTo(java.util.Comparator.reverseOrder()).doesNotHaveDuplicates();
        assertThat(response.contYn()).isIn("Y", "N");
        if ("Y".equals(response.contYn())) {
            assertThat(response.candles()).hasSize(properties.pageSize());
            assertThat(response.nextCursor()).isEqualTo(response.candles().get(response.candles().size() - 1).time());
        } else {
            assertThat(response.nextCursor()).isNull();
        }
    }

    private void printResponse(String label, CandleContinuousQueryResponse response) {
        System.out.println(label + " 결과:");
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(response));
    }
}
