package trady.com.backend.candle.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import trady.com.backend.candle.repository.CandleRepository;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.global.kiwoom.candle.dto.KiwoomStockCandleResponse.CandleItem;
import trady.com.backend.stock.repository.StockQueryRepository;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.globally_quoted_identifiers=true")
@Import(CandleService.class)
class CandleServiceTest {
    @Autowired
    CandleService service;
    @Autowired
    CandleRepository repository;
    @Autowired
    StockQueryRepository stocks;
    @Autowired EntityManager em;
    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 9, 9, 0);

    @Test
    void 가격은_절댓값으로_거래량은_그대로_저장한다() {
        Stock stock = stocks.save(new Stock("005930", "삼성전자", "코스피"));
        service.savePage(stock, "15", List.of(item(-100, -120, -90, -110, 50)));
        flushAndClear();
        var saved = repository.findAllByStock_IdAndIntervalAndTimeIn(stock.getId(), "15", List.of(TIME)).get(0);
        assertThat(saved.getOpenPrice()).isEqualTo(100);
        assertThat(saved.getHighPrice()).isEqualTo(120);
        assertThat(saved.getLowPrice()).isEqualTo(90);
        assertThat(saved.getClosePrice()).isEqualTo(110);
        assertThat(saved.getVolume()).isEqualTo(50L);
    }

    @Test
    void 기존_캔들을_갱신하고_새_시각의_캔들만_추가한다() {
        Stock stock = stocks.save(new Stock("005930", "삼성전자", "코스피"));
        service.savePage(stock, "15", List.of(item(100, 120, 90, 110, 50)));
        flushAndClear();
        Long id = repository.findAll().get(0).getId();
        em.clear();
        service.savePage(stock, "15", List.of(item(101, 130, 80, 115, 60),
                new CandleItem(110, 140, 100, 130, 70, "20261009091500")));
        flushAndClear();
        var saved = repository.findById(id).orElseThrow();
        assertThat(repository.count()).isEqualTo(2);
        assertThat(saved.getTime()).isEqualTo(TIME);
        assertThat(saved.getOpenPrice()).isEqualTo(101);
        assertThat(saved.getHighPrice()).isEqualTo(130);
        assertThat(saved.getLowPrice()).isEqualTo(80);
        assertThat(saved.getClosePrice()).isEqualTo(115);
        assertThat(saved.getVolume()).isEqualTo(60L);
    }

    @Test
    void 재저장은_중복되지_않고_종목과_봉주기는_구분한다() {
        Stock first = stocks.save(new Stock("005930", "삼성전자", "코스피"));
        Stock second = stocks.save(new Stock("000660", "SK하이닉스", "코스피"));
        var items = List.of(item(100, 120, 90, 110, 50));
        service.savePage(first, "1", items);
        flushAndClear();
        Long id = repository.findAll().get(0).getId();
        em.clear();
        service.savePage(first, "1", items);
        service.savePage(first, "5", items);
        service.savePage(first, "15", items);
        service.savePage(second, "1", items);
        flushAndClear();
        assertThat(repository.count()).isEqualTo(4);
        assertThat(repository.findAllByStock_IdAndIntervalAndTimeIn(first.getId(), "1", List.of(TIME)))
                .singleElement().satisfies(c -> assertThat(c.getId()).isEqualTo(id));
    }

    private CandleItem item(int open, int high, int low, int close, long volume) {
        return new CandleItem(open, high, low, close, volume, "20261009090000");
    }
    private void flushAndClear() { em.flush(); em.clear(); }
}
