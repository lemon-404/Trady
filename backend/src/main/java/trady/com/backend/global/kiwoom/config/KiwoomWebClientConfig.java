package trady.com.backend.global.kiwoom.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.time.ZoneId;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KiwoomProperties.class)
@RequiredArgsConstructor
public class KiwoomWebClientConfig {
    private final KiwoomProperties properties;

    @Bean
    public WebClient kiwoomWebClient() {
        return WebClient.builder()
                .baseUrl(properties.baseUrl())
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(2*1024*1024))
                .build();
    }
    @Bean
    public Clock kiwoomClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
