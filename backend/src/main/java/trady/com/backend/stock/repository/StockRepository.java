package trady.com.backend.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import trady.com.backend.entity.stock.Stock;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByCode(String stockCode);

    List<Stock> findAllByCodeIn(List<String> codes);
}
