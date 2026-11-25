package trady.com.backend.global.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import trady.com.backend.global.response.FieldErrorDetail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void resourceNotFoundReturnsUnifiedErrorResponse() {
        var response = handler.handleResourceNotFound(new ResourceNotFoundException("종목 없음"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(3000);
        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    void validationFailurePreservesFieldDetails() {
        var binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "interval", "지원하지 않는 봉 단위입니다."));
        var exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(binding);

        var response = handler.handleValidationFailure(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(1000);
        assertThat(response.getBody().errors()).containsExactly(
                new FieldErrorDetail("interval", "지원하지 않는 봉 단위입니다."));
    }

    @Test
    void unreadableBodyReturnsUnifiedBadRequest() {
        var response = handler.handleUnreadableRequestBody(mock(HttpMessageNotReadableException.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(1000);
        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    void databaseFailureReturnsUnifiedServerErrorWithoutInternalMessage() {
        var response = handler.handleDataAccessException(
                new DataAccessResourceFailureException("internal database details"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo(9000);
        assertThat(response.getBody().message()).isEqualTo("서버 내부 오류가 발생했습니다.");
        assertThat(response.getBody().errors()).containsExactly(
                new FieldErrorDetail(null, "데이터베이스 처리 중 오류가 발생했습니다."));
    }
}
