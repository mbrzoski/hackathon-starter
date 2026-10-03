package pl.aniolstroz.settings;

import org.springframework.stereotype.Component;

/** Placeholder until the settings task (BE-09) stores real consents: always says yes. Replace it, do not extend it. */
@Component
class PermissiveConsentChecker implements ConsentChecker {

    @Override
    public boolean hasConsent() {
        return true;
    }
}
