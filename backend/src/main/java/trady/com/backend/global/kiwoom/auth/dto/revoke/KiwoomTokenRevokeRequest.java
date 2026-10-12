package trady.com.backend.global.kiwoom.auth.dto.revoke;

public record KiwoomTokenRevokeRequest(
    String appkey,
    String secretkey,
    String token

) {}
