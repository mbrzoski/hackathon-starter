package pl.aniolstroz.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/**
 * Test helper: collects what is logged while it is open, with the content trace switched on or off as the test needs,
 * and puts both trace levels back on close. Lets a test say what must (flow) and must not (content) reach the log.
 */
public final class LogCapture implements AutoCloseable {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final Logger flow = (Logger) LoggerFactory.getLogger(Trace.FLOW_LOGGER);
    private final Logger content = (Logger) LoggerFactory.getLogger(Trace.CONTENT_LOGGER);
    private final Level flowBefore = flow.getLevel();
    private final Level contentBefore = content.getLevel();

    private LogCapture(boolean contentOn) {
        flow.setLevel(Level.INFO);
        content.setLevel(contentOn ? Level.DEBUG : Level.INFO);
        appender.start();
        root.addAppender(appender);
    }

    /** @param contentOn whether the content trace (the words of the call) is switched on */
    public static LogCapture start(boolean contentOn) {
        return new LogCapture(contentOn);
    }

    /** The formatted messages so far, in order. */
    public List<String> messages() {
        return List.copyOf(appender.list).stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** Everything logged so far as one text, for "this must not appear anywhere". */
    public String all() {
        return String.join("\n", messages());
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
        flow.setLevel(flowBefore);
        content.setLevel(contentBefore);
    }
}
