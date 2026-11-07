package trady.com.backend.global.kiwoom.error;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Component
@Slf4j
public class KiwoomErrorDispatcher {

    private final Map<String, Consumer<KiwoomApiException>> handlers =
            Map.of(
                    "8005", this::handleAuthenticationFailure
            );

    public void handle(KiwoomApiException e) {

        if (isRateLimited(e)) {
            handleRateLimitFailure(e);
            return;
        }

        Consumer<KiwoomApiException> handler =
                e.getErrorCode() == null
                        ? this::handleUnknownFailure
                        : handlers.getOrDefault(
                        e.getErrorCode(),
                        this::handleUnknownFailure
                );

        handler.accept(e);
    }


    private void handleAuthenticationFailure(KiwoomApiException e) {
        log.error("키움 API 인증 실패: {}", e.getMessage(), e);
    }

    private void handleUnknownFailure(KiwoomApiException e) {
        log.error("키움 API 호출 실패: 반환코드={}, 오류코드={}",
                e.getReturnCode(), e.getErrorCode(), e);
    }
    private void handleRateLimitFailure(KiwoomApiException e) {
        log.error("키움 API 요청 제한 재시도 소진: 반환코드={}, 오류코드={}",
                e.getReturnCode(), e.getErrorCode(), e);
    }

    public boolean isRateLimited(KiwoomApiException e) {
        String code = e.getErrorCode();

        boolean kiwoomLimit = code != null
                && Set.of("1700", "1701", "1702").contains(code);

        boolean httpLimit =
                e.getCause() instanceof WebClientResponseException cause
                        && cause.getStatusCode().value() == 429;

        return kiwoomLimit || httpLimit;
    }
}

