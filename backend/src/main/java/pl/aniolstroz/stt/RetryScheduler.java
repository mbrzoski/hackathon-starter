package pl.aniolstroz.stt;

import java.time.Duration;

/** Runs a task after a delay. A seam so the recogniser retries (1, 2, 4 s) can be tested without waiting. */
@FunctionalInterface
public interface RetryScheduler {

    void schedule(Duration delay, Runnable task);
}
