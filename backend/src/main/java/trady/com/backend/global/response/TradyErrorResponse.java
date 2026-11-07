package trady.com.backend.global.response;

import java.util.List;

public record TradyErrorResponse(
        int code,
        String message,
        List<FieldErrorDetail> errors
) {}
