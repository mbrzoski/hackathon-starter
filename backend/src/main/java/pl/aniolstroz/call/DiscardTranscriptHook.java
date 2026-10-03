package pl.aniolstroz.call;

import org.springframework.stereotype.Component;

/** Default hook (DAT-01): the whole transcript is deleted when the call ends. Alerted calls will keep excerpts (BE-04). */
@Component
public class DiscardTranscriptHook implements CallEndedHook {

    @Override
    public void onCallEnded(CallState state) {
        state.clearTranscript();
    }
}
