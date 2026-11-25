package trady.com.backend.candle.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import trady.com.backend.candle.support.CandleDataJpaTest;
import org.springframework.context.annotation.Import;
import trady.com.backend.candle.domain.CandleInterval;
import trady.com.backend.candle.repository.CandleRepository;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleResponse.CandleItem;
import trady.com.backend.stock.entity.Stock;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@CandleDataJpaTest
@Import(CandleQueryService.class)
class CandleIntervalPersistenceTest {
    @Autowired CandleQueryService service;
    @Autowired CandleRepository candles;
    @Autowired EntityManager em;

    @ParameterizedTest
    @EnumSource(CandleInterval.class)
    void storesStandardCodeAndUpdatesSameCandleOnRecollection(CandleInterval interval) {
        Stock stock = new Stock("005930", "삼성전자", "코스피");
        em.persist(stock);
        String time = interval.isMinute() ? "20261125090000" : "20261125";
        service.savePage(stock, interval, List.of(new CandleItem(100, 120, 90, 110, 50, time)));
        em.flush();
        em.clear();

        assertThat(em.createNativeQuery("select \"interval\" from \"stock_candle\"", String.class).getSingleResult())
                .isEqualTo(interval.code());
        var first = candles.findAll().get(0);
        assertThat(first.getInterval()).isSameAs(interval);
        Long id = first.getId();
        service.savePage(first.getStock(), interval, List.of(new CandleItem(100, 120, 90, 115, 60, time)));
        em.flush();
        em.clear();
        assertThat(candles.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getId()).isEqualTo(id);
            assertThat(saved.getInterval()).isSameAs(interval);
            assertThat(saved.getClosePrice()).isEqualTo(115);
            assertThat(saved.getVolume()).isEqualTo(60L);
        });
    }
}
