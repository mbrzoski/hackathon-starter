package pl.aniolstroz.stt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class SegmentDispatcherTest {

    @Test
    void tasksRunInOrderOnTheExecutorNotOnTheCallingThread() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var dispatcher = new SegmentDispatcher(executor);
            List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
            List<Boolean> onCaller = Collections.synchronizedList(new ArrayList<>());
            Thread caller = Thread.currentThread();

            for (int i = 0; i < 200; i++) {
                int n = i;
                dispatcher.submit(() -> {
                    seen.add(n);
                    onCaller.add(Thread.currentThread() == caller);
                });
            }

            assertThat(dispatcher.awaitIdle(Duration.ofSeconds(5))).isTrue();
            assertThat(seen).hasSize(200).isSorted();
            assertThat(onCaller).doesNotContain(true);
        }
    }

    @Test
    void aWaitingInterimTaskIsReplacedByTheNextInterimButNeverByAFinalOne() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var dispatcher = new SegmentDispatcher(executor);
            var release = new CountDownLatch(1);
            List<String> seen = Collections.synchronizedList(new ArrayList<>());
            dispatcher.submit(() -> {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            dispatcher.submitReplaceable(() -> seen.add("interim 1"));
            dispatcher.submitReplaceable(() -> seen.add("interim 2"));
            dispatcher.submit(() -> seen.add("final"));
            dispatcher.submitReplaceable(() -> seen.add("interim 3"));
            release.countDown();

            assertThat(dispatcher.awaitIdle(Duration.ofSeconds(5))).isTrue();
            assertThat(seen).containsExactly("interim 2", "final", "interim 3");
        }
    }

    @Test
    void aFailingTaskDoesNotStopTheOnesAfterIt() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var dispatcher = new SegmentDispatcher(executor);
            List<String> seen = Collections.synchronizedList(new ArrayList<>());

            dispatcher.submit(() -> {
                throw new IllegalStateException("fails");
            });
            dispatcher.submit(() -> seen.add("next"));

            assertThat(dispatcher.awaitIdle(Duration.ofSeconds(5))).isTrue();
            assertThat(seen).containsExactly("next");
        }
    }
}
