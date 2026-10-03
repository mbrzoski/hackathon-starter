package pl.aniolstroz.call;

/**
 * Published after a final segment was added to a call. Listeners must return quickly: the call is locked. The event
 * carries the call itself, so a listener never has to look the call up: that would take the slot lock while the call
 * lock is held, the reverse of the order {@link CallService} uses (CC-01).
 */
public record FinalSegmentAdded(CallState call) {

    public String callId() {
        return call.callId();
    }
}
