package trady.com.backend.global.response;


import java.util.List;

/**
 * 레거시 error response 나중에 해당 영역 코딩시 삭제하고 /global/response/TradyErrorResponse 사용
 * @param code
 * @param message
 * @param errors
 */
public record ApiErrorResponse(
        String code,
        String message,
        List<FieldErrorDetail> errors
) {
    public record FieldErrorDetail(
            String field,
            String message
    ) {
    }
}
