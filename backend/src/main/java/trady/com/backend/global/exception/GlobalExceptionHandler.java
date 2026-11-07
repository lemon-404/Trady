package trady.com.backend.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import trady.com.backend.global.response.ApiErrorResponse;
import trady.com.backend.global.response.FieldErrorDetail;
import trady.com.backend.global.response.TradyErrorResponse;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException exception
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                "404",
                "요청한 리소스를 찾을 수 없습니다.",
                List.of()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    //@Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailure(
            MethodArgumentNotValidException exception
    ) {
        List<ApiErrorResponse.FieldErrorDetail> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiErrorResponse.FieldErrorDetail(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        ApiErrorResponse response = new ApiErrorResponse(
                "400",
                "요청값을 확인해 주세요.",
                errors
        );

        return ResponseEntity.badRequest().body(response);
    }
    //@RequestBody
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableRequestBody(
            HttpMessageNotReadableException exception
    ) {
        ApiErrorResponse response = new ApiErrorResponse(
                "400",
                "요청값을 확인해 주세요.",
                List.of()
        );

        return ResponseEntity.badRequest().body(response);
    }

    //DB ERROR
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<TradyErrorResponse> handleDataAccessException(
            DataAccessException exception
    ) {
        log.error("데이터베이스 처리 중 오류가 발생했습니다.", exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new TradyErrorResponse(
                        9000,
                        "서버 내부 오류가 발생했습니다.",
                        List.of(new FieldErrorDetail(
                                null,
                                "데이터베이스 처리 중 오류가 발생했습니다."
                        ))
                ));
    }
}
