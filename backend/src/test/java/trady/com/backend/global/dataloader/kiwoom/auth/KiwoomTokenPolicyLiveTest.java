package trady.com.backend.global.dataloader.kiwoom.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import trady.com.backend.global.dataloader.kiwoom.auth.dto.issue.KiwoomTokenIssueResponse;
import trady.com.backend.global.dataloader.kiwoom.config.KiwoomProperties;
import trady.com.backend.global.dataloader.kiwoom.config.KiwoomWebClientConfig;
import trady.com.backend.global.dataloader.kiwoom.stock.client.KiwoomStockClient;
import trady.com.backend.global.dataloader.kiwoom.stock.dto.KiwoomStockResponse;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("kiwoom-live")
@EnabledIfEnvironmentVariable(named = "KIWOOM_AUTH_LIVE_TEST", matches = "true")
@SpringJUnitConfig(
        classes = KiwoomTokenPolicyLiveTest.TestConfig.class,
        initializers = ConfigDataApplicationContextInitializer.class
)
class KiwoomTokenPolicyLiveTest {

    private static final String STOCK_CODE = "005930";
    private static final DateTimeFormatter EXPIRES_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private KiwoomAuthClient authClient;

    @Autowired
    private KiwoomStockClient stockClient;

    @Autowired
    private KiwoomProperties properties;

    @Test
    @DisplayName("1. 발급받은 토큰으로 실제 종목 정보를 조회한다")
    @DisabledIfEnvironmentVariable(named = "KIWOOM_EXPIRY_LIVE_TEST", matches = "true")
    void issuedTokenCanQueryStock() {
        assertCredentialsConfigured();
        KiwoomTokenIssueResponse token = authClient.issueToken();
        try {
            assertThat(expiresAt(token.expiresDt()).isAfter(now()))
                    .as("발급 응답의 만료 시각이 현재보다 이후여야 합니다")
                    .isTrue();
            assertUsable(token.token());
        } finally {
            authClient.revokeToken(token.token());
        }
    }

    @Test
    @DisplayName("2. 짧은 간격으로 재요청하면 동일 토큰과 만료 시각을 반환한다")
    @DisabledIfEnvironmentVariable(named = "KIWOOM_EXPIRY_LIVE_TEST", matches = "true")
    void immediateReissueReturnsSameToken() {
        assertCredentialsConfigured();
        KiwoomTokenIssueResponse first = authClient.issueToken();
        String cleanupToken = first.token();
        try {
            // 다른 수집 프로세스가 없는 전용 세션에서 실행한다.
            KiwoomTokenIssueResponse second = authClient.issueToken();
            cleanupToken = second.token();
            // 실제 토큰 값이 Assertion 실패 메시지에 노출되지 않게 비교 결과만 검증한다.
            assertThat(first.token().equals(second.token()))
                    .as("연속 발급 요청의 토큰이 같아야 합니다")
                    .isTrue();
            assertThat(first.expiresDt().equals(second.expiresDt()))
                    .as("기존 토큰 반환 시 만료 시각이 유지되어야 합니다")
                    .isTrue();
            assertUsable(second.token());
        } finally {
            authClient.revokeToken(cleanupToken);
        }
    }

    @Test
    @DisplayName("5. 명시적으로 폐기한 토큰의 종목 조회는 인증 거절된다")
    @DisabledIfEnvironmentVariable(named = "KIWOOM_EXPIRY_LIVE_TEST", matches = "true")
    void revokedTokenCannotQueryStock() {
        assertCredentialsConfigured();
        KiwoomTokenIssueResponse token = authClient.issueToken();
        boolean revoked = false;
        try {
            assertUsable(token.token());
            authClient.revokeToken(token.token());
            revoked = true;
            assertAuthenticationRejected(token.token());
        } finally {
            if (!revoked) {
                authClient.revokeToken(token.token());
            }
        }
    }

    @Test
    @DisplayName("7. 자연 만료된 토큰의 종목 조회는 인증 거절된다")
    @EnabledIfEnvironmentVariable(named = "KIWOOM_EXPIRY_LIVE_TEST", matches = "true")
    void expiredTokenCannotQueryStock() {
        // 이 테스트만 단독 실행한다. 다른 테스트의 발급·폐기가 개입하면 자연 만료를 증명할 수 없다.
        String token = System.getenv("KIWOOM_EXPIRED_TOKEN");
        String expiry = System.getenv("KIWOOM_EXPIRED_TOKEN_EXPIRES_DT");
        assumeTrue(hasText(token) && hasText(expiry), "자연 만료된 토큰과 실제 만료 시각이 필요합니다");
        assertThat(!expiresAt(expiry).isAfter(now()))
                .as("입력한 토큰의 실제 만료 시각이 지나야 합니다")
                .isTrue();
        assertAuthenticationRejected(token);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    private void assertCredentialsConfigured() {
        assertThat(hasText(properties.appKey()) && hasText(properties.secretKey()))
                .as("키움 인증 설정이 필요합니다")
                .isTrue();
    }

    private LocalDateTime expiresAt(String value) {
        return LocalDateTime.parse(value, EXPIRES_FORMAT);
    }

    private void assertUsable(String token) {
        KiwoomStockResponse response;
        try {
            response = stockClient.getStockInfo(STOCK_CODE, token);
        } catch (RuntimeException exception) {
            throw new AssertionError("종목 조회 실패: " + exception.getClass().getSimpleName());
        }
        assertThat(Integer.valueOf(0).equals(response.returnCode()))
                .as("유효 토큰으로 종목정보 요청이 성공해야 합니다")
                .isTrue();
        assertThat(STOCK_CODE.equals(response.stockCode()))
                .as("요청한 종목 정보가 반환되어야 합니다")
                .isTrue();
    }

    private void assertAuthenticationRejected(String token) {
        boolean rejected;
        try {
            KiwoomStockResponse response = stockClient.getStockInfo(STOCK_CODE, token);
            String message = response.returnMsg() == null ? "" : response.returnMsg().toLowerCase(Locale.ROOT);
            boolean authenticationMessage = message.contains("토큰") || message.contains("token")
                    || message.contains("인증") || message.contains("authentication")
                    || message.contains("만료") || message.contains("expired");
            rejected = response.returnCode() != null && response.returnCode() != 0 && authenticationMessage;
        } catch (WebClientResponseException exception) {
            // 403은 IP·권한 제한일 수도 있으므로 토큰 거절로 단정하지 않는다.
            rejected = exception.getStatusCode().value() == 401;
        } catch (RuntimeException exception) {
            throw new AssertionError("종목 조회 실패: " + exception.getClass().getSimpleName());
        }
        assertThat(rejected)
                .as("인증 거절이어야 합니다. 일반 오류나 요청 제한은 만료·폐기 근거가 아닙니다")
                .isTrue();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Configuration(proxyBeanMethods = false)
    @Import({KiwoomWebClientConfig.class, KiwoomAuthClient.class, KiwoomStockClient.class})
    static class TestConfig {
        // Lifecycle, Provider, DB 및 애플리케이션 전체 스캔은 등록하지 않는다.
    }
}
