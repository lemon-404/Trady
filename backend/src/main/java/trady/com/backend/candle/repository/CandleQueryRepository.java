package trady.com.backend.candle.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import trady.com.backend.candle.entity.Candle;
import trady.com.backend.candle.domain.CandleInterval;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CandleQueryRepository extends JpaRepository<Candle, Long> {


    @Query("""
            SELECT c
            FROM Candle c
            WHERE c.stock.code = :stockCode
              AND c.interval = :interval
            ORDER BY c.time DESC
            """)
    List<Candle> findInitialCandles(
            @Param("stockCode") String stockCode,
            @Param("interval") CandleInterval interval,
            Pageable pageable
    );

    @Query("""
            SELECT c
            FROM Candle c
            WHERE c.stock.code = :stockCode
              AND c.interval = :interval
              AND c.time < :cursor
            ORDER BY c.time DESC
            """)
    List<Candle> findContinuousCandles(
            @Param("stockCode") String stockCode,
            @Param("interval") CandleInterval interval,
            @Param("cursor") LocalDateTime cursor,
            Pageable pageable
    );
}
