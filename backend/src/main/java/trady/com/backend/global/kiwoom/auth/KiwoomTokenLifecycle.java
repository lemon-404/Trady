package trady.com.backend.global.kiwoom.auth;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(0)
@Slf4j
@Component
@RequiredArgsConstructor
public class KiwoomTokenLifecycle implements ApplicationRunner {

    private final KiwoomTokenProvider tokenProvider;

    @Override
    public void run(ApplicationArguments args) {
        // 초기화 실패는 전파하여 토큰 없이 준비 완료 상태가 되지 않도록 한다.
        tokenProvider.initialize();
        log.info("키움 토큰 초기화 완료");
    }

    @PreDestroy
    public void shutdown() {
        try {
            tokenProvider.revokeToken();
        } catch (RuntimeException exception) {
            // 외부 응답이 포함될 수 있는 예외 메시지와 토큰은 출력하지 않는다.
            log.warn("키움 토큰 폐기 실패. 원격 토큰이 유효할 수 있습니다. 오류 유형: {}",
                    exception.getClass().getSimpleName());
        }
        log.info("키움 토큰 폐기 완료");
    }
}
