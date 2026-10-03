package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum RiskLevel {
    NONE, LOW, MEDIUM, HIGH;

    @JsonValue
    String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
