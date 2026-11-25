package trady.com.backend.candle.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import trady.com.backend.stock.repository.StockQueryRepository;
import trady.com.backend.candle.config.CandleQueryProperties;
import trady.com.backend.candle.domain.CandleInterval;
import trady.com.backend.candle.exception.InvalidCandleQueryException;
import trady.com.backend.global.exception.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@EnableConfigurationProperties(CandleQueryProperties.class)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandleQueryService {
    private final CandleRepository candleRepository;
    private final CandleQueryRepository candleQueryRepository;
    private final StockQueryRepository stocks;
    private final CandleQueryProperties properties;

    @Transactional
    public void savePage(Stock stock, CandleInterval interval,
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
        CandleInterval interval = parseInterval(request.interval());
        requireStock(request.stockCode());
        int limit = properties.pageSize();

        List<Candle> candles =  candleQueryRepository.findInitialCandles(
                request.stockCode(),
                interval,
                PageRequest.of(0, limit + 1)
        );

        return toResponse(candles, limit);
    }

    public CandleContinuousQueryResponse getContinuousCandles(
            CandleContinuousQueryRequest request
    ) {
        CandleInterval interval = parseInterval(request.interval());
        if (request.cursor() == null) {
            throw new InvalidCandleQueryException("cursor", "올바르지 않은 조회 기준 시각입니다.");
        }
        requireStock(request.stockCode());
        int limit = properties.pageSize();
        List<Candle> candles = candleQueryRepository.findContinuousCandles(
                request.stockCode(),
                interval,
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

        // Repository의 시간 내림차순을 유지하여 최신 캔들부터 반환한다.
        List<CandleContinuousQueryResponse.CandleItem> items = page.stream()
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

    private void requireStock(String stockCode) {
        stocks.findByCode(stockCode).orElseThrow(() -> new ResourceNotFoundException("요청한 종목 정보가 존재하지 않습니다."));
    }

    private CandleInterval parseInterval(String code) {
        try {
            return CandleInterval.fromCode(code);
        } catch (IllegalArgumentException exception) {
            throw new InvalidCandleQueryException("interval", "지원하지 않는 봉 단위입니다.");
        }
    }
}
