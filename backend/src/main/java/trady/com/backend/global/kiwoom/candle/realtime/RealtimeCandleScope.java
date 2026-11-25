package trady.com.backend.global.kiwoom.candle.realtime;

import java.time.Duration;
import trady.com.backend.candle.domain.CandleInterval;
import trady.com.backend.global.kiwoom.candle.KiwoomCandleMapping;

public enum RealtimeCandleScope {
    MINUTE_1(CandleInterval.MINUTE_1, 4), MINUTE_5(CandleInterval.MINUTE_5, 12),
    MINUTE_15(CandleInterval.MINUTE_15, 24), MINUTE_30(CandleInterval.MINUTE_30, 36), MINUTE_60(CandleInterval.MINUTE_60, 60),
    DAY(CandleInterval.DAY, 30), WEEK(CandleInterval.WEEK, 60), MONTH(CandleInterval.MONTH, 120), YEAR(CandleInterval.YEAR, 300);

    private final CandleInterval interval;
    private final Duration refresh;

    RealtimeCandleScope(CandleInterval interval, long seconds) {
        this.interval = interval;
        this.refresh = Duration.ofSeconds(seconds);
    }

    public String tr() { return KiwoomCandleMapping.tr(interval); }
    public CandleInterval interval() { return interval; }
    public Duration refresh() { return refresh; }
    public boolean minute() { return interval.isMinute(); }
}
