package pl.aniolstroz.stt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import pl.aniolstroz.stt.SilenceWatch.Change;

/** OBS-02: no frames, or only flat ones, for the whole timeout mean the audio is lost. */
class SilenceWatchTest {

    /** Moves only when told to. */
    static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-03T12:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final TestClock clock = new TestClock();
    private final SilenceWatch watch = new SilenceWatch(clock, TIMEOUT);

    private static byte[] flat() {
        return new byte[3200];
    }

    /** One loud sample (little endian 16-bit) in an otherwise flat frame. */
    private static byte[] withSignal(int sample) {
        byte[] frame = new byte[3200];
        frame[100] = (byte) sample;
        frame[101] = (byte) (sample >> 8);
        return frame;
    }

    @Test
    void noFramesForTheWholeTimeoutIsSilence() {
        clock.advance(TIMEOUT.minusMillis(1));
        assertThat(watch.check()).isEqualTo(Change.NONE);

        clock.advance(Duration.ofMillis(1));
        assertThat(watch.check()).isEqualTo(Change.SILENT);
    }

    @Test
    void silenceIsReportedOnlyOnce() {
        clock.advance(TIMEOUT);
        assertThat(watch.check()).isEqualTo(Change.SILENT);

        clock.advance(TIMEOUT);
        assertThat(watch.check()).isEqualTo(Change.NONE);
    }

    @Test
    void flatFramesAreSilenceToo() {
        for (int i = 0; i < 101; i++) { // 10.1 s of frames, all flat
            clock.advance(Duration.ofMillis(100));
            assertThat(watch.onFrame(flat())).isEqualTo(Change.NONE);
        }

        assertThat(watch.check()).isEqualTo(Change.SILENT);
    }

    @Test
    void frameWithSignalRestartsTheCountdown() {
        clock.advance(Duration.ofSeconds(9));
        assertThat(watch.onFrame(withSignal(500))).isEqualTo(Change.NONE);

        clock.advance(Duration.ofSeconds(9));
        assertThat(watch.check()).isEqualTo(Change.NONE);

        clock.advance(Duration.ofSeconds(1));
        assertThat(watch.check()).isEqualTo(Change.SILENT);
    }

    @Test
    void soundAfterReportedSilenceIsRecoveredOnce() {
        clock.advance(TIMEOUT);
        assertThat(watch.check()).isEqualTo(Change.SILENT);

        assertThat(watch.onFrame(flat())).isEqualTo(Change.NONE);
        assertThat(watch.onFrame(withSignal(-500))).isEqualTo(Change.RECOVERED);
        assertThat(watch.onFrame(withSignal(500))).isEqualTo(Change.NONE);
    }

    @Test
    void aPauseIsNotSilenceAndTheTimeSpentPausedDoesNotCount() {
        watch.pause();
        clock.advance(Duration.ofMinutes(5));
        assertThat(watch.check()).isEqualTo(Change.NONE);

        watch.resume();
        clock.advance(TIMEOUT.minusMillis(1));
        assertThat(watch.check()).isEqualTo(Change.NONE);

        clock.advance(Duration.ofMillis(1));
        assertThat(watch.check()).isEqualTo(Change.SILENT);
    }

    @Test
    void resumeAfterReportedSilenceStartsFresh() {
        clock.advance(TIMEOUT);
        assertThat(watch.check()).isEqualTo(Change.SILENT);

        watch.pause();
        watch.resume();
        clock.advance(TIMEOUT);

        assertThat(watch.check()).isEqualTo(Change.SILENT);
    }

    @Test
    void theThresholdIsTheLoudestSampleOfTheFrameAndNegativeSamplesCount() {
        assertThat(SilenceWatch.hasSignal(flat())).isFalse();
        assertThat(SilenceWatch.hasSignal(withSignal(SilenceWatch.SIGNAL_THRESHOLD - 1))).isFalse();
        assertThat(SilenceWatch.hasSignal(withSignal(SilenceWatch.SIGNAL_THRESHOLD))).isTrue();
        assertThat(SilenceWatch.hasSignal(withSignal(-SilenceWatch.SIGNAL_THRESHOLD))).isTrue();
        assertThat(SilenceWatch.hasSignal(withSignal(-32768))).isTrue();
        assertThat(SilenceWatch.hasSignal(new byte[0])).isFalse();
    }
}
