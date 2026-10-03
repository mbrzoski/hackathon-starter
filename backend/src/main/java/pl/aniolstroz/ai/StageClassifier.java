package pl.aniolstroz.ai;

/**
 * Finds manipulation stages in a transcript and returns evidence only: stage and verbatim quote (AI-01). The risk
 * level, alert texts and decisions are not its business. Implementations never throw for expected failures; they
 * return a result with {@code error} set.
 */
public interface StageClassifier {

    ClassifierResult classify(CallSnapshot snapshot);

    /** True for canned answers, so that the call is labelled MOCK and not passed off as a real AI result. */
    default boolean isMock() {
        return false;
    }
}
