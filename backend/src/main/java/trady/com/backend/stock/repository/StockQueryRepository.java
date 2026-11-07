package trady.com.backend.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import trady.com.backend.stock.entity.Stock;

import java.util.List;
import java.util.Optional;

public interface StockQueryRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByCode(String stockCode);

    List<Stock> findAllByCodeIn(List<String> codes);

    @Query("select s.code from Stock s")
    List<String> findAllCodes();

}
