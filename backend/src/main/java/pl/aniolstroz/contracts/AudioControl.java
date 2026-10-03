package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;

/** Control message on /ws/audio (text frame), the schema AudioControl in contracts/openapi.yaml. */
public record AudioControl(@NotNull Command type) {

    public enum Command {
        START, STOP, PAUSE, RESUME;

        @JsonValue
        String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
