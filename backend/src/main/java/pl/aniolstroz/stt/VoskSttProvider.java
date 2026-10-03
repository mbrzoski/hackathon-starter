package pl.aniolstroz.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.vosk.Model;
import org.vosk.Recognizer;
import pl.aniolstroz.config.Trace;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.SpeakerLabel;

/**
 * Local, offline speech recognition with Vosk (AUD-03). Vosk has no callbacks, so one dedicated platform thread per
 * run reads frames from a bounded queue, feeds the {@link Recognizer} and reports what it hears (AUD-04): native,
 * CPU-heavy code would pin a virtual thread. {@link #write} never blocks: when the queue is full the frame is dropped
 * and STT is reported degraded once, until the queue has drained.
 *
 * <p>Interim text comes from {@code getPartialResult} (reported when it changes), final text from {@code getResult}
 * when the recognizer detects the end of an utterance, and the rest from {@code getFinalResult} on {@link #stop}.
 * Times are counted from the bytes fed (3200 bytes = 100 ms). Confidence is the mean confidence of the words of a
 * final result. Vosk without a speaker model cannot tell speakers apart, so the speaker is always UNKNOWN.
 *
 * <p>Audio is never logged. Recognised text is only in the content trace, which is off by default ({@link Trace}); the
 * flow trace has the numbers: how long recognition takes against the audio it was given (a factor above 1 means it
 * cannot keep up), the queue, partial and final results, empty finals. The recognizer is created in {@link #start}
 * and closed by the recognition thread (native memory) whatever happens.
 */
public class VoskSttProvider implements SttProvider {

    static final float SAMPLE_RATE = 16000f;
    /** 16 kHz * 2 bytes per sample / 1000 ms. */
    private static final int BYTES_PER_MS = 32;
    static final int QUEUE_CAPACITY = 100; // 10 s of audio
    static final String BEHIND_MESSAGE = "Rozpoznawanie mowy nie nadąża";
    static final String CAUGHT_UP_MESSAGE = "Rozpoznawanie mowy działa";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final VoskModelHolder models;
    private final SttStatusSink status;
    private final ArrayBlockingQueue<byte[]> frames = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean overflowing = new AtomicBoolean();
    private volatile boolean stopping;
    private volatile boolean failed;
    private Recognizer recognizer;
    private Thread thread;
    private SegmentListener segments;

    // Touched only by the recognition thread.
    private long fedBytes;
    private long statsSinceNanos = System.nanoTime();
    private long statsFrames;
    private long statsWorkNanos;
    private long statsMaxNanos;
    private long statsPartials;
    private long statsFinals;
    private long statsEmptyFinals;
    private long utteranceStartMs = -1;
    private long lastFinalEndMs;
    private String lastPartial = "";

    public VoskSttProvider(VoskModelHolder models, SttStatusSink status) {
        this.models = models;
        this.status = status;
    }

    @Override
    public void start(SegmentListener segmentListener, ErrorListener errors) {
        if (thread != null) {
            throw new IllegalStateException("Already started");
        }
        Model model;
        try {
            model = models.model();
        } catch (SttUnavailableException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        Recognizer created;
        try {
            created = new Recognizer(model, SAMPLE_RATE);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create the recognizer", e);
        }
        created.setWords(true);
        this.recognizer = created;
        Trace.flow("vosk | recognizer created: {} Hz, mono, 16-bit, word timings and confidences on", (int) SAMPLE_RATE);
        this.segments = segmentListener;
        Thread t = Thread.ofPlatform().name("vosk-recognizer").daemon(true).unstarted(() -> run(errors));
        this.thread = t;
        try {
            t.start();
        } catch (RuntimeException | Error e) {
            created.close();
            throw e;
        }
    }

    @Override
    public void write(byte[] pcm) {
        if (stopping || failed) {
            return;
        }
        if (!frames.offer(pcm)) {
            if (overflowing.compareAndSet(false, true)) {
                Trace.flow("vosk | the recognizer cannot keep up: the queue of {} frames is full, frames are dropped",
                        QUEUE_CAPACITY);
                status.onStatus(ComponentState.DEGRADED, BEHIND_MESSAGE);
            }
        }
    }

    @Override
    public void stop() {
        Trace.flow("vosk | stop requested, {} frames still queued", frames.size());
        stopping = true;
        Thread t = thread;
        if (t == null) {
            return;
        }
        try {
            t.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run(ErrorListener errors) {
        try {
            while (true) {
                byte[] frame = frames.poll(50, TimeUnit.MILLISECONDS);
                if (frame == null) {
                    if (stopping) {
                        break;
                    }
                    continue;
                }
                long began = System.nanoTime();
                process(frame);
                recordWork(System.nanoTime() - began);
                if (overflowing.get() && frames.isEmpty() && overflowing.compareAndSet(true, false)) {
                    status.onStatus(ComponentState.OK, CAUGHT_UP_MESSAGE);
                }
            }
            JsonNode last = JSON.readTree(recognizer.getFinalResult());
            emitFinal(last);
            Trace.flow("vosk | recognition finished: {} ms of audio fed in total", fedBytes / BYTES_PER_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            failed = true;
            Trace.flow("vosk | the recognizer failed: {}", t.getClass().getName());
            errors.onError(t);
        } finally {
            failed = true;
            recognizer.close();
        }
    }

    private void process(byte[] frame) throws IOException {
        long frameStartMs = fedBytes / BYTES_PER_MS;
        fedBytes += frame.length;
        if (recognizer.acceptWaveForm(frame, frame.length)) {
            emitFinal(JSON.readTree(recognizer.getResult()));
            return;
        }
        String partial = JSON.readTree(recognizer.getPartialResult()).path("partial").asText("");
        if (partial.isEmpty() || partial.equals(lastPartial)) {
            return;
        }
        if (utteranceStartMs < 0) {
            utteranceStartMs = frameStartMs;
        }
        lastPartial = partial;
        statsPartials++;
        if (Trace.content()) {
            Trace.content("vosk | partial \"{}\"", Trace.oneLine(partial));
        }
        segments.onSegment(new SttSegment(utteranceStartMs, fedBytes / BYTES_PER_MS, partial, false,
                SpeakerLabel.UNKNOWN, null));
    }

    private void emitFinal(JsonNode result) {
        String text = result.path("text").asText("").trim();
        long endMs = fedBytes / BYTES_PER_MS;
        long startMs = utteranceStartMs >= 0 ? utteranceStartMs : lastFinalEndMs;
        utteranceStartMs = -1;
        lastPartial = "";
        lastFinalEndMs = endMs;
        if (text.isEmpty()) {
            statsEmptyFinals++;
            Trace.flow("vosk | final without words at {} ms (an utterance the recognizer dropped, or silence)", endMs);
            return;
        }
        Double confidence = meanConfidence(result.path("result"));
        statsFinals++;
        Trace.flow("vosk | final: {} chars, {} words, confidence {}, {}-{} ms", text.length(), result.path("result").size(),
                confidence == null ? "-" : String.format("%.2f", confidence), Math.min(startMs, endMs), endMs);
        if (Trace.content()) {
            Trace.content("vosk | final \"{}\" raw={}", Trace.oneLine(text), Trace.oneLine(result.toString()));
        }
        segments.onSegment(new SttSegment(Math.min(startMs, endMs), endMs, text, true, SpeakerLabel.UNKNOWN,
                confidence));
    }

    /** Counts the work of one frame and, every 5 s, says how recognition keeps up with real time. */
    private void recordWork(long nanos) {
        statsFrames++;
        statsWorkNanos += nanos;
        statsMaxNanos = Math.max(statsMaxNanos, nanos);
        long now = System.nanoTime();
        long elapsed = now - statsSinceNanos;
        if (elapsed < 5_000_000_000L) {
            return;
        }
        double audioMs = statsFrames * 100.0;
        Trace.flow("vosk | last {} s: {} frames ({} ms of audio) in {} ms of work, slowest frame {} ms, real-time factor {}, "
                        + "queue {}, partials {}, finals {}, empty finals {}", elapsed / 1_000_000_000L, statsFrames, (long) audioMs,
                statsWorkNanos / 1_000_000L, statsMaxNanos / 1_000_000L,
                audioMs == 0 ? "-" : String.format("%.2f", statsWorkNanos / 1_000_000.0 / audioMs), frames.size(), statsPartials,
                statsFinals, statsEmptyFinals);
        statsSinceNanos = now;
        statsFrames = 0;
        statsWorkNanos = 0;
        statsMaxNanos = 0;
        statsPartials = 0;
        statsFinals = 0;
        statsEmptyFinals = 0;
    }

    /** Mean of the word confidences, or null if the result carries none. */
    private static Double meanConfidence(JsonNode words) {
        if (!words.isArray() || words.isEmpty()) {
            return null;
        }
        double sum = 0;
        int count = 0;
        for (JsonNode word : words) {
            if (word.hasNonNull("conf")) {
                sum += word.get("conf").asDouble();
                count++;
            }
        }
        return count == 0 ? null : Math.max(0, Math.min(1, sum / count));
    }
}
