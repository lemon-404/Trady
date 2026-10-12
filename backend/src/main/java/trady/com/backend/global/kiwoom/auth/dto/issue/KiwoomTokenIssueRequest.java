package trady.com.backend.global.kiwoom.auth.dto.issue;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KiwoomTokenIssueRequest(
    @JsonProperty("grant_type") String grantType,
    String appkey,
    String secretkey

) {
    public KiwoomTokenIssueRequest(String appKey, String secretKey) {
        this("client_credentials", appKey, secretKey);
    }
}
