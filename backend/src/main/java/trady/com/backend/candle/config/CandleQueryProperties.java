package trady.com.backend.candle.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "trady.candle.query")
public record CandleQueryProperties(@DefaultValue("100") @Min(1) int pageSize) {
}
