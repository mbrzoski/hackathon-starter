package pl.aniolstroz.alerts;

public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(String alertId) {
        super("Unknown alert '" + alertId + "'");
    }
}
