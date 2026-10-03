package pl.aniolstroz.call;

import org.springframework.stereotype.Component;

/**
 * Whether the AI answered the last time it was asked. Set by {@link AiAnalyzer}; read by {@link KeywordBaseline} to
 * decide if the keyword safety net must step in (rule 7). A plain flag: the safe reading of "unknown" is "failing".
 */
@Component
public class AiHealth {

    private volatile boolean failing;

    public boolean failing() {
        return failing;
    }

    void failed() {
        failing = true;
    }

    void succeeded() {
        failing = false;
    }
}
