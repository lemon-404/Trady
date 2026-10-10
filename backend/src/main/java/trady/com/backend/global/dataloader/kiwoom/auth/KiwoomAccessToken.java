package trady.com.backend.global.dataloader.kiwoom.auth;

import java.time.LocalDateTime;

public record KiwoomAccessToken (
    String token,
    LocalDateTime expire
){}
