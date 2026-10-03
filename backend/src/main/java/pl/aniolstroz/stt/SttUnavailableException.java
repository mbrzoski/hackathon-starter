package pl.aniolstroz.stt;

/** Speech recognition cannot start (no model, native library missing). The message is Polish and shown to the user. */
public class SttUnavailableException extends Exception {

    public SttUnavailableException(String message) {
        super(message);
    }

    public SttUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
