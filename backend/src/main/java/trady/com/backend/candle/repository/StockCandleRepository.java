package trady.com.backend.candle.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import trady.com.backend.entity.stock.Candle;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface StockCandleRepository extends JpaRepository<Candle, Long> {
    List<Candle> findAllByStock_IdAndIntervalAndTimeIn(
            Long stockId, String interval, Collection<LocalDateTime> times
    );
}
