package trady.com.backend.candle.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import trady.com.backend.candle.support.CandleDataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import trady.com.backend.candle.dto.CandleContinuousQueryRequest;
import trady.com.backend.candle.dto.CandleContinuousQueryResponse;
import trady.com.backend.candle.repository.CandleQueryRepository;
import trady.com.backend.candle.service.CandleQueryService;
import trady.com.backend.candle.entity.Candle;
import trady.com.backend.candle.domain.CandleInterval;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.stock.repository.StockQueryRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller·조회 서비스·Repository·H2 DB를 연결하여 최초·연속 조회 동작을 검증한다.
 */
@CandleDataJpaTest
@org.springframework.test.context.TestPropertySource(properties = "trady.candle.query.page-size=3")
@Import({CandleController.class, CandleQueryService.class})
class CandleControllerFunctionalTest {
    @Autowired CandleController controller;
    @Autowired
    StockQueryRepository stocks;
    @Autowired
    CandleQueryRepository candles;
    @Autowired EntityManager em;

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 9, 9, 0);

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new CandleExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"1,3", "5,3", "15,3", "30,3", "60,3"})
    @DisplayName("커서가 없는 최초 조회는 최신 캔들부터 조회한다")
    void returnsLatestPageWithoutCursor(String interval, int limit) throws Exception {
        seed(interval, limit + 1);
        var response = request("N", interval, null);
        assertThat(response.candles()).hasSize(limit);
        assertThat(response.contYn()).isEqualTo("Y");
        assertThat(response.nextCursor()).isEqualTo(START.plusMinutes(Integer.parseInt(interval)));
        assertThat(response.candles().get(0).time())
                .isEqualTo(START.plusMinutes((long) limit * Integer.parseInt(interval)));
        assertDescending(response);
    }

    @ParameterizedTest
    @CsvSource({"1,3", "5,3", "15,3"})
    @DisplayName("최초 조회의 페이지 크기·커서·정렬을 확인한다")
    void returnsInitialCandlePage(String interval, int limit) throws Exception {
        seed(interval, limit + 1);
        LocalDateTime cursor = START.plusMinutes((long) limit * Integer.parseInt(interval));
        var response = request("N", interval, cursor);
        assertThat(response.candles()).hasSize(limit);
        assertThat(response.contYn()).isEqualTo("Y");
        assertThat(response.nextCursor()).isEqualTo(START.plusMinutes(Integer.parseInt(interval)));
        assertThat(response.candles().get(0).time()).isEqualTo(cursor);
        assertDescending(response);
    }

    @ParameterizedTest
    @CsvSource({"1,3", "5,3", "15,3"})
    @DisplayName("마지막 페이지까지 중복 없이 연속 조회한다")
    void returnsContinuousPagesWithoutDuplicates(String interval, int limit) throws Exception {
        int total = limit * 2 + 1;
        seed(interval, total);
        LocalDateTime cursor = START.plusMinutes((long) (total - 1) * Integer.parseInt(interval));
        var response = request("N", interval, cursor);
        var seen = new HashSet<LocalDateTime>();
        int page = 1;
        while (true) {
            assertDescending(response);
            assertThat(response.candles()).isNotEmpty().hasSizeLessThanOrEqualTo(limit);
            for (var item : response.candles()) {
                assertThat(seen.add(item.time())).as("페이지 간 중복 시각: %s", item.time()).isTrue();
                assertThat(item.open()).isEqualTo(100);
                assertThat(item.high()).isEqualTo(120);
                assertThat(item.low()).isEqualTo(90);
                assertThat(item.close()).isEqualTo(110);
                assertThat(item.volume()).isEqualTo(50L);
            }
            if ("N".equals(response.contYn())) {
                assertThat(response.nextCursor()).isNull();
                assertThat(response.candles()).hasSize(1);
                break;
            }
            assertThat(response.contYn()).isEqualTo("Y");
            assertThat(response.nextCursor()).isEqualTo(response.candles().get(response.candles().size() - 1).time());
            cursor = response.nextCursor();
            assertThat(++page).as("연속조회 종료").isLessThanOrEqualTo(3);
            response = request("Y", interval, cursor);
            LocalDateTime boundary = cursor;
            assertThat(response.candles()).allSatisfy(item -> assertThat(item.time()).isBefore(boundary));
        }
        assertThat(seen).hasSize(total);
    }

    private CandleContinuousQueryResponse request(String contYn, String interval,
                                                  LocalDateTime cursor) throws Exception {
        String body = mapper.writeValueAsString(new CandleContinuousQueryRequest("005930", interval + "m", cursor));
        var result = mvc.perform(post("/trady/v1/stock/candle").header("cont-yn", contYn)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        status().isOk().match(result);
        return mapper.treeToValue(mapper.readTree(result.getResponse().getContentAsString()).get("data"),
                CandleContinuousQueryResponse.class);
    }

    private void seed(String interval, int count) {
        Stock stock = stocks.save(new Stock("005930", "삼성전자", "코스피"));
        List<Candle> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new Candle(stock, CandleInterval.fromCode(interval + "m"), START.plusMinutes((long) i * Integer.parseInt(interval)),
                    100, 120, 90, 110, 50L));
        }
        candles.saveAll(rows);
        em.flush();
        em.clear();
    }

    private void assertDescending(CandleContinuousQueryResponse response) {
        assertThat(response.candles().stream().map(CandleContinuousQueryResponse.CandleItem::time).toList())
                .isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }
}
