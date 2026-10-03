package pl.aniolstroz.risk;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RiskConfig {

    @Bean
    KeywordDetector keywordDetector() {
        return KeywordDetector.bundled();
    }
}
