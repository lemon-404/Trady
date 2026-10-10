package trady.com.backend.global.dataloader.kiwoom.auth;

import org.springframework.stereotype.Component;
import trady.com.backend.global.dataloader.kiwoom.auth.dto.issue.KiwoomTokenIssueResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class KiwoomTokenProvider {
    private static final DateTimeFormatter EXPIRES_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // 요청 도중 만료되는 상황을 줄이기 위한 여유 시간
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(1);

    private final KiwoomAuthClient authClient;
    private final Clock clock;

    private KiwoomAccessToken currentToken;
    private boolean shuttingDown;

    public KiwoomTokenProvider(
            KiwoomAuthClient authClient,
            Clock clock
    ) {
        this.authClient = authClient;
        this.clock = clock;
    }

    public void initialize() {
        getAccessToken();
    }
    public synchronized String getAccessToken() {
        if (shuttingDown) {
            throw new IllegalStateException("토큰 관리가 종료 중입니다.");
        }

        if (needsRenewal()) {
            currentToken = issueAccessToken();
        }

        return currentToken.token();
    }

    public synchronized void revokeCurrentToken() {
        shuttingDown = true;

        if (currentToken == null) {
            return;
        }

        // 성공한 경우에만 보관된 토큰을 제거한다.
        authClient.revokeToken(currentToken.token());
        currentToken = null;
    }

    private boolean needsRenewal() {
        if (currentToken == null) {
            return true;
        }

        LocalDateTime threshold = LocalDateTime.now(clock)
                .plus(EXPIRY_MARGIN);

        // 만료 시각이 여유 시간 경계와 같아도 재발급한다.
        return !currentToken.expire().isAfter(threshold);
    }

    private KiwoomAccessToken issueAccessToken() {
        KiwoomTokenIssueResponse response = authClient.issueToken();

        if (response.expiresDt() == null || response.expiresDt().isBlank()) {
            throw new IllegalStateException("토큰 만료 시각이 없습니다.");
        }

        LocalDateTime expiresAt = LocalDateTime.parse(
                response.expiresDt(),
                EXPIRES_FORMAT
        );

        LocalDateTime threshold = LocalDateTime.now(clock)
                .plus(EXPIRY_MARGIN);

        if (!expiresAt.isAfter(threshold)) {
            throw new IllegalStateException(
                    "발급된 토큰의 남은 유효 시간이 부족합니다."
            );
        }

        return new KiwoomAccessToken(response.token(), expiresAt);
    }
}
