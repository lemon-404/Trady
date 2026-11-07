package trady.com.backend.global.kiwoom.error;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.concurrent.TimeoutException;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class KiwoomErrorAspect {
    private final KiwoomErrorDispatcher dispatcher;

    @Around(
            "@annotation(trady.com.backend.global.kiwoom.error.HandleKiwoomError)"
    )
    public Object handle(ProceedingJoinPoint joinPoint) throws Throwable {
        // 최초 요청을 포함해 최대 3회 시도한다.
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // 대상 메서드를 실행한다.
                // 성공하면 결과를 반환하고 재시도 처리를 종료한다.
                return joinPoint.proceed();

            } catch (KiwoomApiException e) {
                // 요청 제한은 Dispatcher에서 키움 오류코드 또는 HTTP 429로 판별한다.
                boolean rateLimited = dispatcher.isRateLimited(e);

                // 요청 제한, 타임아웃 또는 요청 통신 오류인 경우 재시도한다.
                boolean retryable =
                        rateLimited
                                || e.getCause() instanceof TimeoutException
                                || e.getCause() instanceof WebClientRequestException;

                // 재시도 대상이 아니거나 최대 횟수를 소진했다면,
                // 코드별 오류 처리를 수행하고 호출한 쪽으로 예외를 전달한다.
                if (!retryable || attempt == maxAttempts) {
                    dispatcher.handle(e);
                    throw e;
                }

                // 1차 실패 후 1초, 2차 실패 후 2초 대기한다.
                long delayMillis = attempt * 1_000L;

                log.warn(
                        "키움 API 재시도 예정: method={}, 요청제한={}, 오류코드={}, 실패시도={}, 최대시도={}, 대기={}ms",
                        joinPoint.getSignature().toShortString(),
                        rateLimited,
                        e.getErrorCode(),
                        attempt,
                        maxAttempts,
                        delayMillis
                );

                try {
                    // 대기 후 다음 반복에서 같은 메서드를 같은 인자로 다시 실행한다.
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    // 대기 중 중단 요청을 받으면 중단 상태를 복원하고 작업을 종료한다.
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }

        // 각 시도는 성공 시 return, 최종 실패 시 throw로 종료되므로
        // 정상적인 실행 흐름에서는 도달하지 않는다.
        throw new IllegalStateException("도달할 수 없는 재시도 상태");
    }
}
