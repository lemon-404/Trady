package trady.com.backend.candle.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import trady.com.backend.candle.entity.CandleIntervalConverter;

import static org.assertj.core.api.Assertions.*;

class CandleIntervalTest {
    @ParameterizedTest
    @EnumSource(CandleInterval.class)
    void standardCodeRoundTripsThroughDatabaseConverter(CandleInterval interval) {
        var converter = new CandleIntervalConverter();
        assertThat(CandleInterval.fromCode(interval.code())).isSameAs(interval);
        assertThat(converter.convertToDatabaseColumn(interval)).isEqualTo(interval.code());
        assertThat(converter.convertToEntityAttribute(interval.code())).isSameAs(interval);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"1", "5", "15", "30", "60", "1Y", "1D", "2m", "", " 1m"})
    void rejectsLegacyAndUnsupportedCodes(String code) {
        assertThatThrownBy(() -> CandleInterval.fromCode(code)).isInstanceOf(IllegalArgumentException.class);
    }
}
