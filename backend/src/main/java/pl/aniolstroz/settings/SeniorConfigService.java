package pl.aniolstroz.settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pl.aniolstroz.contracts.SeniorConfig;

/**
 * Configuration of the senior's account: the family phone and the words the family wants the profile to be sensitive
 * to. Both survive a restart. It is a setting, not data: {@code DELETE /api/data} leaves it. It is never sent to
 * Claude or STT (rule 5): the words are matched on the backend only.
 */
@Service
public class SeniorConfigService {

    private final JdbcClient jdbc;

    SeniorConfigService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public SeniorConfig current() {
        String phone = jdbc.sql("SELECT family_phone FROM senior_config WHERE id = 1").query(String.class).optional()
                .orElse("");
        return new SeniorConfig(phone, keywords());
    }

    /** The family's words in the order they were saved. */
    public List<String> keywords() {
        return jdbc.sql("SELECT keyword FROM senior_keywords ORDER BY position").query(String.class).list();
    }

    public SeniorConfig save(SeniorConfig config) {
        String phone = config.familyPhone().trim();
        jdbc.sql("""
                INSERT INTO senior_config (id, family_phone) VALUES (1, :phone)
                ON CONFLICT (id) DO UPDATE SET family_phone = excluded.family_phone""")
                .param("phone", phone).update();
        List<String> keywords = clean(config.keywords());
        jdbc.sql("DELETE FROM senior_keywords").update();
        for (int i = 0; i < keywords.size(); i++) {
            jdbc.sql("INSERT INTO senior_keywords (keyword, position) VALUES (:keyword, :position)")
                    .param("keyword", keywords.get(i)).param("position", i).update();
        }
        return new SeniorConfig(phone, keywords);
    }

    /** Trimmed, single spaces, no empty entries, no duplicates (case-insensitive); the first spelling is kept. */
    static List<String> clean(List<String> raw) {
        Map<String, String> unique = new LinkedHashMap<>();
        for (String word : raw) {
            String trimmed = word.trim().replaceAll("\\s+", " ");
            if (trimmed.length() >= 2) {
                unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
        }
        return List.copyOf(unique.values());
    }
}
