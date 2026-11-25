package trady.com.backend.global.kiwoom.candle.realtime;

import org.junit.jupiter.api.Test;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleResponse.CandleItem;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeCandleProcessorTest {
    private final RealtimeCandleProcessor processor = new RealtimeCandleProcessor();

    @Test
    void selectsAllNewBarsAndOnePreviousBarInTimeOrder() {
        var result = processor.process(List.of(bar("20261124090400"), bar("20261124090000"), bar("20261124090300"),
                bar("20261124090100"), bar("20261124090200")), RealtimeCandleScope.MINUTE_1,
                LocalDateTime.of(2026, 11, 24, 9, 2));

        assertThat(result).extracting(CandleItem::time).containsExactly(
                "20261124090100", "20261124090200", "20261124090300", "20261124090400");
    }

    @Test
    void selectsLatestTwoForInitialCollectionIncludingPreviousTradingDay() {
        var result = processor.process(List.of(bar("20261123150000"), bar("20261124090000"), bar("20261123151500")),
                RealtimeCandleScope.MINUTE_15, null);

        assertThat(result).extracting(CandleItem::time).containsExactly("20261123151500", "20261124090000");
    }

    @Test
    void normalizesSignedPricesAndDeduplicatesEquivalentValues() {
        var positive = new CandleItem(10, 12, 9, 11, 100, "20261124090000");
        var result = processor.process(List.of(bar("20261124090000"), positive), RealtimeCandleScope.MINUTE_1, null);

        assertThat(result).containsExactly(positive);
    }

    @Test
    void excludesConflictingTimestampEntirelyWithinOneResponse() {
        var result = processor.process(List.of(bar("20261124090000"),
                new CandleItem(10, 12, 9, 12, 200, "20261124090000"), bar("20261124090000"), bar("20261124090100")),
                RealtimeCandleScope.MINUTE_1, null);

        assertThat(result).extracting(CandleItem::time).containsExactly("20261124090100");
    }

    @Test
    void dropsInvalidDatesOhlcvAndNullItems() {
        var result = processor.process(Arrays.asList(null, bar(null), bar("20260230090000"), bar("20261124"),
                new CandleItem(10, 9, 8, 10, 1, "20261124090000"),
                new CandleItem(0, 12, 0, 11, 1, "20261124090000"),
                new CandleItem(Integer.MIN_VALUE, 12, 9, 11, 1, "20261124090000"),
                new CandleItem(10, 12, 9, 11, -1, "20261124090000"), bar("20261124090100")),
                RealtimeCandleScope.MINUTE_1, null);

        assertThat(result).extracting(CandleItem::time).containsExactly("20261124090100");
    }

    @Test
    void handlesDateBasedScopesAndEmptyResponse() {
        for (var scope : List.of(RealtimeCandleScope.DAY, RealtimeCandleScope.WEEK,
                RealtimeCandleScope.MONTH, RealtimeCandleScope.YEAR)) {
            assertThat(processor.process(List.of(bar("20261123"), bar("20261116")), scope,
                    LocalDateTime.of(2026, 11, 23, 0, 0)))
                    .extracting(CandleItem::time).containsExactly("20261116", "20261123");
        }
        assertThat(processor.process(List.of(), RealtimeCandleScope.MINUTE_1, null)).isEmpty();
    }

    private CandleItem bar(String time) { return new CandleItem(-10, -12, -9, -11, 100, time); }
}
