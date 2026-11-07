package trady.com.backend.global.kiwoom.error;

import lombok.Getter;

@Getter
public class KiwoomApiException extends RuntimeException {

    private final String returnCode;
    private final String errorCode;

    public KiwoomApiException(
            String returnCode,
            String errorCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.returnCode = returnCode;
        this.errorCode = errorCode;
    }
}
