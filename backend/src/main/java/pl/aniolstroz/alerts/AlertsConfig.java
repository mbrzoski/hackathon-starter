package pl.aniolstroz.alerts;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AlertsConfig {

    @Bean
    AlertTemplates alertTemplates() {
        return AlertTemplates.bundled();
    }
}
