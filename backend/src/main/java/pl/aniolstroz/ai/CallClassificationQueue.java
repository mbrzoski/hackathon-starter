package pl.aniolstroz.ai;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The queue of one call (AI-02): at most one classifier call in flight. A segment that arrives meanwhile only sets the
 * dirty flag; when the running call ends, one follow-up call classifies the whole transcript, however many segments
 * came in. Runs on the given executor (virtual threads) and uses a {@link ReentrantLock}, never {@code synchronized}
 * (CC-03). It holds no call lock while classifying and takes its own lock only for the flags, so a caller that holds
 * a call lock can safely call {@link #segmentAdded()}.
 */
public final class CallClassificationQueue {

    private static final Logger log = LoggerFactory.getLogger(CallClassificationQueue.class);

    private final StageClassifier classifier;
    private final Supplier<Optional<CallSnapshot>> snapshots;
    private final Consumer<ClassifierResult> results;
    private final Executor executor;

    private final ReentrantLock lock = new ReentrantLock();
    private boolean running;
    private boolean dirty;

    /**
     * @param snapshots the transcript so far, taken fresh for every call; empty when the call is over
     * @param results receives every result, including failures; must not throw
     */
    public CallClassificationQueue(StageClassifier classifier, Supplier<Optional<CallSnapshot>> snapshots,
            Consumer<ClassifierResult> results, Executor executor) {
        this.classifier = classifier;
        this.snapshots = snapshots;
        this.results = results;
        this.executor = executor;
    }

    /** Call after a final segment was added. Returns at once. */
    public void segmentAdded() {
        lock.lock();
        try {
            if (running) {
                dirty = true;
                return;
            }
            running = true;
        } finally {
            lock.unlock();
        }
        executor.execute(this::drain);
    }

    private void drain() {
        try {
            while (true) {
                lock.lock();
                try {
                    // Only segments that arrive after this point need another call: the snapshot below has the rest.
                    dirty = false;
                } finally {
                    lock.unlock();
                }
                Optional<CallSnapshot> snapshot = snapshots.get();
                if (snapshot.isPresent()) {
                    results.accept(classify(snapshot.get()));
                }
                lock.lock();
                try {
                    if (!dirty) {
                        running = false;
                        return;
                    }
                } finally {
                    lock.unlock();
                }
            }
        } catch (RuntimeException e) {
            lock.lock();
            try {
                running = false;
            } finally {
                lock.unlock();
            }
            log.error("Classification queue stopped by {}", e.getClass().getName());
        }
    }

    private ClassifierResult classify(CallSnapshot snapshot) {
        try {
            return classifier.classify(snapshot);
        } catch (RuntimeException e) {
            // Type only: messages may carry transcript text.
            log.error("Classifier threw {}", e.getClass().getName());
            return ClassifierResult.failed(snapshot, ClassifierError.INTERNAL);
        }
    }
}
