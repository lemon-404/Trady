package trady.com.backend.global.dataloader.kiwoom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kiwoom")
public record KiwoomProperties (
        String baseUrl,
        String appKey,
        String secretKey
) {
    @Override
    public String toString() {
        return "KiwoomProperties{" +
                "baseUrl='" + baseUrl + '\'' +
                ", appKey='" + "****" + '\'' +
                ", secretKey='" + "****"  + '\'' +
                '}';
    }
}
