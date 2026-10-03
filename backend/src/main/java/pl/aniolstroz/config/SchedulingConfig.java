package pl.aniolstroz.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} tasks (CC-04). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}
