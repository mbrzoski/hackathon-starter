package pl.aniolstroz.settings;

import org.springframework.stereotype.Component;
import pl.aniolstroz.contracts.Sensitivity;
import pl.aniolstroz.risk.SensitivitySource;

/** The household's sensitivity from the setup wizard (Calm / Standard / Sensitive); Standard until it is saved. */
@Component
class StoredSensitivitySource implements SensitivitySource {

    private final SettingsService settings;

    StoredSensitivitySource(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public Sensitivity current() {
        return settings.sensitivity();
    }
}
