package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum DecisionType {
    HUNG_UP, CALLED_TRUSTED, FALSE_ALARM, CONFIRMED_SCAM;

    @JsonValue
    String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
