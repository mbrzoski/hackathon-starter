package pl.aniolstroz.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.events.EventBus;

/**
 * A listener of a final segment runs while the call is locked and may ask for the active call (the AI layer does).
 * That must not wait for the slot lock, which {@code end()} holds while it waits for the call lock.
 */
class CallServiceLockOrderTest {

    @Test
    void askingForTheActiveCallInAListenerDoesNotDeadlockWithEnd() throws Exception {
        var inListener = new CountDownLatch(1);
        CompletableFuture<Optional<CallState>> seenByListener = new CompletableFuture<>();
        CallService[] holder = new CallService[1];
        holder[0] = CallServices.create(mock(EventBus.class), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                new DiscardTranscriptHook(), () -> pl.aniolstroz.contracts.Sensitivity.STANDARD, event -> {
                    if (event instanceof FinalSegmentAdded) {
                        inListener.countDown();
                        try {
                            Thread.sleep(300); // long enough for end() to take the slot lock and wait for the call
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        seenByListener.complete(holder[0].active());
                    }
                });
        CallService service = holder[0];
        CallState call = service.start(Mode.SCRIPTED);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            var adding = pool.submit(() -> service.addSegment(
                    new TranscriptSegment(call.callId(), "x", 0, 10, "dzień dobry", true, SpeakerLabel.UNKNOWN, null)));
            assertThat(inListener.await(5, TimeUnit.SECONDS)).isTrue();
            var ending = pool.submit(() -> service.end());

            assertThat(seenByListener.get(5, TimeUnit.SECONDS)).containsSame(call);
            adding.get(5, TimeUnit.SECONDS);
            assertThat(ending.get(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(service.active()).isEmpty();
    }
}
