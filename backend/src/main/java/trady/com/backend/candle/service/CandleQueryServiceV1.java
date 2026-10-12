package trady.com.backend.candle.service;


import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import trady.com.backend.candle.dto.CandleContinuousQueryRequest;
import trady.com.backend.candle.dto.CandleContinuousQueryResponse;
import trady.com.backend.candle.repository.CandleQueryRepositoryV1;
import trady.com.backend.candle.entity.Candle;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;


/**
 * 차트 조회의 봉 주기별 페이지 크기 초안.
 *
 * <p>정규장 390분을 기준으로 한 페이지에 하루 분량을 반환한다.
 * 1분봉은 390개, 5분봉은 78개, 15분봉은 26개로 설정한다.
 *
 * <p>정규장 외 캔들이 포함되면 실제 조회 기간은 하루와 다를 수 있다.
 * 프론트엔드 구현 후 화면에 표시할 캔들 수와 Prefetch 동작을 확인하여 조정한다.
 */

/**
 * TODO sort를 JPQL에 하드코딩하는데 saveall로 통일하고 PageRequest에 Sort.by를 사용하게 변경
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandleQueryServiceV1 {

    private final CandleQueryRepositoryV1 repository;

    public CandleContinuousQueryResponse getInitialCandles(
            CandleContinuousQueryRequest request
    ) {
        int limit = getPageLimit(request.interval());

        List<Candle> candles = repository.findInitialCandles(
                request.stockCode(),
                request.interval(),
                request.cursor(),
                PageRequest.of(0, limit + 1)
        );

        return toResponse(candles, limit);
    }

    public CandleContinuousQueryResponse getContinuousCandles(
            CandleContinuousQueryRequest request
    ) {
        int limit = getPageLimit(request.interval());

        List<Candle> candles = repository.findContinuousCandles(
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