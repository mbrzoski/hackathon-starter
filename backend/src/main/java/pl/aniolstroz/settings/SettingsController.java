package pl.aniolstroz.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import pl.aniolstroz.contracts.Settings;
import pl.aniolstroz.contracts.api.SettingsApi;

/** GET and PUT /api/settings: the setup wizard of the family panel. The interface is generated from openapi.yaml. */
@RestController
class SettingsController implements SettingsApi {

    private final SettingsService settings;

    SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public ResponseEntity<Settings> getSettings() {
        return ResponseEntity.ok(settings.current());
    }

    @Override
    public ResponseEntity<Settings> setSettings(Settings body) {
        return ResponseEntity.ok(settings.save(body));
    }
}
