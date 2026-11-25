package trady.com.backend.global.kiwoom.candle.realtime;

import trady.com.backend.stock.entity.Stock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 종목·봉 주기별로 실행할 작업을 선택한다. HTTP 요청과 저장은 호출자가 담당한다. */
public class RealtimeCandleDispatcher {
    private final Map<Key, State> states = new LinkedHashMap<>();
    private final RealtimeCandleRateLimiter rateLimiter;
    private boolean closing;
    private boolean stopped;

    public RealtimeCandleDispatcher(List<Stock> stocks, RealtimeCandleRateLimiter rateLimiter, Instant startAt) {
        if (stocks == null || stocks.isEmpty() || stocks.size() > 10) {
            throw new IllegalArgumentException("수집 대상은 1~10종목이어야 합니다.");
        }
        if (stocks.stream().anyMatch(stock -> stock == null || stock.getCode() == null || stock.getCode().isBlank())
                || stocks.stream().map(Stock::getCode).distinct().count() != stocks.size()) {
            throw new IllegalArgumentException("유효하고 중복되지 않은 종목 코드가 필요합니다.");
        }
        this.rateLimiter = Objects.requireNonNull(rateLimiter);
        Objects.requireNonNull(startAt);

        for (var scope : RealtimeCandleScope.values()) {
            for (int index = 0; index < stocks.size(); index++) {
                var task = new Task(stocks.get(index), scope);
                // 같은 주기의 종목들이 한 시점에 몰리지 않도록 최초 조회 시각을 분산한다.
                var offset = scope.refresh().dividedBy(stocks.size()).multipliedBy(index);
                states.put(key(task), new State(task, startAt.plus(offset)));
            }
        }
    }

    /**
     * 실행 가능한 작업을 예약하고 반환한다. 반환된 작업은 호출 예산을 이미 소비했으므로 즉시 실행한다.
     * 예산이 없거나 처리 중인 작업은 상태를 바꾸지 않고 다음 회차에 다시 확인한다.
     */
    public synchronized List<Task> dispatch(Instant now) {
        Objects.requireNonNull(now);
        if (stopped) return List.of();
        var candidates = states.values().stream()
                .filter(state -> !state.inFlight)
                .filter(state -> closing ? !state.finalComplete : !state.nextDueAt.isAfter(now))
                .sorted(Comparator.comparing((State state) -> state.nextDueAt)
                        .thenComparing(state -> state.task.scope()))
                .toList();

        var selected = new ArrayList<Task>();
        for (var state : candidates) {
            if (!rateLimiter.tryAcquire(state.task.scope().tr())) {
                continue;
            }
            state.inFlight = true;
            state.finalInFlight = closing;
            // 과거에 밀린 횟수를 재생하지 않고, 실제 선택한 시각부터 다음 조회 간격을 계산한다.
            state.nextDueAt = now.plus(state.task.scope().refresh());
            selected.add(state.task);
        }
        return List.copyOf(selected);
    }

    /** 성공·실패 모두 요청과 저장 작업이 실제로 종료된 후 호출한다. */
    public synchronized void complete(Task task) {
        var state = states.get(key(task));
        if (state == null) {
            throw new IllegalArgumentException("등록되지 않은 수집 작업입니다.");
        }
        state.inFlight = false;
        if (state.finalInFlight) {
            state.finalComplete = true;
            state.finalInFlight = false;
        }
    }

    /** 진행 중인 정기 작업이 끝나면 각 키를 한 번 더 조회한다. 반복 호출해도 완료 상태를 초기화하지 않는다. */
    public synchronized void requestFinalCollection() {
        closing = true;
    }

    /** 마지막 조회는 성공·실패 모두 한 번의 시도로 종료하며 재시도하지 않는다. */
    public synchronized boolean isFinished() {
        return closing && states.values().stream().allMatch(state -> state.finalComplete);
    }

    /** 이미 실행 중인 저장의 상태는 유지하고 신규 작업 선택만 차단한다. */
    public synchronized void stop() {
        stopped = true;
    }

    private static Key key(Task task) {
        Objects.requireNonNull(task);
        return new Key(task.stock().getCode(), task.scope());
    }

    public record Task(Stock stock, RealtimeCandleScope scope) { }

    private record Key(String stockCode, RealtimeCandleScope scope) { }

    private static class State {
        final Task task;
        Instant nextDueAt;
        boolean inFlight;
        boolean finalInFlight;
        boolean finalComplete;

        State(Task task, Instant nextDueAt) {
            this.task = task;
            this.nextDueAt = nextDueAt;
        }
    }
}
