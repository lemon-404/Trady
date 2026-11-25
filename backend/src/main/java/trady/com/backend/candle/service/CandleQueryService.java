package trady.com.backend.candle.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import trady.com.backend.candle.dto.CandleContinuousQueryRequest;
import trady.com.backend.candle.dto.CandleContinuousQueryResponse;
import trady.com.backend.candle.entity.Candle;
import trady.com.backend.candle.repository.CandleQueryRepository;
import trady.com.backend.candle.repository.CandleRepository;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleResponse;
import trady.com.backend.stock.entity.Stock;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandleQueryService {
    private final CandleRepository candleRepository;
    private final CandleQueryRepository candleQueryRepository;

    @Transactional
    public void savePage(Stock stock, String interval,
                         List<CandleResponse.CandleItem> items) {

        List<Candle> incomingCandles = items.stream()
                .distinct()
                .map(item -> CandleResponse.toResponseDto(item, stock, interval))
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

    public CandleContinuousQueryResponse getCandles(
            CandleContinuousQueryRequest request
    ) {
        int limit = getPageLimit(request.interval());

        List<Candle> candles =  candleQueryRepository.findInitialCandles(
                request.stockCode(),
                request.interval(),
                PageRequest.of(0, limit + 1)
        );

        return toResponse(candles, limit);
    }

    public CandleContinuousQueryResponse getContinuousCandles(
            CandleContinuousQueryRequest request
    ) {
        int limit = getPageLimit(request.interval());
        List<Candle> candles = candleQueryRepository.findContinuousCandles(
                request.stockCode(),
                request.interval(),
                request.cursor(),
                PageRequest.of(0, limit + 1)
        );
        return toResponse(candles, limit);
    }

    private CandleContinuousQueryResponse toResponse(List<Candle> fetched, int limit) {
        boolean hasNext = fetched.size() > limit;

        List<Candle> page = fetched.subList(
                0, Math.min(fetched.size(), limit)
        );

        LocalDateTime nextCursor = hasNext
                ? page.get(page.size() - 1).getTime()
                : null;

        // 차트에 전달할 목록은 시간 오름차순으로 변환.
        List<CandleContinuousQueryResponse.CandleItem> items = page.stream()
                .sorted(Comparator.comparing(Candle::getTime))
                .map(candle -> new CandleContinuousQueryResponse.CandleItem(
                        candle.getTime(),
                        candle.getOpenPrice(),
                        candle.getHighPrice(),
                        candle.getLowPrice(),
                        candle.getClosePrice(),
                        candle.getVolume()
                ))
                .toList();

        return new CandleContinuousQueryResponse(
                nextCursor,
                hasNext ? "Y" : "N",
                items
        );
    }

    private int getPageLimit(String interval) {
        return switch (interval) {
            case "1" -> 390;
            case "5" -> 78;
            case "15" -> 26;
            default -> throw new IllegalArgumentException("지원하지 않는 봉 주기입니다.");
        };
    }
}
