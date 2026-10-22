package trady.com.backend.global.response;


import java.util.List;

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
