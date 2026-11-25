package trady.com.backend.global.kiwoom.candle.realtime;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** 한 애플리케이션에서 동일 인증 정보를 사용하는 실시간 요청의 TR별 호출 예산. */
@Component
public class RealtimeCandleRateLimiter {
    private static final int MAX_REQUESTS = 5;
    private static final long WINDOW_NANOS = Duration.ofSeconds(1).toNanos();

    private final Map<String, Deque<Long>> requestTimesByTr = new HashMap<>();
    private final LongSupplier nanoTime;

    public RealtimeCandleRateLimiter() {
        this(System::nanoTime);
    }

    // 테스트에서 대기 없이 시간 경계를 확인하기 위한 생성자.
    RealtimeCandleRateLimiter(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    /** 허용 시 호출 예산을 즉시 소비한다. HTTP 실패 시에도 소비한 예산을 돌려주지 않는다. */
    public synchronized boolean tryAcquire(String tr) {
        if (tr == null || tr.isBlank()) {
            throw new IllegalArgumentException("TR ID가 필요합니다.");
        }

        long now = nanoTime.getAsLong();
        Deque<Long> requestTimes = requestTimesByTr.computeIfAbsent(tr, ignored -> new ArrayDeque<>());

        // 1초가 지난 호출 기록만 제거한다. 매초 일괄 초기화하지 않는다.
        while (!requestTimes.isEmpty() && now - requestTimes.peekFirst() >= WINDOW_NANOS) {
            requestTimes.removeFirst();
        }

        if (requestTimes.size() >= MAX_REQUESTS) {
            return false;
        }

        requestTimes.addLast(now);
        return true;
    }
}
