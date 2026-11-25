package trady.com.backend.global.kiwoom.candle;

import trady.com.backend.candle.domain.CandleInterval;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleRequest;

/** 과거·실시간 수집이 공유하는 정규장 차트 요청 형식. 도메인·DB에는 이 형식을 저장하지 않는다. */
public final class KiwoomCandleMapping {
    private KiwoomCandleMapping() { }

    public static String tr(CandleInterval interval) {
        return switch (interval) {
            case MINUTE_1, MINUTE_5, MINUTE_15, MINUTE_30, MINUTE_60 -> "ka11080";
            case DAY -> "ka11081";
            case WEEK -> "ka11082";
            case MONTH -> "ka11083";
            case YEAR -> "ka11094";
        };
    }

    public static CandleRequest request(String stockCode, String baseDate, CandleInterval interval) {
        String tickScope = interval.isMinute() ? Integer.toString(interval.minutes()) : null;
        return new CandleRequest(stockCode, baseDate, tickScope);
    }
}
