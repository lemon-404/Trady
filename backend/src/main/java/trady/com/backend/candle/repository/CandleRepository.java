package trady.com.backend.candle.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import trady.com.backend.candle.entity.Candle;
import trady.com.backend.candle.domain.CandleInterval;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface CandleRepository extends JpaRepository<Candle, Long> {
    List<Candle> findAllByStock_IdAndIntervalAndTimeIn(
            Long stockId, CandleInterval interval, Collection<LocalDateTime> times
    );
}
