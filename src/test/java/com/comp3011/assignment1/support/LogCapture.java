package com.comp3011.assignment1.support;

import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Attaches a Logback ListAppender to the root logger so a test can assert on what was logged.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public LogCapture() {
        appender.start();
        root.addAppender(appender);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    /** Every rendered message, with MDC values already substituted where asked for. */
    public List<String> messages() {
        return events().stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    public List<ILoggingEvent> atLeast(Level level) {
        return events().stream().filter(e -> e.getLevel().isGreaterOrEqual(level)).toList();
    }

    public boolean anyMessageContains(String text) {
        return messages().stream().anyMatch(m -> m.contains(text));
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
    }
}
