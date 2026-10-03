package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum Component {
    AUDIO, STT, AI, BACKEND;

    @JsonValue
    String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
