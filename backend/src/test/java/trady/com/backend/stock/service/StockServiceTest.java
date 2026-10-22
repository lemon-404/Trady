package trady.com.backend.stock.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import trady.com.backend.stock.entity.Stock;
import trady.com.backend.stock.repository.StockQueryRepository;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.globally_quoted_identifiers=true")
@Import(StockService.class)
class StockServiceTest {
    @Autowired StockService service;
    @Autowired
    StockQueryRepository repository;
    @Autowired EntityManager em;

    @Test
    void 신규_종목을_저장한다() {
        service.savePage(List.of(new Stock("005930", "삼성전자", "코스피")));
        flushAndClear();
        Stock saved = repository.findByCode("005930").orElseThrow();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("삼성전자");
        assertThat(saved.getMarket()).isEqualTo("코스피");
    }

    @Test
    void 기존_종목은_갱신하고_신규_종목만_추가한다() {
        service.savePage(List.of(new Stock("005930", "기존 이름", "기존 시장"),
                new Stock("000660", "SK하이닉스", "코스피")));
        flushAndClear();
        Stock before = repository.findByCode("005930").orElseThrow();
        Long id = before.getId();
        var createdAt = before.getCreatedAt();
        em.clear();
        service.savePage(List.of(new Stock("005930", "삼성전자", "코스피"),
                new Stock("005380", "현대차", "코스피")));
        flushAndClear();
        Stock after = repository.findByCode("005930").orElseThrow();
        assertThat(after.getId()).isEqualTo(id);
        assertThat(after.getCreatedAt()).isEqualTo(createdAt);
        assertThat(after.getName()).isEqualTo("삼성전자");
        assertThat(after.getMarket()).isEqualTo("코스피");
        assertThat(repository.count()).isEqualTo(3);
        assertThat(repository.findByCode("000660")).isPresent();
    }

    @Test
    void 같은_목록을_재저장해도_중복되지_않는다() {
        service.savePage(List.of(new Stock("005930", "삼성전자", "코스피")));
        flushAndClear();
        Long id = repository.findByCode("005930").orElseThrow().getId();
        em.clear();
        service.savePage(List.of(new Stock("005930", "삼성전자", "코스피")));
        flushAndClear();
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByCode("005930").orElseThrow().getId()).isEqualTo(id);
    }

    private void flushAndClear() { em.flush(); em.clear(); }
}
