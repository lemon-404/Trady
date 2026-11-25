package trady.com.backend.candle.service;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CandlePerformanceReportNameTest {
    @Test
    void supportsClassNamesWithoutLegacyVersionSuffix() {
        var measuredAt = ZonedDateTime.of(2026, 11, 25, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"));

        assertThat(CandleQueryServiceV1PerformanceTest.reportName("CandleQueryRepository", "CandleQueryService", measuredAt))
                .isEqualTo("CandleQueryRepository-CandleQueryService-20261125-090000");
    }
}
