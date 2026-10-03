package pl.aniolstroz.settings;

import org.springframework.stereotype.Component;

/** AUD-07: LIVE audio only with the consent of the senior and the family, as saved in the setup wizard. */
@Component
class StoredConsentChecker implements ConsentChecker {

    private final SettingsService settings;

    StoredConsentChecker(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public boolean hasConsent() {
        return settings.hasConsent();
    }
}
