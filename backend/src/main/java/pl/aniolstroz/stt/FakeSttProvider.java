package pl.aniolstroz.stt;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import pl.aniolstroz.contracts.SpeakerLabel;

/**
 * Test double: turns incoming "audio" into prepared texts, without recognising anything. Each text takes
 * {@code framesPerUtterance} frames: every frame but the last yields an interim segment, the last yields the final
 * one. Frames beyond the prepared texts are ignored. Segments are delivered on the thread that calls {@link #write}.
 * Times follow the frame count (100 ms per frame), like the real provider.
 */
public class FakeSttProvider implements SttProvider {

    private static final long FRAME_MS = 100;

    private final List<String> utterances;
    private final int framesPerUtterance;
    private final AtomicInteger frames = new AtomicInteger();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private volatile SegmentListener segments;
    private volatile ErrorListener errors;

    public FakeSttProvider(List<String> utterances, int framesPerUtterance) {
        if (framesPerUtterance < 1) {
            throw new IllegalArgumentException("framesPerUtterance must be at least 1");
        }
        this.utterances = List.copyOf(utterances);
        this.framesPerUtterance = framesPerUtterance;
    }

    public FakeSttProvider(List<String> utterances) {
        this(utterances, 2);
    }

    @Override
    public void start(SegmentListener segmentListener, ErrorListener errorListener) {
        this.segments = segmentListener;
        this.errors = errorListener;
        started.set(true);
    }

    @Override
    public void write(byte[] pcm) {
        SegmentListener listener = segments;
        if (!started.get() || stopped.get() || listener == null) {
            return;
        }
        int index = frames.getAndIncrement();
        int utterance = index / framesPerUtterance;
        if (utterance >= utterances.size()) {
            return;
        }
        int inUtterance = index % framesPerUtterance;
        boolean last = inUtterance == framesPerUtterance - 1;
        long start = (long) utterance * framesPerUtterance * FRAME_MS;
        listener.onSegment(new SttSegment(start, (index + 1) * FRAME_MS, utterances.get(utterance), last,
                SpeakerLabel.UNKNOWN, null));
    }

    @Override
    public void stop() {
        stopped.set(true);
    }

    /** Simulates a recognizer failure, as the real provider reports one. */
    public void fail(Throwable error) {
        ErrorListener listener = errors;
        if (listener != null) {
            listener.onError(error);
        }
    }

    public boolean isStopped() {
        return stopped.get();
    }
}
