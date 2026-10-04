package pl.aniolstroz.stt;

/** There is no usable recording for the scenario (REPLAY needs recordings/&lt;scenarioId&gt;.wav). */
public class RecordingNotFoundException extends RuntimeException {

    public RecordingNotFoundException(String scenarioId) {
        super("Brak nagrania dla scenariusza " + scenarioId + ".");
    }
}
