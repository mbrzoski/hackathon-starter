package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Actor {
    SENIOR, FAMILY;

    @JsonValue
    String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
