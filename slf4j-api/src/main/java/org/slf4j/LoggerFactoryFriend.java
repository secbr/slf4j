package org.slf4j;

/**
 * All methods in this class are reserved for internal use, for testing purposes.
 *
 * <p>They can can be modified, renamed or removed at any time without notice.
 *
 * <p>You are strongly discouraged calling any of the methods of this class.
 *
 * @author Ceki G&uuml;lc&uuml;
 * @since 1.8.0
 */
public class LoggerFactoryFriend {

    /**
     * Force LoggerFactory to consider itself uninitialized.
     */
    static public void reset() {
        LoggerFactory.reset();
    }

    /**
     * Set LoggerFactory.DETECT_LOGGER_NAME_MISMATCH variable.
     *
     * @param enabled a boolean
     */
    public static void setDetectLoggerNameMismatch(boolean enabled) {
        LoggerFactory.DETECT_LOGGER_NAME_MISMATCH = enabled;
    }
}
