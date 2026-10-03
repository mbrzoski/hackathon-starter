package pl.aniolstroz.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One place for the trace of the whole process (audio, speech recognition, call, AI, alerts), so that a run can be
 * followed step by step from the logs. Two loggers, two levels of detail:
 *
 * <ul>
 *   <li>{@value #FLOW_LOGGER} (INFO, always on): what happened and how big/how fast. Ids, counts, lengths, timings,
 *       states. No transcript text, no quotes, no audio, no keys, no settings (OBS-05).
 *   <li>{@value #CONTENT_LOGGER} (DEBUG, <b>off by default</b>): the words themselves. What the recognizer heard, the
 *       request sent to Claude, its raw answer, the quotes of the hits. For diagnosing a run with synthetic or
 *       role-played data (DAT-04) only. Switch it on with the environment variable
 *       {@code LOGGING_LEVEL_PL_ANIOLSTROZ_TRACE_CONTENT=DEBUG}.
 * </ul>
 *
 * <p>Audio itself (the bytes) never reaches either logger, whatever the level (AUD-02). Callers wrap anything costly
 * to build in {@code if (Trace.content())}.
 */
public final class Trace {

    public static final String FLOW_LOGGER = "pl.aniolstroz.trace.flow";
    public static final String CONTENT_LOGGER = "pl.aniolstroz.trace.content";

    private static final Logger FLOW = LoggerFactory.getLogger(FLOW_LOGGER);
    private static final Logger CONTENT = LoggerFactory.getLogger(CONTENT_LOGGER);
    private static final int ID_LENGTH = 8;

    private Trace() {
    }

    /** A step of the process, without any words from the call. */
    public static void flow(String message, Object... args) {
        FLOW.info(message, args);
    }

    /** A step with the words of the call. Does nothing unless the content logger is switched on. */
    public static void content(String message, Object... args) {
        CONTENT.debug(message, args);
    }

    public static boolean content() {
        return CONTENT.isDebugEnabled();
    }

    /** First characters of an id, enough to follow one call or alert in the log. */
    public static String id(String id) {
        return id == null ? "-" : id.length() <= ID_LENGTH ? id : id.substring(0, ID_LENGTH);
    }

    /** The text on one line (a caller cannot forge log lines with line breaks), cut to {@code max} characters. */
    public static String oneLine(String text, int max) {
        if (text == null) {
            return "-";
        }
        String line = text.replaceAll("[\\r\\n\\t]+", " ").strip();
        return line.length() <= max ? line : line.substring(0, max) + "...(" + line.length() + " chars)";
    }

    /** {@link #oneLine(String, int)} without a practical limit (a request to Claude is long on purpose). */
    public static String oneLine(String text) {
        return oneLine(text, 20_000);
    }
}
