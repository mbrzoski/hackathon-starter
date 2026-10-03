package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ComponentState {
    OK, DEGRADED, DOWN;

    @JsonValue
    String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
