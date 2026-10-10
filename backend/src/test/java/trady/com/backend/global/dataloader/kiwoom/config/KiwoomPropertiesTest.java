package trady.com.backend.global.dataloader.kiwoom.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
@SpringJUnitConfig(
        classes = KiwoomPropertiesTest.TestConfig.class,
        initializers = ConfigDataApplicationContextInitializer.class
)
class KiwoomPropertiesTest {

    @Autowired
    private KiwoomProperties properties;

    @DisplayName("설정 파일에서 키움 설정을 읽어온다")
    @Test
    void loadsKiwoomProperties() {
        assertThat(properties.baseUrl())
                .isEqualTo("https://mockapi.kiwoom.com");

        assertThat(hasText(properties.appKey()))
                .as("앱키가 설정되어 있어야 한다")
                .isTrue();

        assertThat(hasText(properties.secretKey()))
                .as("시크릿키가 설정되어 있어야 한다")
                .isTrue();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(KiwoomProperties.class)
    static class TestConfig {
    }
}