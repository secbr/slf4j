package org.slf4j;

/**
 * ILoggerFactory接口定义了根据名称获得Logger实例的功能
 * 
 * <p>Most users retrieve {@link Logger} instances through the static
 * {@link LoggerFactory#getLogger(String)} method. An instance of this
 * interface is bound internally with {@link LoggerFactory} class at 
 * compile time. 
 * 
 * @author Ceki G&uuml;lc&uuml;
 */
public interface ILoggerFactory {

    /**
     * Return an appropriate {@link Logger} instance as specified by the
     * <code>name</code> parameter.
     * 
     * <p>If the name parameter is equal to {@link Logger#ROOT_LOGGER_NAME}, that is 
     * the string value "ROOT" (case insensitive), then the root logger of the 
     * underlying logging system is returned.
     * 
     * <p>Null-valued name arguments are considered invalid.
     *
     * <p>Certain extremely simple logging systems, e.g. NOP, may always
     * return the same logger instance regardless of the requested name.
     * 
     * @param name the name of the Logger to return
     * @return 返回Logger实例
     */
    Logger getLogger(String name);
}
