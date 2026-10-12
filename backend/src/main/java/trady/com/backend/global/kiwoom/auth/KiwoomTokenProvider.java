package trady.com.backend.global.kiwoom.auth;

import org.springframework.stereotype.Component;
import trady.com.backend.global.kiwoom.auth.dto.issue.KiwoomTokenIssueResponse;

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

    private KiwoomAccessToken token;

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
    public synchronized void getAccessToken() {
        token = issueAccessToken();

    }

    public synchronized void revokeToken() {
        if (token == null) {
            return;
        }

        authClient.revokeToken(token.token());
    }

    public synchronized void renewalToken() {
        if (token == null) {
            return;
        }

        Duration remaining = Duration.between(
                token.expire(),
                LocalDateTime.now(clock)
        );

        if (remaining.compareTo(Duration.ofHours(18)) > 0) {
            revokeToken();

        }
        issueAccessToken();
    }

    public String getToken() {
        return token.token();
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
