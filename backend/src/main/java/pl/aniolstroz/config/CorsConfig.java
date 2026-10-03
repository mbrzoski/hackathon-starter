package pl.aniolstroz.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for /api/** (API-04): only the origins in {@code app.events.allowed-origins}, the same list that guards
 * /ws/events. Empty (the default) registers nothing, so only same-origin requests work. In prod the list is the
 * public address of the web server (APP_EVENTS_ALLOWED_ORIGINS); in dev it is the localhost dev servers.
 */
@Configuration(proxyBeanMethods = false)
class CorsConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    CorsConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var origins = properties.events().allowedOrigins();
        if (origins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT")
                .allowedHeaders("Content-Type", "Accept");
    }
}
