package pl.aniolstroz.tools;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * Developer tool: plays a WAV file into /ws/audio as a phone call would, to try LIVE mode without a microphone.
 *
 * <pre>
 * cd backend &amp;&amp; ./mvnw test-compile
 * java -cp target/test-classes pl.aniolstroz.tools.SendWavTool call.wav [ws://localhost:8080/ws/audio] [speed]
 * </pre>
 *
 * The file must be PCM, 16 kHz, mono, 16-bit. It is sent as {@code start}, frames of 3200 bytes (100 ms) every
 * 100 ms divided by {@code speed} (default 1; 10 means ten times faster), then {@code stop}. JDK only.
 */
public final class SendWavTool {

    static final int FRAME_BYTES = 3200;
    static final int FRAME_MS = 100;

    private SendWavTool() {
    }

    /** The PCM data of a WAV file, checked to be 16 kHz mono 16-bit. */
    record Wav(byte[] pcm) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: SendWavTool <file.wav> [ws://localhost:8080/ws/audio] [speed]");
            System.exit(2);
        }
        String url = args.length > 1 ? args[1] : "ws://localhost:8080/ws/audio";
        double speed = args.length > 2 ? Double.parseDouble(args[2]) : 1.0;
        if (speed <= 0) {
            System.err.println("speed must be positive");
            System.exit(2);
        }
        Wav wav;
        try {
            wav = parse(Files.readAllBytes(Path.of(args[0])));
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Cannot use " + args[0] + ": " + e.getMessage());
            System.exit(1);
            return;
        }
        System.exit(send(wav, url, speed));
    }

    /** Parses a RIFF/WAVE file; throws IllegalArgumentException for anything but PCM 16 kHz mono 16-bit. */
    static Wav parse(byte[] file) {
        ByteBuffer in = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        if (file.length < 12 || !tag(in, 0).equals("RIFF") || !tag(in, 8).equals("WAVE")) {
            throw new IllegalArgumentException("not a WAV file (no RIFF/WAVE header)");
        }
        boolean formatSeen = false;
        int pos = 12;
        while (pos + 8 <= file.length) {
            String id = tag(in, pos);
            long size = in.getInt(pos + 4) & 0xFFFFFFFFL;
            int body = pos + 8;
            if (id.equals("fmt ")) {
                if (size < 16 || body + 16 > file.length) {
                    throw new IllegalArgumentException("damaged fmt chunk");
                }
                int format = in.getShort(body) & 0xFFFF;
                int channels = in.getShort(body + 2);
                int rate = in.getInt(body + 4);
                int bits = in.getShort(body + 14);
                // 0xFFFE (extensible) is accepted as long as rate, channels and bits match.
                if ((format != 1 && format != 0xFFFE) || channels != 1 || rate != 16000 || bits != 16) {
                    throw new IllegalArgumentException("expected PCM 16 kHz mono 16-bit, got format " + format + ", "
                            + channels + " channel(s), " + rate + " Hz, " + bits + "-bit");
                }
                formatSeen = true;
            } else if (id.equals("data")) {
                if (!formatSeen) {
                    throw new IllegalArgumentException("data chunk before fmt chunk");
                }
                long available = file.length - body;
                int length = (int) Math.min(size, available); // streamed files may declare a bigger size
                byte[] pcm = new byte[length - (length % 2)];
                System.arraycopy(file, body, pcm, 0, pcm.length);
                return new Wav(pcm);
            }
            long next = body + size + (size % 2);
            if (next > file.length) {
                break;
            }
            pos = (int) next;
        }
        throw new IllegalArgumentException("no data chunk");
    }

    private static String tag(ByteBuffer in, int at) {
        byte[] id = new byte[4];
        in.get(at, id);
        return new String(id, StandardCharsets.US_ASCII);
    }

    /** Returns the process exit code. */
    static int send(Wav wav, String url, double speed) throws Exception {
        CompletableFuture<String> closed = new CompletableFuture<>();
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
                closed.complete(code + (reason.isEmpty() ? "" : " " + reason));
                return null;
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                closed.completeExceptionally(error);
            }
        };
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(url), listener).get(10, TimeUnit.SECONDS);
        ws.sendText("{\"type\":\"start\"}", true).get(10, TimeUnit.SECONDS);
        long pauseNanos = (long) (FRAME_MS * 1_000_000L / speed);
        long next = System.nanoTime();
        int frames = 0;
        for (int off = 0; off < wav.pcm().length && !closed.isDone(); off += FRAME_BYTES) {
            int len = Math.min(FRAME_BYTES, wav.pcm().length - off);
            byte[] frame = new byte[FRAME_BYTES]; // the last frame is padded with silence
            System.arraycopy(wav.pcm(), off, frame, 0, len);
            ws.sendBinary(ByteBuffer.wrap(frame), true).get(10, TimeUnit.SECONDS);
            frames++;
            next += pauseNanos;
            long wait = next - System.nanoTime();
            if (wait > 0) {
                TimeUnit.NANOSECONDS.sleep(wait);
            }
        }
        if (closed.isDone()) {
            System.err.println("The server closed the connection: " + closed.getNow("error"));
            return 1;
        }
        ws.sendText("{\"type\":\"stop\"}", true).get(10, TimeUnit.SECONDS);
        try {
            System.out.println("Sent " + frames + " frames; server closed with " + closed.get(10, TimeUnit.SECONDS));
        } catch (java.util.concurrent.ExecutionException e) {
            System.err.println("Connection error: " + e.getCause());
            return 1;
        }
        return 0;
    }
}
