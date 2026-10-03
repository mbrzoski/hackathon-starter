package pl.aniolstroz.settings;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * The protection switch of the admin portal. Protection is on unless a caretaker turned it off; the choice survives a
 * restart. Only the flag is stored: no audio, no text, no settings that could reach Claude or STT (rule 5).
 */
@Service
public class ProtectionService {

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    ProtectionService(JdbcClient jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    public boolean enabled() {
        return jdbc.sql("SELECT enabled FROM protection WHERE id = 1").query(Integer.class).optional()
                .map(value -> value == 1).orElse(true);
    }

    /** Stores the setting and tells the audio path, also when the value did not change (it is cheap and idempotent). */
    public void set(boolean enabled) {
        jdbc.sql("""
                INSERT INTO protection (id, enabled) VALUES (1, :enabled)
                ON CONFLICT (id) DO UPDATE SET enabled = excluded.enabled""")
                .param("enabled", enabled ? 1 : 0).update();
        events.publishEvent(new ProtectionChanged(enabled));
    }
}
