package pl.aniolstroz.alerts;

/** The decision is well-formed but this actor cannot make it, or it carries something only the family may send. */
public class InvalidDecisionException extends RuntimeException {

    public InvalidDecisionException(String message) {
        super(message);
    }
}
