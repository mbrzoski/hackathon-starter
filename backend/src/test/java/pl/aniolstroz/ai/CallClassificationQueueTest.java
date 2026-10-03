package pl.aniolstroz.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.TranscriptSegment;

/** AI-02: at most one call in flight per call; segments that arrive meanwhile cause exactly one more call. */
class CallClassificationQueueTest {

    private static final long WAIT_SECONDS = 5;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final List<TranscriptSegment> transcript = new CopyOnWriteArrayList<>();
    private final List<CallSnapshot> classified = new CopyOnWriteArrayList<>();
    private final List<ClassifierResult> delivered = new CopyOnWriteArrayList<>();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger maxRunning = new AtomicInteger();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private void addSegment() {
        int n = transcript.size() + 1;
        transcript.add(new TranscriptSegment("c", "s" + n, 0, 0, "tekst " + n, true, SpeakerLabel.B, null));
    }

    private static ClassifierResult emptyResult(CallSnapshot snapshot) {
        return new ClassifierResult("s1-" + snapshot.lastSegmentId(), "fake", "low", "{}", List.of(),
                new ClassifierResult.Usage(0, 0, 0), 1, "end_turn", null);
    }

    private CallClassificationQueue queue(StageClassifier classifier) {
        return new CallClassificationQueue(classifier,
                () -> Optional.of(new CallSnapshot("c", Mode.SCRIPTED, null, List.copyOf(transcript))),
                delivered::add, executor);
    }

    private StageClassifier recording() {
        return snapshot -> {
            classified.add(snapshot);
            return emptyResult(snapshot);
        };
    }

    /** Records every call and the concurrency; the first call blocks until released. */
    private final class GatedClassifier implements StageClassifier {
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        public ClassifierResult classify(CallSnapshot snapshot) {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            classified.add(snapshot);
            firstStarted.countDown();
            try {
                if (classified.size() == 1) {
                    release.await(WAIT_SECONDS, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
            }
            return emptyResult(snapshot);
        }
    }

    private void awaitDelivered(int count) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (delivered.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(delivered).hasSize(count);
    }

    @Test
    void oneSegmentGivesOneCall() throws Exception {
        CallClassificationQueue queue = queue(recording());
        addSegment();

        queue.segmentAdded();
        awaitDelivered(1);

        assertThat(classified).hasSize(1);
        assertThat(classified.get(0).segments()).hasSize(1);
    }

    @Test
    void twoSegmentsDuringACallGiveOneFollowUpCallWithTheWholeTranscript() throws Exception {
        GatedClassifier classifier = new GatedClassifier();
        CallClassificationQueue queue = queue(classifier);
        addSegment();
        queue.segmentAdded();
        assertThat(classifier.firstStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        addSegment();
        queue.segmentAdded();
        addSegment();
        queue.segmentAdded();
        classifier.release.countDown();
        awaitDelivered(2);
        Thread.sleep(150);

        assertThat(classified).hasSize(2);
        assertThat(classified.get(0).segments()).hasSize(1);
        assertThat(classified.get(1).segments()).extracting(TranscriptSegment::segId).containsExactly("s1", "s2", "s3");
        assertThat(maxRunning.get()).isEqualTo(1);
    }

    @Test
    void callsNeverOverlapEvenWithManySegmentsArrivingTogether() throws Exception {
        GatedClassifier classifier = new GatedClassifier();
        CallClassificationQueue queue = queue(classifier);
        addSegment();
        queue.segmentAdded();
        assertThat(classifier.firstStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        for (int i = 0; i < 20; i++) {
            addSegment();
            queue.segmentAdded();
        }
        classifier.release.countDown();
        awaitDelivered(2);
        Thread.sleep(150);

        assertThat(maxRunning.get()).isEqualTo(1);
        assertThat(classified).hasSize(2);
        assertThat(classified.get(1).segments()).hasSize(21);
    }

    @Test
    void afterTheQueueDrainsTheNextSegmentStartsANewCall() throws Exception {
        CallClassificationQueue queue = queue(recording());
        addSegment();
        queue.segmentAdded();
        awaitDelivered(1);

        addSegment();
        queue.segmentAdded();
        awaitDelivered(2);

        assertThat(classified).hasSize(2);
        assertThat(classified.get(1).segments()).hasSize(2);
    }

    @Test
    void aSingleSegmentIsNotClassifiedTwice() throws Exception {
        CallClassificationQueue queue = queue(recording());
        addSegment();

        queue.segmentAdded();
        awaitDelivered(1);
        Thread.sleep(100);

        assertThat(classified).hasSize(1);
    }

    @Test
    void aClassifierThatThrowsDoesNotStopTheQueue() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CallClassificationQueue queue = queue(snapshot -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
            return emptyResult(snapshot);
        });
        addSegment();
        queue.segmentAdded();
        awaitDelivered(1);

        assertThat(delivered.get(0).error()).isEqualTo(ClassifierError.INTERNAL);
        assertThat(delivered.get(0).hits()).isEmpty();

        addSegment();
        queue.segmentAdded();
        awaitDelivered(2);
        assertThat(delivered.get(1).error()).isNull();
    }

    @Test
    void noSnapshotMeansNoCall() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CallClassificationQueue queue = new CallClassificationQueue(snapshot -> {
            calls.incrementAndGet();
            return emptyResult(snapshot);
        }, Optional::empty, delivered::add, executor);

        queue.segmentAdded();
        Thread.sleep(100);

        assertThat(calls.get()).isZero();
        assertThat(delivered).isEmpty();
    }
}
