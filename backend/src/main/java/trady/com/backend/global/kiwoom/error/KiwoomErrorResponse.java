package trady.com.backend.global.kiwoom.error;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomErrorResponse(
        @JsonProperty("return_code") Integer returnCode,
        @JsonProperty("return_msg") String returnMsg
) {
}
