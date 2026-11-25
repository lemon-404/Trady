package trady.com.backend.candle.exception;

import lombok.Getter;

@Getter
public class InvalidCandleQueryException extends RuntimeException {
    private final String field;

    public InvalidCandleQueryException(String field, String message) {
        super(message);
        this.field = field;
    }
}
