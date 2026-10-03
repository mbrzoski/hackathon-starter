package pl.aniolstroz.call;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.TranscriptSegment;

/**
 * State of one call. Every read and write of the mutable fields happens under the call's own lock (CC-01, CC-03).
 * The transcript lives only in memory (DAT-01).
 */
public final class CallState {

    private final String callId;
    private final Mode mode;
    private final Instant startedAt;

    private final ReentrantLock lock = new ReentrantLock();
    private final List<TranscriptSegment> transcript = new ArrayList<>();
    private int segmentCount;
    private boolean ended;
    private boolean alerted;

    CallState(String callId, Mode mode, Instant startedAt) {
        this.callId = callId;
        this.mode = mode;
        this.startedAt = startedAt;
    }

    public String callId() {
        return callId;
    }

    public Mode mode() {
        return mode;
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** Copy of the final segments kept so far. */
    public List<TranscriptSegment> transcript() {
        lock.lock();
        try {
            return List.copyOf(transcript);
        } finally {
            lock.unlock();
        }
    }

    public boolean hadAlert() {
        lock.lock();
        try {
            return alerted;
        } finally {
            lock.unlock();
        }
    }

    /** Called by the alert pipeline when an alert was raised, so the transcript is kept (BE-04 follow-up). */
    public void markAlerted() {
        lock.lock();
        try {
            alerted = true;
        } finally {
            lock.unlock();
        }
    }

    /** Removes the whole transcript from memory (DAT-01). */
    public void clearTranscript() {
        lock.lock();
        try {
            transcript.clear();
        } finally {
            lock.unlock();
        }
    }

    ReentrantLock lock() {
        return lock;
    }

    /** Assigns the next segment id. Caller must hold the lock. */
    String nextSegmentId() {
        return "s" + (++segmentCount);
    }

    /** Caller must hold the lock. */
    void keep(TranscriptSegment segment) {
        transcript.add(segment);
    }

    /** Caller must hold the lock. */
    boolean isEnded() {
        return ended;
    }

    /** Caller must hold the lock. */
    void markEnded() {
        ended = true;
    }
}
