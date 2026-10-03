package pl.aniolstroz.call;

/** Runs once when a call ends, before call.ended is published. Decides what happens to the transcript. */
@FunctionalInterface
public interface CallEndedHook {

    void onCallEnded(CallState state);
}
