package trady.com.backend.global.kiwoom.auth.dto.issue;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomTokenIssueResponse(
        @JsonProperty("expires_dt") String expiresDt,
        @JsonProperty("token_type") String tokenType,
        String token,
        @JsonProperty("return_code") Integer returnCode,
        @JsonProperty("return_msg") String returnMsg
) {
    @Override
    public String toString() {
        return "returnCode=[" + returnCode + "]" +
                "KiwoomTokenResponse[token= ****" +
                "], expire=[" + expiresDt + "]";
    }

}
