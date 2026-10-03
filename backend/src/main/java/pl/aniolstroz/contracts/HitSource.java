package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum HitSource {
    LLM, KEYWORDS;

    @JsonValue
    String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
