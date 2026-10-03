package pl.aniolstroz.call;

/** The no-alert rule of DAT-01: the whole transcript is deleted when the call ends. */
public class DiscardTranscriptHook implements CallEndedHook {

    @Override
    public void onCallEnded(CallState state) {
        state.clearTranscript();
    }
}
