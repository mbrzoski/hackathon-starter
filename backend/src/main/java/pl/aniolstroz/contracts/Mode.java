package pl.aniolstroz.contracts;

/** Honest operating mode; every event, audit record and screen carries one. */
public enum Mode {
    LIVE,
    REPLAY,
    SCRIPTED,
    MOCK
}
