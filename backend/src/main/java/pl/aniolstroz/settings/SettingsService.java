package pl.aniolstroz.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pl.aniolstroz.config.AppProperties;
import pl.aniolstroz.contracts.Sensitivity;
import pl.aniolstroz.contracts.Settings;

/**
 * Household settings (BE-09): consents (AUD-07), trusted contacts, sensitivity and retention (DAT-02). Stored as one
 * JSON row in SQLite; a setting, not data, so {@code DELETE /api/data} leaves it. Nothing here is ever sent to
 * Claude or STT (rule 5). Without saved settings the consents are off: LIVE listening needs the setup wizard first.
 */
@Service
public class SettingsService {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final AppProperties properties;
    private final ApplicationEventPublisher events;

    SettingsService(JdbcClient jdbc, ObjectMapper mapper, AppProperties properties, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.properties = properties;
        this.events = events;
    }

    /** The defaults before the first save: no consent, no contacts, standard sensitivity, configured retention. */
    public Settings defaults() {
        return new Settings(false, false, List.of(), Sensitivity.STANDARD, properties.retention().days(), "");
    }

    public Settings current() {
        Optional<String> json = jdbc.sql("SELECT json FROM settings WHERE id = 1").query(String.class).optional();
        if (json.isEmpty()) {
            return defaults();
        }
        try {
            return mapper.readValue(json.get(), Settings.class);
        } catch (JsonProcessingException e) {
            // A row the current code cannot read (an older shape): start from the defaults rather than fail.
            return defaults();
        }
    }

    public Settings save(Settings settings) {
        Settings clean = new Settings(settings.seniorConsent(), settings.familyConsent(),
                settings.contacts().stream().map(c -> new Settings.Contact(c.name().trim(), c.phone().trim())).toList(),
                settings.sensitivity(), settings.retentionDays(), settings.seniorName().trim());
        String json;
        try {
            json = mapper.writeValueAsString(clean);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write settings", e);
        }
        jdbc.sql("""
                INSERT INTO settings (id, json) VALUES (1, :json)
                ON CONFLICT (id) DO UPDATE SET json = excluded.json""").param("json", json).update();
        events.publishEvent(new SettingsChanged(clean));
        return clean;
    }

    public boolean hasConsent() {
        Settings s = current();
        return s.seniorConsent() && s.familyConsent();
    }

    public Sensitivity sensitivity() {
        return current().sensitivity();
    }

    public int retentionDays() {
        return current().retentionDays();
    }
}
