package com.hackathonstarter.health;

import com.hackathonstarter.config.AppProperties;
import java.sql.Connection;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    public record LlmInfo(String provider, String model, boolean apiKeyConfigured) {}

    public record Health(String status, String app, String[] profiles, Instant time, String database, LlmInfo llm) {}

    private final AppProperties props;
    private final DataSource dataSource;
    private final Environment env;
    private final String appName;

    public HealthController(AppProperties props, DataSource dataSource, Environment env,
                            @Value("${spring.application.name}") String appName) {
        this.props = props;
        this.dataSource = dataSource;
        this.env = env;
        this.appName = appName;
    }

    @GetMapping
    public Health health() {
        AppProperties.Llm llm = props.llm();
        return new Health("UP", appName, env.getActiveProfiles(), Instant.now(), databaseStatus(),
                new LlmInfo(llm.provider(), llm.model(), llm.isMock() || llm.apiKeyConfigured()));
    }

    private String databaseStatus() {
        try (Connection c = dataSource.getConnection()) {
            return c.isValid(2) ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
