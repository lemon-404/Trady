package trady.com.backend.global.kiwoom.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import trady.com.backend.global.kiwoom.auth.dto.issue.KiwoomTokenIssueRequest;
import trady.com.backend.global.kiwoom.auth.dto.issue.KiwoomTokenIssueResponse;
import trady.com.backend.global.kiwoom.auth.dto.revoke.KiwoomTokenRevokeRequest;
import trady.com.backend.global.kiwoom.auth.dto.revoke.KiwoomTokenRevokeResponse;
import trady.com.backend.global.kiwoom.config.KiwoomProperties;

import java.time.Duration;

@Component
public class KiwoomAuthClient {

    private final WebClient webClient;
    private final KiwoomProperties properties;

    public KiwoomAuthClient(
            @Qualifier("kiwoomWebClient") WebClient webClient,
            KiwoomProperties properties
    ) {
        this.webClient = webClient;
        this.properties = properties;
    }
    public KiwoomTokenIssueResponse issueToken() {
        KiwoomTokenIssueRequest request = new KiwoomTokenIssueRequest(
                properties.appKey(),
                properties.secretKey()
        );

        KiwoomTokenIssueResponse response = webClient
                .post()
                .uri("/oauth2/token")
                .contentType(
                        MediaType.parseMediaType(
                                "application/json;charset=UTF-8"
                        )
                )
                .bodyValue(request)
                .retrieve()
                .bodyToMono(KiwoomTokenIssueResponse.class)
                .block(Duration.ofSeconds(10));

        if (response == null) {
            throw new IllegalStateException("키움 토큰 발급 응답이 비어 있습니다.");
        }

        if (!Integer.valueOf(0).equals(response.returnCode())) {
            throw new IllegalStateException(
                    "키움 토큰 발급 실패: code=" + response.returnCode()
            );
        }

        if (response.token() == null || response.token().isBlank()) {
            throw new IllegalStateException("키움 응답에 접근 토큰이 없습니다.");
        }

        return response;
    }
    public void revokeToken(String token){
        KiwoomTokenRevokeRequest request = new KiwoomTokenRevokeRequest(
                properties.appKey(),
                properties.secretKey(),
                token
        );

        KiwoomTokenRevokeResponse response = webClient
                .post()
                .uri("/oauth2/revoke")
                .contentType(
                        MediaType.parseMediaType(
                                "application/json;charset=UTF-8"
                        )
                )
                .bodyValue(request)
                .retrieve()
                .bodyToMono(KiwoomTokenRevokeResponse.class)
                .block(Duration.ofSeconds(10));

        if (response == null) {
            throw new IllegalStateException("키움 토큰 폐기 응답이 비어 있습니다.");
        }

        if (!Integer.valueOf(0).equals(response.returnCode())) {
            throw new IllegalStateException(
                    "키움 토큰 폐기 실패: code=" + response.returnCode()
            );
        }

    }
}
