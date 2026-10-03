package pl.aniolstroz.stt;

import pl.aniolstroz.contracts.SpeakerLabel;

/**
 * One interim or final piece of recognised speech, before the call gives it an id. {@code sttConfidence} is null
 * unless the recogniser returned word confidences. The text is never logged (OBS-05), so {@code toString} hides it.
 */
public record SttSegment(long tStartMs, long tEndMs, String text, boolean isFinal, SpeakerLabel speaker,
        Double sttConfidence) {

    @Override
    public String toString() {
        return "SttSegment[" + (isFinal ? "final" : "interim") + "]";
    }
}
