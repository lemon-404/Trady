package trady.com.backend.global.dataloader.kiwoom.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class KiwoomTokenLifecycleTest {

    @Test
    @DisplayName("부팅 시 토큰을 초기화하고 컨텍스트 종료 시 폐기한다")
    void initializesOnStartupAndRevokesOnShutdown() {
        KiwoomTokenProvider provider = mock(KiwoomTokenProvider.class);

        try (ConfigurableApplicationContext context = application(provider).run()) {
            assertThat(context.isActive()).isTrue();
            verify(provider).initialize();
            verify(provider, never()).revokeCurrentToken();
        }

        var order = inOrder(provider);
        order.verify(provider).initialize();
        order.verify(provider).revokeCurrentToken();
        order.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("초기화 실패를 부팅 실패로 전달한다")
    void failsStartupWhenInitializationFails() {
        KiwoomTokenProvider provider = mock(KiwoomTokenProvider.class);
        IllegalStateException failure = new IllegalStateException("token initialization failed");
        doThrow(failure).when(provider).initialize();

        assertThatThrownBy(() -> {
            try (var context = application(provider).run()) {
                // 정상 부팅되면 테스트가 실패해야 한다.
            }
        }).isSameAs(failure);

        verify(provider).revokeCurrentToken();
    }

    @Test
    @DisplayName("폐기 실패를 기록하고 민감한 예외 메시지 없이 종료한다")
    void logsRevocationFailureWithoutLeakingResponse(CapturedOutput output) {
        KiwoomTokenProvider provider = mock(KiwoomTokenProvider.class);
        doThrow(new IllegalStateException("sensitive-token-response"))
                .when(provider).revokeCurrentToken();
        ConfigurableApplicationContext context = application(provider).run();

        assertThatCode(context::close).doesNotThrowAnyException();

        verify(provider).revokeCurrentToken();
        assertThat(context.isActive()).isFalse();
        assertThat(output.getAll())
                .contains("키움 토큰 폐기 실패")
                .contains("IllegalStateException")
                .doesNotContain("sensitive-token-response");
    }

    private SpringApplication application(KiwoomTokenProvider provider) {
        SpringApplication application = new SpringApplication(TestConfig.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        application.addInitializers(context ->
                context.getBeanFactory().registerSingleton("tokenProvider", provider));
        return application;
    }

    @Configuration(proxyBeanMethods = false)
    @Import(KiwoomTokenLifecycle.class)
    static class TestConfig {
    }
}
