package in.devtamakuwala.smarti18nauto.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight logger wrapper that prefixes all library log messages.
 */
public final class SmartI18nLogger {

    private static final String PREFIX = "Smart I18N Auto: ";

    private final Logger delegate;

    private SmartI18nLogger(Class<?> source) {
        this.delegate = LoggerFactory.getLogger(source);
    }

    public static SmartI18nLogger getLogger(Class<?> source) {
        return new SmartI18nLogger(source);
    }

    public void trace(String message, Object... args) {
        delegate.trace(withPrefix(message), args);
    }

    public void debug(String message, Object... args) {
        delegate.debug(withPrefix(message), args);
    }

    public void info(String message, Object... args) {
        delegate.info(withPrefix(message), args);
    }

    public void warn(String message, Object... args) {
        delegate.warn(withPrefix(message), args);
    }

    public void error(String message, Object... args) {
        delegate.error(withPrefix(message), args);
    }

    private String withPrefix(String message) {
        return PREFIX + (message == null ? "" : message);
    }
}

