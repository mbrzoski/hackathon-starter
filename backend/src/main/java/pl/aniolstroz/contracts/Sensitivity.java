package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Sensitivity {
    CALM, STANDARD, SENSITIVE;

    @JsonValue
    String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
