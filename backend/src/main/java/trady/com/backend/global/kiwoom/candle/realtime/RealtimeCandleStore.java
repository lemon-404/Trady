package trady.com.backend.global.kiwoom.candle.realtime;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import trady.com.backend.candle.repository.CandleQueryRepository;
import trady.com.backend.candle.service.CandleQueryService;
import trady.com.backend.global.kiwoom.candle.historical.dto.CandleResponse.CandleItem;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 반드시 DB 실행기에서 호출한다. 같은 키의 동시 실행 방지는 Dispatcher가 담당한다. */
@Service
public class RealtimeCandleStore {
    private final CandleQueryRepository repository;
    private final CandleQueryService saveService;
    private final RealtimeCandleProcessor processor;
    private final Map<Key, Optional<LocalDateTime>> appliedTimes = new ConcurrentHashMap<>();

    public RealtimeCandleStore(CandleQueryRepository repository, CandleQueryService saveService, RealtimeCandleProcessor processor) {
        this.repository = Objects.requireNonNull(repository);
        this.saveService = Objects.requireNonNull(saveService);
        this.processor = Objects.requireNonNull(processor);
    }

    public int save(RealtimeCandleDispatcher.Task task, List<CandleItem> items, boolean hasMore) {
        var key = new Key(task.stock().getCode(), task.scope());
        var lastAppliedTime = appliedTimes.computeIfAbsent(key, ignored -> loadLastAppliedTime(task)).orElse(null);

        // 전체 응답을 먼저 검증한다. 제외된 시각을 건너뛰고 체크포인트를 전진시키지 않는다.
        var valid = processor.process(items, task.scope(), LocalDateTime.MIN);
        var receivedTimes = new HashSet<String>();
        for (var item : items) receivedTimes.add(item == null ? null : item.time());
        var validTimes = new HashSet<String>();
        for (var item : valid) validTimes.add(item.time());
        if (!validTimes.containsAll(receivedTimes)) {
            throw new IllegalStateException("유효하지 않거나 충돌한 봉이 있어 응답 저장을 보류합니다.");
        }
        if (valid.isEmpty()) return 0;

        var oldestTime = processor.parseTime(valid.get(0).time(), task.scope());
        if (lastAppliedTime != null && hasMore && oldestTime.isAfter(lastAppliedTime)) {
            throw new IllegalStateException("첫 페이지로 누락 구간을 덮을 수 없습니다. 연속 조회가 필요합니다.");
        }

        var selected = processor.process(valid, task.scope(), lastAppliedTime);
        if (selected.isEmpty()) return 0;
        var latestTime = processor.parseTime(selected.get(selected.size() - 1).time(), task.scope());

        // Spring 프록시의 savePage()가 반환된 시점에는 커밋까지 완료된다.
        saveService.savePage(task.stock(), task.scope().interval(), selected);

        if (lastAppliedTime == null || latestTime.isAfter(lastAppliedTime)) {
            appliedTimes.put(key, Optional.of(latestTime));
        }
        return selected.size();
    }

    private Optional<LocalDateTime> loadLastAppliedTime(RealtimeCandleDispatcher.Task task) {
        return repository.findInitialCandles(task.stock().getCode(), task.scope().interval(), PageRequest.of(0, 1))
                .stream().findFirst().map(candle -> candle.getTime());
    }

    private record Key(String stockCode, RealtimeCandleScope scope) { }
}
