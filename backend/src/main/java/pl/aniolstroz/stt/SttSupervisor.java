package pl.aniolstroz.stt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.aniolstroz.contracts.Component;
import pl.aniolstroz.contracts.ComponentState;

/**
 * Keeps speech recognition alive for one call (OBS-02). It starts the provider, passes frames to it and, when the
 * provider fails, creates a new one up to three times, after 1, 2 and 4 seconds. While that goes on, {@code stt} is
 * degraded; after the third failed attempt it is down; a restart that works makes it ok again. Every change is
 * published as {@code system.status} (OBS-01): a deaf system never shows green.
 *
 * <p>Whatever a provider reports, from its own thread, is queued to a {@link SegmentDispatcher}, which runs it on a
 * virtual thread and returns at once, so the recognition thread never calls Claude or does blocking I/O (AUD-04).
 * Failures that come less than {@value #HEALTHY_SECONDS} s after the last successful start do not reset the count of
 * attempts, so a recognizer that fails right away cannot restart forever.
 */
public final class SttSupervisor {

    private static final Logger log = LoggerFactory.getLogger(SttSupervisor.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long HEALTHY_SECONDS = 30;
    private static final Duration HEALTHY = Duration.ofSeconds(HEALTHY_SECONDS);
    private static final Duration FIRST_DELAY = Duration.ofSeconds(1);

    static final String RUNNING = "Rozpoznawanie mowy działa";
    static final String DEGRADED = "Rozpoznawanie mowy chwilowo niedostępne";
    static final String DOWN = "Rozpoznawanie mowy niedostępne. Ochrona nie działa";

    private final SttProviderFactory factory;
    private final SttProvider.SegmentListener downstream;
    private final StatusPublisher status;
    private final Clock clock;
    private final RetryScheduler scheduler;
    private final SegmentDispatcher dispatcher;

    private final ReentrantLock lock = new ReentrantLock();
    private SttProvider provider;
    private int generation;
    private int attempts;
    private Instant lastStartedAt;
    private boolean closed;
    /** What was last published; the caller of {@link #start()} has just published "running". */
    private ComponentState lastState = ComponentState.OK;
    private String lastMessage = RUNNING;

    /**
     * @param downstream gets every segment, on a virtual thread, in order
     * @param executor runs the hand-over; in production a virtual-thread executor
     */
    public SttSupervisor(SttProviderFactory factory, SttProvider.SegmentListener downstream, StatusPublisher status,
            Clock clock, RetryScheduler scheduler, Executor executor) {
        this.factory = factory;
        this.downstream = downstream;
        this.status = status;
        this.clock = clock;
        this.scheduler = scheduler;
        this.dispatcher = new SegmentDispatcher(executor);
    }

    /** Starts the first provider. A failure here is the caller's to handle (no retries: the call has not begun). */
    public void start() {
        lock.lock();
        try {
            launch();
        } finally {
            lock.unlock();
        }
    }

    /** Never blocks. Frames are dropped while no provider runs (after a failure, until the restart). */
    public void write(byte[] pcm) {
        SttProvider current = provider;
        if (current != null) {
            current.write(pcm);
        }
    }

    /** Stops recognition, delivers the last segments and waits (briefly) until downstream has handled them. */
    public void stop() {
        SttProvider current;
        lock.lock();
        try {
            closed = true;
            current = provider;
            provider = null;
        } finally {
            lock.unlock();
        }
        if (current != null) {
            current.stop();
        }
        if (!dispatcher.awaitIdle(Duration.ofSeconds(3))) {
            log.warn("STT hand-over did not finish within 3 s");
        }
    }

    /** Caller holds the lock. */
    private void launch() {
        int gen = ++generation;
        SttProvider created = factory.create((state, message) ->
                dispatcher.submit(() -> providerStatus(gen, state, message)));
        created.start(
                segment -> {
                    if (segment.isFinal()) {
                        dispatcher.submit(() -> downstream.onSegment(segment));
                    } else {
                        dispatcher.submitReplaceable(() -> downstream.onSegment(segment));
                    }
                },
                error -> dispatcher.submit(() -> failed(gen)));
        provider = created;
        lastStartedAt = clock.instant();
    }

    private void providerStatus(int gen, ComponentState state, String message) {
        lock.lock();
        try {
            if (!closed && gen == generation) {
                publish(state, message);
            }
        } finally {
            lock.unlock();
        }
    }

    /** The provider of generation {@code gen} failed, or a restart could not start. Runs on a virtual thread. */
    private void failed(int gen) {
        SttProvider old;
        lock.lock();
        try {
            if (closed || gen != generation) {
                return;
            }
            old = provider;
            provider = null;
            Instant now = clock.instant();
            if (lastStartedAt != null && Duration.between(lastStartedAt, now).compareTo(HEALTHY) >= 0) {
                attempts = 0;
            }
            if (attempts >= MAX_ATTEMPTS) {
                publish(ComponentState.DOWN, DOWN);
                log.error("Speech recognition is down after {} restarts", MAX_ATTEMPTS);
            } else {
                Duration delay = FIRST_DELAY.multipliedBy(1L << attempts);
                attempts++;
                publish(ComponentState.DEGRADED, DEGRADED);
                scheduler.schedule(delay, this::restart);
            }
        } finally {
            lock.unlock();
        }
        if (old != null) {
            try {
                old.stop();
            } catch (RuntimeException e) {
                log.warn("Stopping the failed recognizer: {}", e.getClass().getName());
            }
        }
    }

    /** Caller holds the lock. Publishes only a change, so a repeated failure does not repeat the same status. */
    private void publish(ComponentState state, String message) {
        if (state == lastState && message.equals(lastMessage)) {
            return;
        }
        lastState = state;
        lastMessage = message;
        status.publish(Component.STT, state, message);
    }

    private void restart() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            try {
                launch();
            } catch (RuntimeException | Error e) {
                // Type only: messages may carry recognised text (OBS-05).
                log.warn("Recognizer restart failed: {}", e.getClass().getName());
                int gen = generation;
                dispatcher.submit(() -> failed(gen));
                return;
            }
            publish(ComponentState.OK, RUNNING);
        } finally {
            lock.unlock();
        }
    }
}
