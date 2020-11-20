package org.slf4j.spi;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.LoggingEventAware;
import org.slf4j.event.*;

import java.util.function.Supplier;

public class DefaultLoggingEventBuilder implements LoggingEventBuilder {

    DefaultLoggingEvent loggingEvent;
    Logger logger;

    public DefaultLoggingEventBuilder(Logger logger, Level level) {
        this.logger = logger;
        loggingEvent = new DefaultLoggingEvent(level, logger);
    }

    /**
     * Add a marker to the current logging event being built.
     * <p>
     * It is possible to add multiple markers to the same logging event.
     *
     * @param marker the marker to add
     */
    @Override
    public LoggingEventBuilder addMarker(Marker marker) {
        loggingEvent.addMarker(marker);
        return this;
    }

    @Override
    public LoggingEventBuilder setCause(Throwable t) {
        loggingEvent.setThrowable(t);
        return this;
    }

    @Override
    public LoggingEventBuilder addArgument(Object p) {
        loggingEvent.addArgument(p);
        return this;
    }

    @Override
    public LoggingEventBuilder addArgument(Supplier<Object> objectSupplier) {
        loggingEvent.addArgument(objectSupplier.get());
        return this;
    }

    @Override
    public void log(String message) {
        loggingEvent.setMessage(message);
        innerLog(loggingEvent);
    }


    @Override
    public void log(String message, Object arg) {
        loggingEvent.setMessage(message);
        loggingEvent.addArgument(arg);
        innerLog(loggingEvent);
    }

    @Override
    public void log(String message, Object arg0, Object arg1) {
        loggingEvent.setMessage(message);
        loggingEvent.addArgument(arg0);
        loggingEvent.addArgument(arg1);
        innerLog(loggingEvent);
    }

    @Override
    public void log(String message, Object... args) {
        loggingEvent.setMessage(message);
        loggingEvent.addArguments(args);

        innerLog(loggingEvent);
    }

    private void innerLog(LoggingEvent loggingEvent) {
        if(logger instanceof LoggingEventAware) {
            ((LoggingEventAware) logger).log(loggingEvent);
        } else {
            logViaPublicLoggerAPI(loggingEvent);
        }
    }

    private void logViaPublicLoggerAPI(LoggingEvent loggingEvent) {
        Object[] argArray = loggingEvent.getArgumentArray();
        int argLen = argArray == null ? 0 : argArray.length;

        Throwable t = loggingEvent.getThrowable();
        int tLen = t == null ? 0 : 1;

        String msg = loggingEvent.getMessage();

        Object[] combinedArguments = new Object[argLen + tLen];

        if(argArray != null) {
            System.arraycopy(argArray, 0, combinedArguments, 0, argLen);
        }
        if(t != null) {
            combinedArguments[argLen] = t;
        }

        msg = mergeMarkersAndKeyValuePairs(loggingEvent, msg);

        switch(loggingEvent.getLevel()) {
            case TRACE:
                logger.trace(msg, combinedArguments);
                break;
            case DEBUG:
                logger.debug(msg, combinedArguments);
                break;
            case INFO:
                logger.info(msg, combinedArguments);
                break;
            case WARN:
                logger.warn(msg, combinedArguments);
                break;
            case ERROR:
                logger.error(msg, combinedArguments);
                break;
        }

    }

    /**
     * Prepend markers and key-value pairs to the message.
     *
     * @param loggingEvent
     * @param msg
     * @return
     */
    private String mergeMarkersAndKeyValuePairs(LoggingEvent loggingEvent, String msg) {

        StringBuilder sb = null;

        if(this.loggingEvent.getMarkers() != null) {
            sb = new StringBuilder();
            for(Marker marker : loggingEvent.getMarkers()) {
                sb.append(marker);
                sb.append(' ');
            }
        }

        if(loggingEvent.getKeyValuePairs() != null) {
            if(sb == null) {
                sb = new StringBuilder();
            }
            for(KeyValuePair kvp : loggingEvent.getKeyValuePairs()) {
                sb.append(kvp.key);
                sb.append('=');
                sb.append(kvp.value);
                sb.append(' ');
            }
        }

        if(sb != null) {
            sb.append(msg);
            return sb.toString();
        } else {
            return msg;
        }
    }


    @Override
    public void log(Supplier<String> messageSupplier) {
        if(messageSupplier == null) {
            log((String) null);
        } else {
            log(messageSupplier.get());
        }
    }

    @Override
    public LoggingEventBuilder addKeyValue(String key, Object value) {
        loggingEvent.addKeyValue(key, value);
        return this;
    }

    @Override
    public LoggingEventBuilder addKeyValue(String key, Supplier<Object> value) {
        loggingEvent.addKeyValue(key, value.get());
        return this;
    }

}
