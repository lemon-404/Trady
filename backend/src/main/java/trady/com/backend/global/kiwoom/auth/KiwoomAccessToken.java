package trady.com.backend.global.kiwoom.auth;

import java.time.LocalDateTime;

public record KiwoomAccessToken (
    String token,
    LocalDateTime expire
){}
