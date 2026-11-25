package trady.com.backend.candle.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.core.JacksonException;
import trady.com.backend.candle.exception.InvalidCandleQueryException;
import trady.com.backend.global.exception.ResourceNotFoundException;
import trady.com.backend.global.response.FieldErrorDetail;
import trady.com.backend.global.response.TradyErrorResponse;

import java.util.List;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CandleController.class)
public class CandleExceptionHandler {
    @ExceptionHandler(InvalidCandleQueryException.class)
    public ResponseEntity<TradyErrorResponse> handleInvalidQuery(InvalidCandleQueryException exception) {
        return badRequest(List.of(new FieldErrorDetail(exception.getField(), exception.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<TradyErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        return badRequest(exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage())).toList());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<TradyErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof JacksonException jackson && jackson.getPath().stream()
                    .anyMatch(reference -> "cursor".equals(reference.getPropertyName()))) {
                return badRequest(List.of(new FieldErrorDetail("cursor", "올바르지 않은 조회 기준 시각입니다.")));
            }
        }
        return badRequest(List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<TradyErrorResponse> handleMissingStock(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new TradyErrorResponse(
                3000, "종목 정보를 찾을 수 없습니다.",
                List.of(new FieldErrorDetail(null, "요청한 종목 정보가 존재하지 않습니다."))));
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    public ResponseEntity<TradyErrorResponse> handleDatabaseFailure(RuntimeException exception) {
        log.error("차트 데이터 조회 중 오류가 발생했습니다.", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new TradyErrorResponse(
                9000, "서버 내부 오류가 발생했습니다.",
                List.of(new FieldErrorDetail(null, "차트 데이터 조회 중 오류가 발생했습니다."))));
    }

    private ResponseEntity<TradyErrorResponse> badRequest(List<FieldErrorDetail> errors) {
        return ResponseEntity.badRequest().body(new TradyErrorResponse(1000, "올바르지 않은 요청입니다.", errors));
    }
}
