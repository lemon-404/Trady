package trady.com.backend.global.kiwoom.candle.realtime;

import java.time.Duration;

public enum RealtimeCandleScope {
    MINUTE_1("ka10080", "1", 4), MINUTE_5("ka10080", "5", 12),
    MINUTE_15("ka10080", "15", 24), MINUTE_30("ka10080", "30", 36), MINUTE_60("ka10080", "60", 60),
    DAY("ka10081", "1d", 30), WEEK("ka10082", "1w", 60), MONTH("ka10083", "1M", 120), YEAR("ka10094", "1y", 300);

    private final String tr;
    private final String interval;
    private final Duration refresh;

    RealtimeCandleScope(String tr, String interval, long seconds) {
        this.tr = tr;
        this.interval = interval;
        this.refresh = Duration.ofSeconds(seconds);
    }

    public String tr() { return tr; }
    public String interval() { return interval; }
    public Duration refresh() { return refresh; }
    public boolean minute() { return tr.equals("ka10080"); }
}
