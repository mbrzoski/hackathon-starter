package pl.aniolstroz.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.aniolstroz.contracts.ComponentState;

/** No model and no native library needed: only the paths that do not reach Vosk. */
class VoskModelHolderTest {

    @Test
    void nativeStringsAreDecodedAsUtf8WhateverThePlatformEncodingIs(@TempDir Path dir) {
        new VoskModelHolder(dir); // loading the class is what sets the property, before JNA is first used

        assertThat(System.getProperty("jna.encoding")).isEqualTo("UTF-8");
    }

    @Test
    void missingModelDirectoryGivesAPolishMessage(@TempDir Path dir) {
        var holder = new VoskModelHolder(dir.resolve("no-such-model"));

        assertThatThrownBy(holder::model)
                .isInstanceOf(SttUnavailableException.class)
                .hasMessage("Brak modelu rozpoznawania mowy");
    }

    @Test
    void closingBeforeLoadingIsHarmless(@TempDir Path dir) {
        var holder = new VoskModelHolder(dir);

        holder.close();
        holder.close();
    }

    @Test
    void fullFrameQueueReportsDegradedOnceAndDropsFrames(@TempDir Path dir) {
        List<ComponentState> reported = new ArrayList<>();
        var provider = new VoskSttProvider(new VoskModelHolder(dir), (state, message) -> {
            reported.add(state);
            assertThat(message).isEqualTo("Rozpoznawanie mowy nie nadąża");
        });

        // Not started: nothing consumes the queue, so it fills up.
        for (int i = 0; i < VoskSttProvider.QUEUE_CAPACITY + 20; i++) {
            provider.write(new byte[3200]);
        }

        assertThat(reported).containsExactly(ComponentState.DEGRADED);
    }

    @Test
    void startWithoutModelFails(@TempDir Path dir) {
        var provider = new VoskSttProvider(new VoskModelHolder(dir.resolve("none")), (state, message) -> { });

        assertThatThrownBy(() -> provider.start(s -> { }, e -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Brak modelu rozpoznawania mowy");
    }
}
