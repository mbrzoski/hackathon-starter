package pl.aniolstroz.call;

/** Told about every finished classifier call, successful or not. Must not throw. The audit is the observer. */
@FunctionalInterface
public interface AiCallObserver {

    void onAiCall(AiCallReport report);
}
