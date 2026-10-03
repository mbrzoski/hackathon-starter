package pl.aniolstroz.stt;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hands work from the recognition thread to a virtual-thread executor and returns at once (AUD-04), keeping the order
 * of the tasks: only one drain task runs at a time. A queued interim task is replaced by the next interim one, so a
 * slow consumer sees the newest interim text and the backlog stays short.
 */
final class SegmentDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SegmentDispatcher.class);

    private record Item(Runnable task, boolean replaceable) {
    }

    private final Executor executor;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition idle = lock.newCondition();
    private final ArrayDeque<Item> queue = new ArrayDeque<>();
    private boolean running;

    SegmentDispatcher(Executor executor) {
        this.executor = executor;
    }

    void submit(Runnable task) {
        enqueue(new Item(task, false));
    }

    /** A task that a newer replaceable task may overtake while it still waits in the queue. */
    void submitReplaceable(Runnable task) {
        enqueue(new Item(task, true));
    }

    private void enqueue(Item item) {
        boolean start = false;
        lock.lock();
        try {
            if (item.replaceable() && !queue.isEmpty() && queue.peekLast().replaceable()) {
                queue.pollLast();
            }
            queue.addLast(item);
            if (!running) {
                running = true;
                start = true;
            }
        } finally {
            lock.unlock();
        }
        if (start) {
            try {
                executor.execute(this::drain);
            } catch (RuntimeException e) {
                // The executor is shut down (the application is stopping): nothing more can be delivered.
                lock.lock();
                try {
                    queue.clear();
                    running = false;
                    idle.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private void drain() {
        while (true) {
            Item item;
            lock.lock();
            try {
                item = queue.pollFirst();
                if (item == null) {
                    running = false;
                    idle.signalAll();
                    return;
                }
            } finally {
                lock.unlock();
            }
            try {
                item.task().run();
            } catch (Throwable t) {
                // Type only: the message may carry transcript text (OBS-05).
                log.error("STT task failed: {}", t.getClass().getName());
            }
        }
    }

    /** Waits until everything submitted so far has been run. Returns false on timeout. */
    boolean awaitIdle(Duration timeout) {
        long nanos = timeout.toNanos();
        lock.lock();
        try {
            while (running || !queue.isEmpty()) {
                if (nanos <= 0) {
                    return false;
                }
                nanos = idle.awaitNanos(nanos);
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.unlock();
        }
    }
}
