package pl.aniolstroz.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.aniolstroz.contracts.Component;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.SpeakerLabel;

/** OBS-02: three restarts after 1, 2 and 4 s, then down; a working restart brings ok back. No real recognizer. */
class SttSupervisorTest {

    /** Moves only when told to. */
    static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-03T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Provider whose start can be made to fail; keeps its listeners so a test can make it fail later. */
    static final class StubProvider implements SttProvider {
        final boolean failToStart;
        final List<byte[]> written = new ArrayList<>();
        SegmentListener segments;
        ErrorListener errors;
        boolean stopped;

        StubProvider(boolean failToStart) {
            this.failToStart = failToStart;
        }

        @Override
        public void start(SegmentListener s, ErrorListener e) {
            if (failToStart) {
                throw new IllegalStateException("cannot create the recognizer");
            }
            segments = s;
            errors = e;
        }

        @Override
        public void write(byte[] pcm) {
            written.add(pcm);
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    /** Hands out the prepared providers one by one. */
    static final class StubFactory implements SttProviderFactory {
        final List<StubProvider> created = new ArrayList<>();
        final List<Boolean> plan;

        StubFactory(Boolean... failToStart) {
            this.plan = List.of(failToStart);
        }

        @Override
        public void preflight() {
        }

        @Override
        public SttProvider create(SttStatusSink status) {
            var provider = new StubProvider(created.size() < plan.size() && plan.get(created.size()));
            created.add(provider);
            return provider;
        }
    }

    record Published(Component component, ComponentState state, String message) {
    }

    /** Records the delay and keeps the task until the test runs it. */
    static final class ManualScheduler implements RetryScheduler {
        final List<Duration> delays = new ArrayList<>();
        final List<Runnable> tasks = new ArrayList<>();

        @Override
        public void schedule(Duration delay, Runnable task) {
            delays.add(delay);
            tasks.add(task);
        }

        void runNext() {
            tasks.remove(0).run();
        }
    }

    private final TestClock clock = new TestClock();
    private final ManualScheduler scheduler = new ManualScheduler();
    private final List<Published> statuses = new ArrayList<>();
    private final List<SttSegment> segments = new ArrayList<>();

    @BeforeEach
    void reset() {
        statuses.clear();
        segments.clear();
    }

    private SttSupervisor supervisor(StubFactory factory) {
        return new SttSupervisor(factory, segments::add,
                (component, state, message) -> statuses.add(new Published(component, state, message)),
                clock, scheduler, Runnable::run);
    }

    @Test
    void threeFailedRestartsAfterOneTwoAndFourSecondsEndInDown() {
        var factory = new StubFactory(false, true, true, true);
        var supervisor = supervisor(factory);
        supervisor.start();

        factory.created.get(0).errors.onError(new IllegalStateException("native failure"));
        assertThat(scheduler.delays).containsExactly(Duration.ofSeconds(1));
        scheduler.runNext(); // attempt 1 fails to start
        assertThat(scheduler.delays).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
        scheduler.runNext(); // attempt 2 fails
        assertThat(scheduler.delays)
                .containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
        assertThat(statuses).extracting(Published::state).containsExactly(ComponentState.DEGRADED);

        scheduler.runNext(); // attempt 3 fails: give up

        assertThat(factory.created).hasSize(4);
        assertThat(scheduler.tasks).isEmpty();
        assertThat(scheduler.delays).hasSize(3);
        assertThat(statuses).extracting(Published::component, Published::state, Published::message)
                .containsExactly(
                        tuple(Component.STT, ComponentState.DEGRADED, "Rozpoznawanie mowy chwilowo niedostępne"),
                        tuple(Component.STT, ComponentState.DOWN, "Rozpoznawanie mowy niedostępne. Ochrona nie działa"));
    }

    @Test
    void aRestartThatWorksRestoresOkAndFramesFlowAgain() {
        var factory = new StubFactory(false, false);
        var supervisor = supervisor(factory);
        supervisor.start();
        factory.created.get(0).errors.onError(new IllegalStateException("boom"));

        supervisor.write(new byte[3200]); // while restarting: dropped
        scheduler.runNext();
        supervisor.write(new byte[3200]);

        assertThat(statuses).extracting(Published::state)
                .containsExactly(ComponentState.DEGRADED, ComponentState.OK);
        assertThat(factory.created.get(0).written).isEmpty();
        assertThat(factory.created.get(1).written).hasSize(1);
        assertThat(factory.created.get(0).stopped).isTrue();
    }

    @Test
    void failuresSoonAfterARestartKeepCountingButHealthyTimeStartsOver() {
        var factory = new StubFactory();
        var supervisor = supervisor(factory);
        supervisor.start();

        factory.created.get(0).errors.onError(new IllegalStateException());
        scheduler.runNext(); // works
        clock.advance(Duration.ofSeconds(5));
        factory.created.get(1).errors.onError(new IllegalStateException()); // soon after: second attempt
        assertThat(scheduler.delays).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));

        scheduler.runNext();
        clock.advance(Duration.ofSeconds(31));
        factory.created.get(2).errors.onError(new IllegalStateException()); // healthy for 31 s: starts over
        assertThat(scheduler.delays.get(2)).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void oldProviderFailingAfterItsReplacementIsIgnored() {
        var factory = new StubFactory();
        var supervisor = supervisor(factory);
        supervisor.start();
        var first = factory.created.get(0);
        first.errors.onError(new IllegalStateException());
        scheduler.runNext();

        first.errors.onError(new IllegalStateException());

        assertThat(scheduler.tasks).isEmpty();
        assertThat(statuses).extracting(Published::state)
                .containsExactly(ComponentState.DEGRADED, ComponentState.OK);
    }

    @Test
    void aProviderStatusIsPublishedAsSttStatusOnlyWhenItChanges() {
        var factory = new StubFactory();
        var supervisor = supervisor(factory);
        var captured = new SttStatusSink[1];
        var capturing = new SttProviderFactory() {
            @Override
            public void preflight() {
            }

            @Override
            public SttProvider create(SttStatusSink status) {
                captured[0] = status;
                return factory.create(status);
            }
        };
        supervisor = new SttSupervisor(capturing, segments::add,
                (component, state, message) -> statuses.add(new Published(component, state, message)),
                clock, scheduler, Runnable::run);
        supervisor.start();

        captured[0].onStatus(ComponentState.DEGRADED, "Rozpoznawanie mowy nie nadąża");
        captured[0].onStatus(ComponentState.DEGRADED, "Rozpoznawanie mowy nie nadąża");
        captured[0].onStatus(ComponentState.OK, "Rozpoznawanie mowy działa");

        assertThat(statuses).extracting(Published::state)
                .containsExactly(ComponentState.DEGRADED, ComponentState.OK);
    }

    @Test
    void stopDeliversTheLastSegmentsAndNoRestartFollows() {
        var factory = new StubFactory();
        var supervisor = supervisor(factory);
        supervisor.start();
        var provider = factory.created.get(0);

        provider.segments.onSegment(new SttSegment(0, 500, "dzień dobry", false, SpeakerLabel.UNKNOWN, null));
        provider.segments.onSegment(new SttSegment(0, 900, "dzień dobry pani", true, SpeakerLabel.UNKNOWN, null));
        supervisor.stop();
        provider.errors.onError(new IllegalStateException("late"));

        assertThat(segments).extracting(SttSegment::text).containsExactly("dzień dobry", "dzień dobry pani");
        assertThat(provider.stopped).isTrue();
        assertThat(scheduler.tasks).isEmpty();
        assertThat(statuses).isEmpty();
    }
}
