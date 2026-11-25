package trady.com.backend.candle.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import trady.com.backend.candle.config.CandleQueryProperties;

import static org.assertj.core.api.Assertions.assertThat;

class CandleQueryPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultsTo100() {
        runner.run(context -> assertThat(context.getBean(CandleQueryProperties.class).pageSize()).isEqualTo(100));
    }

    @Test
    void bindsConfiguredPageSize() {
        runner.withPropertyValues("trady.candle.query.page-size=7")
                .run(context -> assertThat(context.getBean(CandleQueryProperties.class).pageSize()).isEqualTo(7));
    }

    @Test
    void rejectsNonPositivePageSize() {
        runner.withPropertyValues("trady.candle.query.page-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CandleQueryProperties.class)
    static class PropertiesConfiguration {
    }
}
