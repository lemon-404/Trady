package trady.com.backend.candle.domain;

import java.util.Arrays;

/** API와 DB에서 함께 사용하는 봉 단위. 대소문자를 구분한다. */
public enum CandleInterval {
    MINUTE_1("1m", 1), MINUTE_5("5m", 5), MINUTE_15("15m", 15),
    MINUTE_30("30m", 30), MINUTE_60("60m", 60),
    DAY("1d", 0), WEEK("1w", 0), MONTH("1M", 0), YEAR("1y", 0);

    private final String code;
    private final int minutes;

    CandleInterval(String code, int minutes) {
        this.code = code;
        this.minutes = minutes;
    }

    public String code() { return code; }
    public boolean isMinute() { return minutes > 0; }

    public int minutes() {
        if (!isMinute()) throw new IllegalStateException("분봉이 아닙니다: " + code);
        return minutes;
    }

    public static CandleInterval fromCode(String code) {
        return Arrays.stream(values()).filter(interval -> interval.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 봉 단위: " + code));
    }

    @Override
    public String toString() { return code; }
}
