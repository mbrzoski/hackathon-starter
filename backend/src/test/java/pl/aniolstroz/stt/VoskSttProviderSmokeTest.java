package pl.aniolstroz.stt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs the real Vosk with the real model, so it is skipped wherever the model directory does not exist (it is not in
 * the repository; see scripts/download-vosk-model.sh). The path is APP_STT_VOSK_MODEL_PATH or the default
 * {@code models/vosk-model-small-pl-0.22}. It checks that native recognition starts, takes frames and stops cleanly;
 * silence must produce no text. Recognition quality is not tested here.
 */
@EnabledIf("modelExists")
class VoskSttProviderSmokeTest {

    static Path modelPath() {
        String fromEnv = System.getenv("APP_STT_VOSK_MODEL_PATH");
        return Path.of(fromEnv == null || fromEnv.isBlank() ? "models/vosk-model-small-pl-0.22" : fromEnv);
    }

    static boolean modelExists() {
        return Files.isDirectory(modelPath());
    }

    @Test
    void silenceIsRecognisedAsNothingAndTheRecognizerStopsCleanly() throws Exception {
        var holder = new VoskModelHolder(modelPath());
        try {
            List<SttSegment> segments = new CopyOnWriteArrayList<>();
            List<Throwable> errors = new CopyOnWriteArrayList<>();
            var provider = new VoskSttProvider(holder, (state, message) -> { });
            provider.start(segments::add, errors::add);

            for (int i = 0; i < 30; i++) { // 3 s of silence
                provider.write(new byte[3200]);
                Thread.sleep(10);
            }
            provider.stop();

            assertThat(errors).isEmpty();
            assertThat(segments).noneMatch(s -> !s.text().isBlank());
        } finally {
            holder.close();
        }
    }
}
