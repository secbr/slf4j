package org.slf4j.nop;

import org.slf4j.Logger;
import org.slf4j.helpers.MarkerIgnoringBase;

/**
 * A direct NOP (no operation) implementation of {@link Logger}.
 *
 * @author Ceki G&uuml;lc&uuml;
 */
public class NOPLogger extends MarkerIgnoringBase {

    private static final long serialVersionUID = -517220405410904473L;

    /**
     * The unique instance of NOPLogger.
     */
    public static final NOPLogger NOP_LOGGER = new NOPLogger();

    /**
     * There is no point in creating multiple instances of NOPLogger,
     * except by derived classes, hence the protected  access for the constructor.
     */
    protected NOPLogger() {
    }

    /**
     * Always returns the string value "NOP".
     */
    @Override
    public String getName() {
        return "NOP";
    }

    /**
     * Always returns false.
     * @return always false
     */
    @Override
    final public boolean isTraceEnabled() {
        return false;
    }

    /** A NOP implementation. */
    @Override
    final public void trace(String msg) {
        // NOP
    }

    /** A NOP implementation.  */
    @Override
    final public void trace(String format, Object arg) {
        // NOP
    }

    /** A NOP implementation.  */
    @Override
    public final void trace(String format, Object arg1, Object arg2) {
        // NOP
    }

    /** A NOP implementation.  */
    @Override
    public final void trace(String format, Object... argArray) {
        // NOP
    }

    /** A NOP implementation. */
    @Override
    final public void trace(String msg, Throwable t) {
        // NOP
    }

    /**
     * Always returns false.
     * @return always false
     */
    @Override
    final public boolean isDebugEnabled() {
        return false;
    }

    /** A NOP implementation. */
    @Override
    final public void debug(String msg) {
        // NOP
    }

    /** A NOP implementation.  */
    @Override
    final public void debug(String format, Object arg) {
        // NOP
    }

    /** A NOP implementation.  */
    @Override
    public final void debug(String format, Object arg1, Object arg2) {
        // NOP
    }

    /** A NOP implementation.  */
    @Override
    public final void debug(String format, Object... argArray) {
        // NOP
    }

    /** A NOP implementation. */
    @Override
    final public void debug(String msg, Throwable t) {
        // NOP
    }

    /**
     * Always returns false.
     * @return always false
     */
    @Override
    final public boolean isInfoEnabled() {
        // NOP
        return false;
    }

    /** A NOP implementation. */
    @Override
    final public void info(String msg) {
        // NOP
    }

    /** A NOP implementation. */
    @Override
    final public void info(String format, Object arg1) {
        // NOP
    }

    /** A NOP implementation. */
    @Override
    final public void info(String format, Object arg1, Object arg2) {
        // NOP
    }

    /** A NOP implementation.  */
    @Override
    public final void info(String format, Object... argArray) {
        // NOP
    }

    /** A NOP implementation. */
    @Override
    final public void info(String msg, Throwable t) {
        // NOP
    }

    /**
     * Always returns false.
     * @return always false
     */
    @Override
    final public boolean isWarnEnabled() {
        return false;
    }

    /** A NOP implementation. */
    @Override
    final public void warn(String msg) {
        // NOP
    }

    /** A NOP implementation. */
    @Override
    final public void warn(String format, Object arg1) {
        // NOP
    }

    /** A NOP implementation. */
    @Override
    final public void warn(String format, Object arg1, Object arg2) {
        // NOP
    }

    /** A NOP implementation.  */
    @Override
    public final void warn(String format, Object... argArray) {
        // NOP
    }

    /** A NOP implementation. */
    @Override
    final public void warn(String msg, Throwable t) {
        // NOP
    }

    /** A NOP implementation. */
    @Override
    final public boolean isErrorEnabled() {
        return false;
    }

    /** A NOP implementation. */
    @Override
    final public void error(String msg) {
        // NOP
    }

    /** A NOP implementation. */
    @Override
    final public void error(String format, Object arg1) {
        // NOP
    }

    /** A NOP implementation. */
    @Override
    final public void error(String format, Object arg1, Object arg2) {
        // NOP
    }

    /** A NOP implementation.  */
    @Override
    public final void error(String format, Object... argArray) {
        // NOP
    }

    /** A NOP implementation. */
    @Override
    final public void error(String msg, Throwable t) {
        // NOP
    }
}
