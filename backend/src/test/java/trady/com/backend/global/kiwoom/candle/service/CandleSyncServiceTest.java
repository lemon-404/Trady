package trady.com.backend.global.kiwoom.candle.service;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import trady.com.backend.stock.entity.Stock;
import trady.com.backend.stock.repository.StockQueryRepository;


import java.time.LocalDateTime;


import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@SpringBootTest(properties = "candle.sync-setup=false")
class CandleSyncServiceTest {
    @Autowired
    private CandleSyncService candleSyncService;
    @Autowired
    private StockQueryRepository stockQueryRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void 월봉_수집_소요시간_측정() {
        Stock stock = stockQueryRepository.findByCode("005930")
                .orElseThrow(() -> new IllegalStateException("DB에 종목 005930을 먼저 등록하세요."));
        log.info("주식 코드: {}, 주식 명: {}", stock.getCode(), stock.getName());
        LocalDateTime startedAt = LocalDateTime.now();
        long startedNanos = System.nanoTime();
        System.out.printf("연봉 수집 시작: %s, 종목: %s%n", startedAt, stock.getCode());
        try {
            // 서비스의 RateLimiter와 next-key 연속조회를 그대로 사용한다.
//            ReflectionTestUtils.invokeMethod(candleSyncService,
//                    "syncCandlesForInterval", "ka10082", "1w", stock);
            ReflectionTestUtils.invokeMethod(
                    candleSyncService,
                    "syncCandlesForInterval",
                    "ka10082",
                    "1w",
                    stock
            );



        } finally {
            double seconds = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
            System.out.printf("시작: %s%n종료: %s%n총 소요 시간: %.3f초%n",
                    startedAt, LocalDateTime.now(), seconds);
        }

        // 검증 시간은 측정에서 제외한다. 기존 데이터 갱신도 정상이다.
        Long count = entityManager.createQuery(
                "select count(c) from Candle c where c.stock.id = :id and c.interval = :interval", Long.class)
                .setParameter("id", stock.getId())
                .setParameter("interval", "1w")
                .getSingleResult();
        assertTrue(count > 0, () -> "연봉 저장 결과가 없습니다: " + stock.getCode());
    }
}