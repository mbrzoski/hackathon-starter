package pl.aniolstroz.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.aniolstroz.contracts.SpeakerLabel;

class FakeSttProviderTest {

    @Test
    void turnsFramesIntoInterimThenFinalSegmentsWithTimesFromTheFrameCount() {
        var provider = new FakeSttProvider(List.of("dzień dobry", "mówi policja"), 3);
        List<SttSegment> segments = new ArrayList<>();
        provider.start(segments::add, error -> { });

        for (int i = 0; i < 7; i++) {
            provider.write(new byte[3200]);
        }

        assertThat(segments).extracting(SttSegment::text, SttSegment::isFinal, SttSegment::tStartMs, SttSegment::tEndMs)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("dzień dobry", false, 0L, 100L),
                        org.assertj.core.groups.Tuple.tuple("dzień dobry", false, 0L, 200L),
                        org.assertj.core.groups.Tuple.tuple("dzień dobry", true, 0L, 300L),
                        org.assertj.core.groups.Tuple.tuple("mówi policja", false, 300L, 400L),
                        org.assertj.core.groups.Tuple.tuple("mówi policja", false, 300L, 500L),
                        org.assertj.core.groups.Tuple.tuple("mówi policja", true, 300L, 600L));
        assertThat(segments).allMatch(s -> s.speaker() == SpeakerLabel.UNKNOWN && s.sttConfidence() == null);
    }

    @Test
    void ignoresFramesBeforeStartAfterStopAndBeyondTheScript() {
        var provider = new FakeSttProvider(List.of("raz"), 1);
        List<SttSegment> segments = new ArrayList<>();

        provider.write(new byte[3200]);
        provider.start(segments::add, error -> { });
        provider.write(new byte[3200]);
        provider.write(new byte[3200]);
        provider.stop();
        provider.write(new byte[3200]);

        assertThat(segments).hasSize(1);
        assertThat(provider.isStopped()).isTrue();
    }

    @Test
    void failReportsTheErrorToTheListener() {
        var provider = new FakeSttProvider(List.of());
        List<Throwable> errors = new ArrayList<>();
        provider.start(s -> { }, errors::add);

        var boom = new IllegalStateException("boom");
        provider.fail(boom);

        assertThat(errors).containsExactly(boom);
    }

    @Test
    void segmentToStringDoesNotShowTheText() {
        var segment = new SttSegment(0, 100, "sekretny tekst", true, SpeakerLabel.UNKNOWN, null);

        assertThat(segment.toString()).doesNotContain("sekretny");
    }

    @Test
    void rejectsZeroFramesPerUtterance() {
        assertThatThrownBy(() -> new FakeSttProvider(List.of("a"), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
