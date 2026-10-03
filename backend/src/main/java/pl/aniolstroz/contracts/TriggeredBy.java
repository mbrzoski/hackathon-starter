package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum TriggeredBy {
    LLM, KEYWORDS, BOTH;

    @JsonValue
    String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
