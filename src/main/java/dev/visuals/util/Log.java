package dev.visuals.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dünner Wrapper, damit überall derselbe Logger-Name benutzt wird. */
public final class Log {
    private static final Logger LOGGER = LoggerFactory.getLogger("Visuals");

    private Log() {
    }

    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    public static void error(String message, Object... args) {
        LOGGER.error(message, args);
    }
}
