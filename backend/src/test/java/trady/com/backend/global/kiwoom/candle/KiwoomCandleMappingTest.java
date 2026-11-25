package trady.com.backend.global.kiwoom.candle;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;
import trady.com.backend.candle.domain.CandleInterval;

import static org.assertj.core.api.Assertions.assertThat;

class KiwoomCandleMappingTest {
    @ParameterizedTest
    @CsvSource({"1m,ka11080,1", "5m,ka11080,5", "15m,ka11080,15", "30m,ka11080,30", "60m,ka11080,60",
            "1d,ka11081,", "1w,ka11082,", "1M,ka11083,", "1y,ka11094,"})
    void translatesStandardIntervalOnlyAtKiwoomBoundary(String code, String tr, String tickScope) {
        CandleInterval interval = CandleInterval.fromCode(code);
        var request = KiwoomCandleMapping.request("005930", "20261125", interval);
        assertThat(KiwoomCandleMapping.tr(interval)).isEqualTo(tr);
        assertThat(request.getInterval()).isEqualTo(tickScope);
        var json = new ObjectMapper().valueToTree(request);
        assertThat(json.get("stk_cd").asText()).isEqualTo("005930");
        assertThat(json.get("base_dt").asText()).isEqualTo("20261125");
        if (interval.isMinute()) {
            assertThat(json.get("tic_scope").asText()).isEqualTo(tickScope);
        } else {
            assertThat(json.has("tic_scope")).isFalse();
        }
    }
}
