package pl.aniolstroz.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SendWavToolTest {

    private static byte[] wav(int format, int channels, int rate, int bits, byte[] data) {
        ByteBuffer b = ByteBuffer.allocate(44 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + data.length)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) format)
                .putShort((short) channels).putInt(rate).putInt(rate * channels * bits / 8)
                .putShort((short) (channels * bits / 8)).putShort((short) bits);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(data.length).put(data);
        return b.array();
    }

    @Test
    void acceptsPcm16kMono16BitAndReturnsTheData() {
        byte[] data = {1, 2, 3, 4, 5, 6};

        assertThat(SendWavTool.parse(wav(1, 1, 16000, 16, data)).pcm()).containsExactly(data);
    }

    @Test
    void skipsExtraChunksBeforeTheData() throws Exception {
        byte[] plain = wav(1, 1, 16000, 16, new byte[] {9, 9});
        var out = new ByteArrayOutputStream();
        out.write(plain, 0, 36);
        out.write("LIST".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[] {3, 0, 0, 0, 'a', 'b', 'c', 0}); // odd size, one pad byte
        out.write(plain, 36, plain.length - 36);

        assertThat(SendWavTool.parse(out.toByteArray()).pcm()).containsExactly(9, 9);
    }

    @Test
    void rejectsOtherSampleRatesChannelsAndBitDepths() {
        for (byte[] file : new byte[][] {
                wav(1, 1, 44100, 16, new byte[2]), wav(1, 2, 16000, 16, new byte[4]),
                wav(1, 1, 16000, 8, new byte[2]), wav(3, 1, 16000, 16, new byte[2])}) {
            assertThatThrownBy(() -> SendWavTool.parse(file))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expected PCM 16 kHz mono 16-bit");
        }
    }

    @Test
    void rejectsFilesThatAreNotWav() {
        assertThatThrownBy(() -> SendWavTool.parse("hello world, not a wav".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a WAV file");
    }
}
