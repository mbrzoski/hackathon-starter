package pl.aniolstroz.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pl.aniolstroz.call.CallAlreadyActiveException;
import pl.aniolstroz.call.CallService;
import pl.aniolstroz.contracts.Mode;

/** BE-11: a recording goes through the recognizer into a REPLAY call; no Vosk, no model (TST-02). */
@SpringBootTest(properties = {"app.events.heartbeat-interval-ms=3600000", "app.stt.recordings-dir=target/test-recordings"})
class AudioReplayerTest {

    static final Path DIR = Path.of("target/test-recordings");

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        @Primary
        SttProviderFactory scriptedRecognizer() {
            return new SttProviderFactory() {
                @Override
                public void preflight() {
                }

                @Override
                public SttProvider create(SttStatusSink status) {
                    return new FakeSttProvider(List.of("dzień dobry mówi policja", "proszę wypłacić pieniądze"), 3);
                }
            };
        }
    }

    @Autowired
    AudioReplayer replayer;

    @Autowired
    CallService calls;

    static byte[] wav(int seconds, int rate, int channels) {
        int dataBytes = seconds * rate * 2 * channels;
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataBytes).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) channels).putInt(rate)
                .putInt(rate * 2 * channels).putShort((short) (2 * channels)).putShort((short) 16);
        b.put("data".getBytes()).putInt(dataBytes);
        return b.array();
    }

    @BeforeEach
    void recordings() throws IOException {
        Files.createDirectories(DIR);
        Files.write(DIR.resolve("01-test.wav"), wav(1, 16000, 1));
        Files.write(DIR.resolve("02-stereo.wav"), wav(1, 16000, 2));
    }

    @AfterEach
    void cleanUp() {
        replayer.stop();
        calls.end();
        await().atMost(5, TimeUnit.SECONDS).until(() -> calls.active().isEmpty());
    }

    @Test
    void knowsWhichScenariosHaveARecording() {
        assertThat(replayer.has("01-test")).isTrue();
        assertThat(replayer.has("03-none")).isFalse();
        assertThat(replayer.has("../etc/passwd")).isFalse();
    }

    @Test
    void theRecordingIsRecognisedIntoAReplayCallThatEndsByItself() throws Exception {
        replayer.start("01-test", Mode.REPLAY, 1); // one second of audio

        await().atMost(5, TimeUnit.SECONDS).until(() -> calls.active().isPresent());
        var call = calls.active().get();
        assertThat(call.mode()).isEqualTo(Mode.REPLAY);
        assertThat(call.scenarioId()).isEqualTo("01-test");
        await().atMost(10, TimeUnit.SECONDS).until(() -> calls.active().isEmpty());
    }

    @Test
    void onlyOneCallAtATime() throws Exception {
        replayer.start("01-test", Mode.REPLAY, 1);
        assertThatThrownBy(() -> replayer.start("01-test", Mode.REPLAY, 1)).isInstanceOf(CallAlreadyActiveException.class);
    }

    @Test
    void refusesAMissingOrWrongRecording() {
        assertThatThrownBy(() -> replayer.start("03-none", Mode.REPLAY, 1)).isInstanceOf(RecordingNotFoundException.class);
        assertThatThrownBy(() -> replayer.start("02-stereo", Mode.REPLAY, 1)).isInstanceOf(RecordingNotFoundException.class);
        assertThat(calls.active()).isEmpty();
    }

    @Test
    void readsOnlyPcm16kMono16bit() throws IOException {
        Path ok = DIR.resolve("01-test.wav");
        assertThat(AudioReplayer.readPcm(ok)).hasSize(32000);
        Path wrongRate = DIR.resolve("rate.wav");
        Files.write(wrongRate, wav(1, 44100, 1));
        assertThatThrownBy(() -> AudioReplayer.readPcm(wrongRate)).isInstanceOf(IOException.class);
    }
}
