package pl.aniolstroz.stt;

/**
 * Speech-to-text behind one interface (AUD-03). One instance serves one recognition run: the supervisor creates a new
 * one per call and again after a failure. Listeners are called from the provider's own thread and must return quickly
 * (AUD-04); audio is never stored or logged (AUD-02).
 */
public interface SttProvider {

    /** Starts recognising. Throws if the recogniser cannot be created; later failures go to {@code errors}. */
    void start(SegmentListener segments, ErrorListener errors);

    /** Hands over one PCM frame (16 kHz, mono, 16-bit little endian). Never blocks; a frame may be dropped. */
    void write(byte[] pcm);

    /** Recognises what is left, delivers the last segment and releases everything. Idempotent. */
    void stop();

    @FunctionalInterface
    interface SegmentListener {
        void onSegment(SttSegment segment);
    }

    /** The recogniser failed and has stopped. The error text may contain speech, so it is not to be logged. */
    @FunctionalInterface
    interface ErrorListener {
        void onError(Throwable error);
    }
}
