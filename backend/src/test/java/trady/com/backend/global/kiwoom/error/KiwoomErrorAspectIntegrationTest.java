package trady.com.backend.global.kiwoom.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

// 애플리케이션 전체를 띄우지 않고 AOP에 필요한 Bean만 등록한다.
@SpringJUnitConfig(KiwoomErrorAspectIntegrationTest.Config.class)
class KiwoomErrorAspectIntegrationTest {

    @Autowired private TestClient client;
    @Autowired private CallTarget target;
    @Autowired private KiwoomErrorDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        reset(target, dispatcher);
    }

    @Test
    void 어노테이션이_붙은_Bean_호출은_프록시를_거쳐_재시도한다() {
        var exception = new KiwoomApiException(
                null, null, "응답 시간 초과", new TimeoutException());
        when(target.call("005930")).thenThrow(exception).thenReturn("success");

        assertThat(AopUtils.isAopProxy(client)).isTrue();
        assertThat(client.call("005930")).isEqualTo("success");

        // 재시도할 때도 동일한 인자로 호출되는지 확인한다.
        verify(target, times(2)).call("005930");
        verifyNoMoreInteractions(target);
        verify(dispatcher).isRateLimited(exception);
        verify(dispatcher, never()).handle(any());
    }

    @Test
    void 인증_실패는_프록시에서_디스패처로_전달하고_재시도하지_않는다() {
        var exception = new KiwoomApiException("3", "8005", "인증 실패", null);
        when(target.call("005930")).thenThrow(exception);

        assertThatThrownBy(() -> client.call("005930")).isSameAs(exception);

        verify(target).call("005930");
        verify(dispatcher).isRateLimited(exception);
        verify(dispatcher).handle(exception);
        verifyNoMoreInteractions(target, dispatcher);
    }

    @Test
    void 요청_제한_코드를_실제로_판별하고_프록시를_통해_재시도한다() {
        var exception = new KiwoomApiException(
                "5", "1700", "허용된 API 요청 개수를 초과하였습니다", null);

        // 판별 결과를 true로 고정하지 않고 실제 Dispatcher의 판별 로직을 실행한다.
        when(dispatcher.isRateLimited(exception)).thenCallRealMethod();
        when(target.call("005930")).thenThrow(exception).thenReturn("success");

        assertThat(AopUtils.isAopProxy(client)).isTrue();
        assertThat(client.call("005930")).isEqualTo("success");

        verify(dispatcher).isRateLimited(exception);
        verify(target, times(2)).call("005930");
        // 재시도에 성공했으므로 최종 실패 처리는 실행하지 않는다.
        verify(dispatcher, never()).handle(any());
        verifyNoMoreInteractions(target, dispatcher);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Config {
        @Bean
        KiwoomErrorDispatcher dispatcher() {
            return mock(KiwoomErrorDispatcher.class);
        }

        @Bean
        KiwoomErrorAspect aspect(KiwoomErrorDispatcher dispatcher) {
            return new KiwoomErrorAspect(dispatcher);
        }

        @Bean
        CallTarget target() {
            return mock(CallTarget.class);
        }

        @Bean
        TestClient client(CallTarget target) {
            return new TestClient(target);
        }
    }

    interface CallTarget {
        String call(String stockCode);
    }

    public static class TestClient {
        private final CallTarget target;

        public TestClient(CallTarget target) {
            this.target = target;
        }

        @HandleKiwoomError
        public String call(String stockCode) {
            return target.call(stockCode);
        }
    }
}
