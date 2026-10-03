package pl.aniolstroz.call;

/** Only one call can be active at a time (BE-04). */
public class CallAlreadyActiveException extends RuntimeException {

    public CallAlreadyActiveException() {
        super("A call is already active");
    }
}
