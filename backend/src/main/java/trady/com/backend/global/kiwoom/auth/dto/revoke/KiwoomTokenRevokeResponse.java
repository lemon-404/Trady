package trady.com.backend.global.kiwoom.auth.dto.revoke;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomTokenRevokeResponse(
        @JsonProperty("return_code") Integer returnCode,
        @JsonProperty("return_msg") String returnMsg
) {
    @Override
    public String toString() {
        return "returnCode=[" + returnCode + "]" +
                "returnMsg=[" + returnMsg + "]";
    }

}
