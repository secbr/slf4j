package org.slf4j.log4j12;

import org.apache.log4j.LogManager;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.helpers.Util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Log4jLoggerFactory is an implementation of {@link ILoggerFactory} returning
 * the appropriate named {@link Log4jLoggerAdapter} instance.
 *
 * @author Ceki G&uuml;lc&uuml;
 */
public class Log4jLoggerFactory implements ILoggerFactory {

    private static final String LOG4J_DELEGATION_LOOP_URL = "http://www.slf4j.org/codes.html#log4jDelegationLoop";

    // check for delegation loops
    static {
        try {
            Class.forName("org.apache.log4j.Log4jLoggerFactory");
            String part1 = "Detected both log4j-over-slf4j.jar AND bound slf4j-log4j12.jar on the class path, preempting StackOverflowError. ";
            String part2 = "See also " + LOG4J_DELEGATION_LOOP_URL + " for more details.";

            Util.report(part1);
            Util.report(part2);
            throw new IllegalStateException(part1 + part2);
        } catch (ClassNotFoundException e) {
            // this is the good case
        }
    }

    /**
     * key: name (String), value: a Log4jLoggerAdapter;
     */
    ConcurrentMap<String, Logger> loggerMap;

    public Log4jLoggerFactory() {
        loggerMap = new ConcurrentHashMap<>();
        // force log4j to initialize
        org.apache.log4j.LogManager.getRootLogger();
    }

    /**
     * (non-Javadoc)
     *
     * @see org.slf4j.ILoggerFactory#getLogger(java.lang.String)
     */
    @Override
    public Logger getLogger(String name) {
        Logger slf4jLogger = loggerMap.get(name);
        if(slf4jLogger != null) {
            return slf4jLogger;
        } else {
            org.apache.log4j.Logger log4jLogger;
            if(name.equalsIgnoreCase(Logger.ROOT_LOGGER_NAME)) {
                log4jLogger = LogManager.getRootLogger();
            } else {
                log4jLogger = LogManager.getLogger(name);
            }

            // 适配器模式的使用
            Logger newInstance = new Log4jLoggerAdapter(log4jLogger);
            Logger oldInstance = loggerMap.putIfAbsent(name, newInstance);
            return oldInstance == null ? newInstance : oldInstance;
        }
    }
}
