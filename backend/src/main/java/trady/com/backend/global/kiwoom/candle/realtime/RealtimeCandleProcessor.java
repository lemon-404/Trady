package trady.com.backend.global.kiwoom.candle.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleResponse.CandleItem;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/** 응답의 봉을 검증하고 저장 대상을 선별한다. DB 저장 및 마지막 반영 시각 갱신은 담당하지 않는다. */
@Slf4j
@Component
public class RealtimeCandleProcessor {
    private static final DateTimeFormatter MINUTE_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);

    /** lastAppliedTime이 없으면 초기 수집으로 판단하여 최신 봉과 직전 봉만 반환한다. */
    public List<CandleItem> process(List<CandleItem> items, RealtimeCandleScope scope, LocalDateTime lastAppliedTime) {
        Objects.requireNonNull(items, "캔들 목록이 필요합니다.");
        Objects.requireNonNull(scope, "봉 주기가 필요합니다.");

        var bars = new TreeMap<LocalDateTime, CandleItem>();
        var conflicts = new HashSet<LocalDateTime>();

        for (var item : items) {
            try {
                var normalized = normalize(item);
                var time = parseTime(normalized.time(), scope);
                var previous = bars.putIfAbsent(time, normalized);
                if (previous != null && !previous.equals(normalized)) conflicts.add(time);
            } catch (IllegalArgumentException | DateTimeException exception) {
                log.warn("유효하지 않은 봉 제외: 주기={}, 시각={}", scope.interval(), item == null ? null : item.time());
            }
        }

        // 같은 응답에서 동일 시각의 값이 충돌하면 어느 쪽도 선택하지 않는다.
        for (var time : conflicts) {
            bars.remove(time);
            log.warn("중복 값이 충돌하는 봉 제외: 주기={}, 시각={}", scope.interval(), time);
        }

        if (lastAppliedTime == null) {
            return bars.values().stream().skip(Math.max(0, bars.size() - 2)).toList();
        }

        // 마지막 반영 봉도 다시 포함하여 진행 중이던 봉의 값을 갱신한다.
        var selected = new TreeMap<>(bars.tailMap(lastAppliedTime, true));
        var previous = bars.lowerEntry(lastAppliedTime);
        if (previous != null) selected.put(previous.getKey(), previous.getValue());
        return List.copyOf(selected.values());
    }

    LocalDateTime parseTime(String time, RealtimeCandleScope scope) {
        if (time == null || time.isBlank()) throw new IllegalArgumentException("봉 시각 누락");
        return scope.minute() ? LocalDateTime.parse(time, MINUTE_TIME)
                : LocalDate.parse(time, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay();
    }

    private CandleItem normalize(CandleItem item) {
        if (item == null) throw new IllegalArgumentException("봉 데이터 누락");
        long open = Math.abs((long) item.open());
        long high = Math.abs((long) item.high());
        long low = Math.abs((long) item.low());
        long close = Math.abs((long) item.close());

        if (low <= 0 || high > Integer.MAX_VALUE || open > high || close > high
                || open < low || close < low || item.quantity() < 0) {
            throw new IllegalArgumentException("유효하지 않은 OHLCV");
        }

        return new CandleItem((int) open, (int) high, (int) low, (int) close, item.quantity(), item.time());
    }
}
