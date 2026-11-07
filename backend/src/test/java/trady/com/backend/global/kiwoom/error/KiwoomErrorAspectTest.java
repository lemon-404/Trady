package trady.com.backend.global.kiwoom.error;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KiwoomErrorAspectTest {

    @Mock private KiwoomErrorDispatcher dispatcher;
    @Mock private ProceedingJoinPoint joinPoint;
    @InjectMocks private KiwoomErrorAspect aspect;

    @BeforeEach
    void setUp() {
        // 성공·즉시 실패 테스트에서는 실행되지 않는 로그용 정보를 준비한다.
        Signature signature = mock(Signature.class);
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.toShortString()).thenReturn("Client.getWatchlist(..)");
    }

    @Test
    void 정상_호출은_한번만_실행하고_결과를_반환한다() throws Throwable {
        Object response = new Object();
        when(joinPoint.proceed()).thenReturn(response);

        assertThat(aspect.handle(joinPoint)).isSameAs(response);

        verify(joinPoint).proceed();
        verifyNoInteractions(dispatcher);
    }

    @Test
    void 타임아웃_후_성공하면_재시도를_종료한다() throws Throwable {
        var timeout = timeout();
        Object response = new Object();
        when(joinPoint.proceed()).thenThrow(timeout).thenReturn(response);

        assertThat(aspect.handle(joinPoint)).isSameAs(response);

        verify(joinPoint, times(2)).proceed();
        verify(dispatcher, times(1)).isRateLimited(timeout);
        verify(dispatcher, never()).handle(any());
    }

    @Test
    void 타임아웃이_계속되면_총_3회_시도하고_마지막_예외를_전파한다() throws Throwable {
        var first = timeout();
        var second = timeout();
        var last = timeout();
        when(joinPoint.proceed()).thenThrow(first, second, last);

        assertThatThrownBy(() -> aspect.handle(joinPoint)).isSameAs(last);

        verify(joinPoint, times(3)).proceed();
        verify(dispatcher).isRateLimited(first);
        verify(dispatcher).isRateLimited(second);
        verify(dispatcher).isRateLimited(last);
        verify(dispatcher).handle(last);
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void 연결_실패도_재시도하고_세번째_성공_결과를_반환한다() throws Throwable {
        var cause = new WebClientRequestException(
                new IOException("연결 실패"), HttpMethod.POST,
                URI.create("https://kiwoom.test/api/dostk/stkinfo"), new HttpHeaders());
        var exception = new KiwoomApiException(null, null, "통신 실패", cause);
        Object response = new Object();
        when(joinPoint.proceed()).thenThrow(exception).thenThrow(exception).thenReturn(response);

        assertThat(aspect.handle(joinPoint)).isSameAs(response);

        verify(joinPoint, times(3)).proceed();
        verify(dispatcher, times(2)).isRateLimited(exception);
        verify(dispatcher, never()).handle(any());
    }

    @Test
    void 인증_실패는_재시도하지_않고_처리후_예외를_전파한다() throws Throwable {
        var exception = new KiwoomApiException("3", "8005", "인증 실패", null);
        when(joinPoint.proceed()).thenThrow(exception);

        assertThatThrownBy(() -> aspect.handle(joinPoint)).isSameAs(exception);

        verify(joinPoint).proceed();
        verify(dispatcher).isRateLimited(exception);
        verify(dispatcher).handle(exception);
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    void 키움_API_예외가_아니면_재시도하거나_디스패처로_전달하지_않는다() throws Throwable {
        var exception = new IllegalStateException("내부 오류");
        when(joinPoint.proceed()).thenThrow(exception);

        assertThatThrownBy(() -> aspect.handle(joinPoint)).isSameAs(exception);

        verify(joinPoint).proceed();
        verifyNoInteractions(dispatcher);
    }

    @Test
    void 요청_제한_후_성공하면_재시도를_종료한다() throws Throwable {
        var exception = new KiwoomApiException("5", "1700", "요청 개수 초과", null);
        when(dispatcher.isRateLimited(exception)).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(exception).thenReturn("success");

        assertThat(aspect.handle(joinPoint)).isEqualTo("success");

        verify(joinPoint, times(2)).proceed();
        verify(dispatcher, never()).handle(any());
    }

    @Test
    void 요청_제한이_계속되면_3회_시도후_실패를_전달한다() throws Throwable {
        var exception = new KiwoomApiException("5", "1700", "요청 개수 초과", null);
        when(dispatcher.isRateLimited(exception)).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(exception);

        assertThatThrownBy(() -> aspect.handle(joinPoint)).isSameAs(exception);

        verify(joinPoint, times(3)).proceed();
        verify(dispatcher, times(3)).isRateLimited(exception);
        verify(dispatcher).handle(exception);
        verifyNoMoreInteractions(dispatcher);
    }

    private KiwoomApiException timeout() {
        return new KiwoomApiException(null, null, "응답 시간 초과", new TimeoutException());
    }
}
