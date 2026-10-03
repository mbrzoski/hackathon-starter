package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum SpeakerRole {
    CALLER, SENIOR, BACKGROUND, UNCLEAR;

    @JsonValue
    String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
