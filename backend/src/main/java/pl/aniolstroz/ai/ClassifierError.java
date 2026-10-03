package pl.aniolstroz.ai;

/** Why a classifier call produced no usable answer. Chosen from typed SDK exceptions and stop reasons, never from text. */
public enum ClassifierError {
    REFUSAL,
    MAX_TOKENS,
    UNEXPECTED_STOP,
    TIMEOUT,
    RATE_LIMIT,
    SERVER_ERROR,
    API_ERROR,
    NETWORK,
    INVALID_OUTPUT,
    INTERNAL
}
