package pl.aniolstroz.stt;

/** Creates a provider per call (and per restart) and tells in advance whether recognition can work at all. */
public interface SttProviderFactory {

    /** Checks the preconditions (for Vosk: the model loads) without starting a call. */
    void preflight() throws SttUnavailableException;

    SttProvider create(SttStatusSink status);
}
