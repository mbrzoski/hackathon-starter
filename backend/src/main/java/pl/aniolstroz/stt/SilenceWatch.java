package pl.aniolstroz.stt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Watches one live audio stream for silence (OBS-02): no frames, or only flat frames (a muted or dead microphone),
 * for the whole timeout means the audio is lost. Frames arrive on the WebSocket thread and {@link #check()} runs on a
 * scheduler, so the state is guarded by a {@link ReentrantLock}. A user pause is not silence.
 *
 * <p>Only the loudest sample of a frame is read; the audio itself is neither kept nor logged (AUD-02).
 */
final class SilenceWatch {

    /** Loudest sample (of 32768) below which a frame counts as flat. A real microphone is noisier than this. */
    static final int SIGNAL_THRESHOLD = 8;

    enum Change { NONE, SILENT, RECOVERED }

    private final Clock clock;
    private final Duration timeout;
    private final ReentrantLock lock = new ReentrantLock();
    private Instant lastSignalAt;
    private boolean silent;
    private boolean paused;

    SilenceWatch(Clock clock, Duration timeout) {
        this.clock = clock;
        this.timeout = timeout;
        this.lastSignalAt = clock.instant();
    }

    /** A frame arrived. RECOVERED if it ends a silence that had been reported. */
    Change onFrame(byte[] pcm) {
        if (!hasSignal(pcm)) {
            return Change.NONE;
        }
        lock.lock();
        try {
            lastSignalAt = clock.instant();
            if (silent) {
                silent = false;
                return Change.RECOVERED;
            }
            return Change.NONE;
        } finally {
            lock.unlock();
        }
    }

    void pause() {
        lock.lock();
        try {
            paused = true;
        } finally {
            lock.unlock();
        }
    }

    /** Counts from now again: the time spent paused must not count as silence. */
    void resume() {
        lock.lock();
        try {
            paused = false;
            silent = false;
            lastSignalAt = clock.instant();
        } finally {
            lock.unlock();
        }
    }

    /** SILENT once, when the timeout has passed without signal; NONE while silence is already reported. */
    Change check() {
        lock.lock();
        try {
            if (paused || silent) {
                return Change.NONE;
            }
            if (Duration.between(lastSignalAt, clock.instant()).compareTo(timeout) >= 0) {
                silent = true;
                return Change.SILENT;
            }
            return Change.NONE;
        } finally {
            lock.unlock();
        }
    }

    /** 16-bit little endian samples: true if any is louder than the threshold. */
    static boolean hasSignal(byte[] pcm) {
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            if (Math.abs(sample) >= SIGNAL_THRESHOLD) {
                return true;
            }
        }
        return false;
    }
}
