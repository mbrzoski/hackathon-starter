package pl.aniolstroz.settings;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pl.aniolstroz.contracts.SeniorConfig;

/**
 * Configuration of the senior's account. Only the family phone is kept, in one row that survives a restart. It is a
 * setting, not data: {@code DELETE /api/data} leaves it. It is never sent to Claude or STT (rule 5).
 */
@Service
public class SeniorConfigService {

    private final JdbcClient jdbc;

    SeniorConfigService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public SeniorConfig current() {
        return jdbc.sql("SELECT family_phone FROM senior_config WHERE id = 1").query(String.class).optional()
                .map(SeniorConfig::new).orElse(new SeniorConfig(""));
    }

    public SeniorConfig save(SeniorConfig config) {
        String phone = config.familyPhone().trim();
        jdbc.sql("""
                INSERT INTO senior_config (id, family_phone) VALUES (1, :phone)
                ON CONFLICT (id) DO UPDATE SET family_phone = excluded.family_phone""")
                .param("phone", phone).update();
        return new SeniorConfig(phone);
    }
}
