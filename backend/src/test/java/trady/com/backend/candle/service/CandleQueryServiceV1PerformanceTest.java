package trady.com.backend.candle.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import trady.com.backend.candle.dto.CandleContinuousQueryRequest;
import trady.com.backend.candle.dto.CandleContinuousQueryResponse;
import trady.com.backend.candle.repository.CandleQueryRepositoryV1;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.properties의 PostgreSQL에 이미 적재된 데이터로 조회 시간을 측정한다.
 * IDE의 JUnit 실행으로 개별 실행한다. 기본 테스트 제외 태그는 candle-performance이다.
 * 데이터 생성·삭제와 키움 호출·Runner 실행은 하지 않는다.
 * 측정에는 Service 트랜잭션·DB 조회·DTO 구성이 포함되며 HTTP·JSON 직렬화는 제외한다.
 * 동일 DB 데이터와 환경에서 반복 비교한다. 워밍업은 DB 캐시를 초기화하지 않는다.
 */
@Tag("candle-performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CandleQueryServiceV1.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CandleQueryServiceV1PerformanceTest {
    private static final String STOCK_CODE = "005930";
    private static final LocalDateTime UPPER = LocalDateTime.of(2026, 10, 9, 23, 59, 59);
    private static final LocalDateTime LOWER = LocalDateTime.of(2025, 10, 8, 0, 0);
    private static final int WARMUP_COUNT = 3;
    private static final int MEASUREMENT_COUNT = 10;

    private final ZonedDateTime measuredAt = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
    private final List<Map<String, Object>> initialResults = new ArrayList<>();
    private final List<Map<String, Object>> continuousResults = new ArrayList<>();
    private final Map<String, Path> resultFiles = new LinkedHashMap<>();

    @Autowired
    CandleQueryServiceV1 service;
    @Autowired DataSource dataSource;

    @BeforeEach
    void verifyPostgreSql() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "5", "15"})
    @DisplayName("PostgreSQL 최초 단일 조회 시간을 반복 측정한다")
    void measureInitialRequest(String interval) throws IOException {
        var request = new CandleContinuousQueryRequest(STOCK_CODE, interval, UPPER);
        for (int i = 0; i < WARMUP_COUNT; i++) {
            assertThat(service.getInitialCandles(request).candles()).as("측정 대상 데이터 필요").isNotEmpty();
        }
        List<Double> times = new ArrayList<>();
        List<Map<String, Object>> measurements = new ArrayList<>();
        for (int i = 1; i <= MEASUREMENT_COUNT; i++) {
            long start = System.nanoTime();
            var response = service.getInitialCandles(request);
            double ms = elapsedMs(start);
            times.add(ms);
            measurements.add(Map.of("sequence", i, "elapsedMs", ms,
                    "returnedCount", response.candles().size()));
            assertThat(response.candles()).isNotEmpty();
            System.out.printf("%s분봉 최초 조회 %d회: %.3fms, %d개%n",
                    interval, i, ms, response.candles().size());
        }
        printSummary(interval + "분봉 최초 조회", times);
        var result = commonResult(interval);
        result.put("cursor", UPPER);
        result.put("measurementCount", times.size());
        result.put("requests", measurements);
        result.put("statistics", statistics(times));
        initialResults.add(result);
        saveResults("1. 단일 조회", initialResults);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "5", "15"})
    @DisplayName("2026-10-09부터 2025-10-08까지 페이지별 연속 조회 시간을 측정한다")
    void measureContinuousRequestsWithinRange(String interval) throws IOException {
        var initialRequest = new CandleContinuousQueryRequest(STOCK_CODE, interval, UPPER);
        for (int i = 0; i < WARMUP_COUNT; i++) {
            assertThat(service.getInitialCandles(initialRequest).candles()).as("측정 대상 데이터 필요").isNotEmpty();
        }
        List<Double> times = new ArrayList<>();
        List<Map<String, Object>> measurements = new ArrayList<>();
        LocalDateTime cursor = UPPER;
        int page = 1;
        long inRangeCount = 0;
        LocalDateTime oldest = null;
        String stopReason;
        while (true) {
            var request = new CandleContinuousQueryRequest(STOCK_CODE, interval, cursor);
            long start = System.nanoTime();
            CandleContinuousQueryResponse response = page == 1
                    ? service.getInitialCandles(request)
                    : service.getContinuousCandles(request);
            double ms = elapsedMs(start);
            times.add(ms);
            var items = response.candles();
            long pageInRangeCount = items.stream().filter(item -> !item.time().isBefore(LOWER)
                    && !item.time().isAfter(UPPER)).count();
            var measurement = new LinkedHashMap<String, Object>();
            measurement.put("page", page);
            measurement.put("requestType", page == 1 ? "initial" : "continuous");
            measurement.put("cursor", cursor);
            measurement.put("elapsedMs", ms);
            measurement.put("returnedCount", items.size());
            measurement.put("inRangeCount", pageInRangeCount);
            measurement.put("oldestTime", items.isEmpty() ? null : items.get(0).time());
            measurement.put("newestTime", items.isEmpty() ? null : items.get(items.size() - 1).time());
            measurement.put("nextCursor", response.nextCursor());
            measurement.put("contYn", response.contYn());
            measurements.add(measurement);
            System.out.printf("%s분봉 %s %d페이지: %.3fms, 반환 %d개%n",
                    interval, page == 1 ? "최초" : "연속", page, ms, items.size());
            if (items.isEmpty()) {
                assertThat(page).as("측정 대상 데이터 필요").isGreaterThan(1);
                stopReason = "data-exhausted";
                break;
            }
            oldest = items.get(0).time();
            inRangeCount += pageInRangeCount;
            if (!oldest.isAfter(LOWER)) {
                stopReason = "range-start-reached";
                break;
            }
            if ("N".equals(response.contYn())) {
                assertThat(response.nextCursor()).isNull();
                stopReason = "data-exhausted";
                break;
            }
            assertThat(response.contYn()).isEqualTo("Y");
            assertThat(response.nextCursor()).isNotNull().isBefore(cursor).isEqualTo(oldest);
            cursor = response.nextCursor();
            page++;
        }
        printSummary(interval + "분봉 전체 페이지", times);
        System.out.printf("대상=%s, 요청 범위=%s ~ %s, 범위 내 %d개, 마지막 페이지 최저시각=%s, 종료=%s%n",
                STOCK_CODE, LOWER, UPPER, inRangeCount, oldest, stopReason);
        var result = commonResult(interval);
        result.put("rangeStart", LOWER);
        result.put("rangeEnd", UPPER);
        result.put("pageCount", times.size());
        result.put("pages", measurements);
        result.put("totalInRangeCount", inRangeCount);
        result.put("stopReason", stopReason);
        result.put("statistics", statistics(times));
        continuousResults.add(result);
        saveResults("2. 기간 연속 조회", continuousResults);
    }

    private double elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    private void printSummary(String label, List<Double> times) {
        var sorted = times.stream().sorted().toList();
        int middle = sorted.size() / 2;
        double median = sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0 : sorted.get(middle);
        double total = times.stream().mapToDouble(Double::doubleValue).sum();
        System.out.printf("%s: %d회, 합계 %.3fms, 평균 %.3fms, 중앙값 %.3fms, 최대 %.3fms%n",
                label, times.size(), total, total / times.size(), median, sorted.get(sorted.size() - 1));
    }

    private Map<String, Object> commonResult(String interval) {
        var result = new LinkedHashMap<String, Object>();
        result.put("stockCode", STOCK_CODE);
        result.put("interval", interval);
        result.put("pageSize", switch (interval) {
            case "1" -> 390;
            case "5" -> 78;
            case "15" -> 26;
            default -> throw new IllegalArgumentException("지원하지 않는 봉 주기");
        });
        result.put("warmupCount", WARMUP_COUNT);
        return result;
    }

    private Map<String, Double> statistics(List<Double> times) {
        var sorted = times.stream().sorted().toList();
        int middle = sorted.size() / 2;
        double median = sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2.0 : sorted.get(middle);
        double total = times.stream().mapToDouble(Double::doubleValue).sum();
        return Map.of("totalMs", total, "averageMs", total / times.size(),
                "medianMs", median, "maxMs", sorted.get(sorted.size() - 1));
    }

    private void saveResults(String mode, List<Map<String, Object>> results) throws IOException {
        Path backend = Path.of("").toAbsolutePath().normalize();
        if (!Files.isRegularFile(backend.resolve("build.gradle"))) {
            backend = backend.resolve("backend");
        }
        if (!Files.isRegularFile(backend.resolve("build.gradle"))) {
            throw new IOException("프로젝트 루트 또는 backend 폴더에서 테스트를 실행하세요.");
        }
        String repositoryVersion = CandleQueryRepositoryV1.class.getSimpleName();
        String serviceVersion = CandleQueryServiceV1.class.getSimpleName();
        Path file = resultFiles.get(mode);
        if (file == null) {
            Path directory = backend.resolve("docs/performance/candle-query").resolve(mode);
            Files.createDirectories(directory);
            String name = "Repo" + repositoryVersion.substring(repositoryVersion.lastIndexOf('V'))
                    + "-Service" + serviceVersion.substring(serviceVersion.lastIndexOf('V'))
                    + "-" + measuredAt.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            file = directory.resolve(name + ".json");
            if (Files.exists(file)) {
                file = directory.resolve(name + "-" + measuredAt.format(DateTimeFormatter.ofPattern("SSS"))
                        + "-" + System.nanoTime() + ".json");
            }
            Files.createFile(file);
            resultFiles.put(mode, file);
        }
        var document = new LinkedHashMap<String, Object>();
        document.put("repositoryVersion", repositoryVersion);
        document.put("serviceVersion", serviceVersion);
        document.put("measuredAt", measuredAt.toOffsetDateTime());
        document.put("measurementScope", "service");
        document.put("results", results);
        String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(document);
        Files.writeString(file, json + System.lineSeparator(), StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("성능 측정 결과 저장: " + file);
    }

}
