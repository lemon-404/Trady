package trady.com.backend.candle.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import trady.com.backend.candle.domain.CandleInterval;

@Converter
public class CandleIntervalConverter implements AttributeConverter<CandleInterval, String> {
    @Override
    public String convertToDatabaseColumn(CandleInterval interval) {
        return interval == null ? null : interval.code();
    }

    @Override
    public CandleInterval convertToEntityAttribute(String code) {
        return code == null ? null : CandleInterval.fromCode(code);
    }
}
