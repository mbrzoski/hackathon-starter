package pl.aniolstroz.ai;

import java.util.List;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.TranscriptSegment;

/**
 * What the classifier gets: the final segments of one call so far. Nothing from the settings (AI-09).
 * {@code scenarioId} is only used by the mock classifier and is never sent to the API.
 */
public record CallSnapshot(String callId, Mode mode, String scenarioId, List<TranscriptSegment> segments) {

    public CallSnapshot {
        segments = List.copyOf(segments);
    }

    public String firstSegmentId() {
        return segments.get(0).segId();
    }

    public String lastSegmentId() {
        return segments.get(segments.size() - 1).segId();
    }

    /** The number N of the last segment sN, 0 for an empty transcript. */
    public int lastSegmentNumber() {
        if (segments.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(lastSegmentId().substring(1));
    }

    public String segmentRange() {
        return segments.isEmpty() ? "" : firstSegmentId() + "-" + lastSegmentId();
    }
}
