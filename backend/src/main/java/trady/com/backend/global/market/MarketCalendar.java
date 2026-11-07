package trady.com.backend.global.market;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

// TODO: 거래소 휴장일 자동 조회를 연동하고, 조회 결과를 장 운영 여부 판단에 반영한다.

@Component
public class MarketCalendar {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final LocalTime openTime;
    private final LocalTime closeTime;
    private final Set<LocalDate> holidays;

    public MarketCalendar(
            @Value("${market.open-time:09:00}") String openTime,
            @Value("${market.close-time:15:30}") String closeTime,
            @Value("${market.holidays:}") String holidays
    ) {
        this.openTime = LocalTime.parse(openTime);
        this.closeTime = LocalTime.parse(closeTime);
        this.holidays = Arrays.stream(holidays.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(LocalDate::parse)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(SEOUL);
        LocalDate date = now.toLocalDate();
        LocalTime time = now.toLocalTime();

        DayOfWeek day = date.getDayOfWeek();

        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }

        if (holidays.contains(date)) {
            return false;
        }

        return !time.isBefore(openTime) && time.isBefore(closeTime);
    }
}

