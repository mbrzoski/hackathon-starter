package pl.aniolstroz.contracts;

import com.fasterxml.jackson.annotation.JsonValue;

/** Diarization label: "A", "B" or "unknown" on the wire. */
public enum SpeakerLabel {
    A("A"),
    B("B"),
    UNKNOWN("unknown");

    private final String wire;

    SpeakerLabel(String wire) {
        this.wire = wire;
    }

    @JsonValue
    String wire() {
        return wire;
    }
}
