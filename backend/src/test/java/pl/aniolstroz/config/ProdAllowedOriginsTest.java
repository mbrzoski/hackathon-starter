package pl.aniolstroz.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The prod profile takes the public origin(s) of the web server from APP_EVENTS_ALLOWED_ORIGINS (API-04). */
class ProdAllowedOriginsTest {

    @Nested
    @SpringBootTest(properties = {
        "spring.profiles.active=prod", "app.mode=MOCK", "APP_DB_FILE=:memory:",
        "APP_EVENTS_ALLOWED_ORIGINS=https://192.168.1.50,https://demo.trycloudflare.com"})
    class WithOrigins {
        @Autowired
        AppProperties properties;

        @Test
        void readsTheCommaSeparatedList() {
            assertThat(properties.events().allowedOrigins())
                    .containsExactly("https://192.168.1.50", "https://demo.trycloudflare.com");
        }
    }

    @Nested
    @SpringBootTest(properties = {"spring.profiles.active=prod", "app.mode=MOCK", "APP_DB_FILE=:memory:"})
    class WithoutOrigins {
        @Autowired
        AppProperties properties;

        @Test
        void fallsBackToSameOriginOnly() {
            assertThat(properties.events().allowedOrigins()).isEmpty();
        }
    }
}
