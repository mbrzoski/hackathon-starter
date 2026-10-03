package pl.aniolstroz.call;

/** Published after a final segment was added to a call. Listeners must return quickly: the call is locked. */
public record FinalSegmentAdded(String callId) {
}
