package trady.com.backend.global.response;

public record TradyResponse<T>(
        int code,
        String message,
        T data
) {
    public static <T> TradyResponse<T> success(String message, T data) {
        return new TradyResponse<>(0, message, data);
    }
}
