package pl.aniolstroz.alerts;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import pl.aniolstroz.contracts.TranscriptSegment;

/** DAT-01: what of an alerted call's transcript may stay in memory and in the database. */
public final class TranscriptExcerpt {

    private TranscriptExcerpt() {
    }

    /**
     * The cited segments plus {@code context} neighbours on each side, in transcript order and without duplicates.
     * A cited id that is not in the transcript keeps nothing.
     */
    public static List<TranscriptSegment> select(
            List<TranscriptSegment> transcript, Collection<String> citedSegIds, int context) {
        Set<Integer> keep = new HashSet<>();
        for (int i = 0; i < transcript.size(); i++) {
            if (citedSegIds.contains(transcript.get(i).segId())) {
                int from = Math.max(0, i - context);
                int to = Math.min(transcript.size() - 1, i + context);
                for (int j = from; j <= to; j++) {
                    keep.add(j);
                }
            }
        }
        return java.util.stream.IntStream.range(0, transcript.size())
                .filter(keep::contains)
                .mapToObj(transcript::get)
                .toList();
    }
}
