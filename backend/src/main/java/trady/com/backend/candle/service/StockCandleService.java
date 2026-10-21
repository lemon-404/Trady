package trady.com.backend.candle.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import trady.com.backend.candle.repository.StockCandleRepository;
import trady.com.backend.entity.stock.Candle;
import trady.com.backend.entity.stock.Stock;
import trady.com.backend.global.kiwoom.candle.dto.KiwoomStockCandleResponse;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockCandleService {
    private final StockCandleRepository candleRepository;

    @Transactional
    public void savePage(Stock stock, String interval,
                         List<KiwoomStockCandleResponse.CandleItem> items) {

        List<Candle> incomingCandles = items.stream()
                .distinct()
                .map(item -> KiwoomStockCandleResponse.toResponseDto(item, stock, interval))
                .toList();

        if (incomingCandles.isEmpty()) return;


        List<LocalDateTime> times = incomingCandles.stream()
                .map(Candle::getTime)
                .toList();

        //중복 처리 는 validation, duplication, Outlier module을 같이 구현하는걸로
//        if (times.stream().distinct().count() != times.size()) {
//            throw new IllegalArgumentException("같은 봉 시각에 서로 다른 OHLCV가 있습니다.");
//        }


        List<Candle> existingCandles = candleRepository.findAllByStock_IdAndIntervalAndTimeIn(
                stock.getId(), interval, times);

        // 교집합: 기존 엔티티의 OHLCV만 변경한다. 커밋 시 변경 감지로 UPDATE된다.
        for (Candle existing : existingCandles) {
            incomingCandles.stream()
                    .filter(incoming -> incoming.getTime().equals(existing.getTime()))
                    .findFirst()
                    .filter(incoming -> !existing.hasSameOhlcv(incoming))
                    .ifPresent(existing::updateOhlcv);
        }

        // 차집합: 원본 목록에서 DB에 같은 시각이 없는 캔들만 INSERT한다.
        List<Candle> newCandles = incomingCandles.stream()
                .filter(incoming -> existingCandles.stream()
                        .noneMatch(existing -> existing.getTime().equals(incoming.getTime())))
                .toList();
        if (!newCandles.isEmpty()) {
            candleRepository.saveAll(newCandles);
        }
    }
}
