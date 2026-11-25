package trady.com.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import trady.com.backend.global.kiwoom.auth.KiwoomTokenProvider;
import trady.com.backend.global.kiwoom.watchlist.WatchlistService;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:context-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
        "candle.sync-setup=false",
        "kiwoom.candle.realtime.enabled=false",
        "kiwoom.base-url=https://kiwoom.test",
        "kiwoom.app-key=test-app-key",
        "kiwoom.secret-key=test-secret-key"
})
class BackendApplicationTests {

    @MockitoBean KiwoomTokenProvider tokenProvider;
    @MockitoBean WatchlistService watchlistService;

	@Test
	void contextLoads() {
	}

}
