package pl.aniolstroz.settings;

import pl.aniolstroz.contracts.Settings;

/** The household saved new settings (for example a shorter retention, which is applied at once). */
public record SettingsChanged(Settings settings) {
}
