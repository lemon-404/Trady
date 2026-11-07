package trady.com.backend.global.response;

public record FieldErrorDetail(
        String field,
        String message
) {}
