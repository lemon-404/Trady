package trady.com.backend.candle.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
import trady.com.backend.candle.repository.CandleQueryRepository;
import trady.com.backend.candle.config.CandleQueryProperties;

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
 * TRADY_DB_TESTS=true를 지정하여 IDE 또는 Gradle에서 개별 실행한다.
 * 데이터 생성·삭제와 키움 호출·Runner 실행은 하지 않는다.
 * 측정에는 Service 트랜잭션·DB 조회·DTO 구성이 포함되며 HTTP·JSON 직렬화는 제외한다.
 * 동일 DB 데이터와 환경에서 반복 비교한다. 워밍업은 DB 캐시를 초기화하지 않는다.
 */
@Tag("candle-performance")
@EnabledIfEnvironmentVariable(named = "TRADY_DB_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CandleQueryService.class)
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
    CandleQueryService service;
    @Autowired CandleQueryProperties properties;
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
        var request = new CandleContinuousQueryRequest(STOCK_CODE, interval + "m", null);
        for (int i = 0; i < WARMUP_COUNT; i++) {
            assertThat(service.getCandles(request).candles()).as("측정 대상 데이터 필요").isNotEmpty();
        }
        List<Double> times = new ArrayList<>();
        List<Map<String, Object>> measurements = new ArrayList<>();
        for (int i = 1; i <= MEASUREMENT_COUNT; i++) {
            long start = System.nanoTime();
            var response = service.getCandles(request);
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
        result.put("cursor", null);
        result.put("measurementCount", times.size());
        result.put("requests", measurements);
        result.put("statistics", statistics(times));
        initialResults.add(result);
        saveResults("1. 단일 조회", initialResults);
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
        result.put("interval", interval + "m");
        result.put("pageSize", properties.pageSize());
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
        String repositoryVersion = CandleQueryRepository.class.getSimpleName();
        String serviceVersion = CandleQueryService.class.getSimpleName();
        Path file = resultFiles.get(mode);
        if (file == null) {
            Path directory = backend.resolve("docs/performance/candle-query").resolve(mode);
            Files.createDirectories(directory);
            String name = reportName(repositoryVersion, serviceVersion, measuredAt);
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

    static String reportName(String repositoryName, String serviceName, ZonedDateTime measuredAt) {
        return repositoryName + "-" + serviceName + "-"
                + measuredAt.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

}
