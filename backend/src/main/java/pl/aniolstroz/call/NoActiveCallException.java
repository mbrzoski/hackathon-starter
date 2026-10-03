package pl.aniolstroz.call;

/** The call a segment belongs to is not (or no longer) the active call. */
public class NoActiveCallException extends RuntimeException {

    public NoActiveCallException() {
        super("No such active call");
    }
}
