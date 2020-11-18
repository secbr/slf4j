package org.slf4j.helpers;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

/**
 * NOPLoggerFactory is an trivial implementation of {@link
 * ILoggerFactory} which always returns the unique instance of
 * NOPLogger.
 *
 * @author Ceki G&uuml;lc&uuml;
 */
public class NOPLoggerFactory implements ILoggerFactory {

    public NOPLoggerFactory() {
        // nothing to do
    }

    @Override
    public Logger getLogger(String name) {
        return NOPLogger.NOP_LOGGER;
    }

}
