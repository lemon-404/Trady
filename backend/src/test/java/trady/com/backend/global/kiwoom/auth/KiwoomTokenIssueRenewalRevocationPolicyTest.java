package trady.com.backend.global.kiwoom.auth;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import trady.com.backend.global.kiwoom.auth.dto.issue.KiwoomTokenIssueResponse;
import trady.com.backend.global.kiwoom.config.KiwoomProperties;
import trady.com.backend.global.kiwoom.config.KiwoomWebClientConfig;
import trady.com.backend.global.kiwoom.stock.client.KiwoomStockClient;
import trady.com.backend.global.kiwoom.stock.dto.KiwoomStockInfoResponse;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 키움 토큰 발급·갱신·폐기 정책을 검증한다.
 *
 * <p>참고 문서:
 * docs/work-plan/backend/2026-10-10 키움 API 토큰 관리 구조/토큰 발급 갱신 폐기 정책 검증.md
 *
 * <p>실행 방법:
 * 토큰 정책 확인이 필요할 때 개별 테스트 메서드를 실행하여 확인한다.
 * {@code kiwoom-token} 태그는 Gradle 기본 test 작업에서 제외된다.
 *
 * <p>주의 사항:
 * 실제 키움 모의투자 API를 호출하므로 같은 인증정보를 사용하는
 * 다른 프로세스의 호출을 중단한 상태에서 실행한다.
 */

@Tag("kiwoom-token")
@DisplayName("키움 토큰 정책 검증")
@Slf4j
@SpringJUnitConfig(
        classes = KiwoomTokenIssueRenewalRevocationPolicyTest.TestConfig.class,
        initializers = ConfigDataApplicationContextInitializer.class
)
class KiwoomTokenIssueRenewalRevocationPolicyTest {

    private static final String STOCK_CODE = "005930";
    private static final DateTimeFormatter EXPIRES_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private KiwoomAuthClient authClient;

    @Autowired
    private KiwoomStockClient stockClient;

    @Autowired
    private KiwoomTokenProvider tokenProvider;

    @Autowired
    private KiwoomProperties properties;

    private KiwoomTokenIssueResponse kiwoomTokenIssueResponse;
    private String token;


    @BeforeEach
    void setUp() {
        assertCredentialsConfigured();
        kiwoomTokenIssueResponse = authClient.issueToken();
        token = kiwoomTokenIssueResponse.token();
        when(tokenProvider.getToken()).thenReturn(kiwoomTokenIssueResponse.token());
    }

    @AfterEach
    void tearDown() {
        if (token != null) {
            authClient.revokeToken(token);
        }
    }

    @Test
    @DisplayName("1. 발급받은 토큰으로 실제 종목 정보를 조회한다")
    void issuedTokenCanQueryStock() {
        assertThat(expiresAt(kiwoomTokenIssueResponse.expiresDt()).isAfter(now()))
                .as("발급 응답의 만료 시각이 현재보다 이후여야 합니다")
                .isTrue();
        assertUsable();
    }

    @Test
    @DisplayName("2. 짧은 간격으로 재요청하면 동일 토큰과 만료 시각을 반환한다")
    void immediateReissueReturnsSameToken() {
        // 다른 수집 프로세스가 없는 전용 세션에서 실행한다.
        KiwoomTokenIssueResponse second = authClient.issueToken();
        token = second.token();
        when(tokenProvider.getToken()).thenReturn(second.token());
        // 실제 토큰 값이 Assertion 실패 메시지에 노출되지 않게 비교 결과만 검증한다.
        assertThat(kiwoomTokenIssueResponse.token().equals(second.token()))
                .as("연속 발급 요청의 토큰이 같아야 합니다")
                .isTrue();
        assertThat(kiwoomTokenIssueResponse.expiresDt().equals(second.expiresDt()))
                .as("기존 토큰 반환 시 만료 시각이 유지되어야 합니다")
                .isTrue();
        assertUsable();
    }

    @Test
    @DisplayName("5. 명시적으로 폐기한 토큰의 종목 조회는 인증 거절된다")
    void revokedTokenCannotQueryStock() {
        assertUsable();
        authClient.revokeToken(kiwoomTokenIssueResponse.token());
        // 폐기 전과 같은 토큰을 제공하여 실제 키움의 인증 거절을 확인한다.
        assertAuthenticationRejected();
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

    private void assertUsable() {
        KiwoomStockInfoResponse response;
        try {
            response = stockClient.getStockInfo(STOCK_CODE);
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

    private void assertAuthenticationRejected() {

        KiwoomStockInfoResponse response = stockClient.getStockInfo(STOCK_CODE);
        String message = response.returnMsg() == null ? "" : response.returnMsg().toLowerCase(Locale.ROOT);

//        log.info(response.toString());

        boolean rejected = response.returnCode() != null && response.returnCode() != 0 && message.contains("8005");
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
        // Lifecycle과 DB는 등록하지 않고, Provider는 검증할 토큰만 반환한다.
        @Bean
        KiwoomTokenProvider tokenProvider() {
            return mock(KiwoomTokenProvider.class);
        }
    }
}
