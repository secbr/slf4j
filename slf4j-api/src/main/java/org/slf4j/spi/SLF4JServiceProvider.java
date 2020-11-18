package org.slf4j.spi;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.LoggerFactory;

/**
 * 该接口基于ServiceLoader（SPI）规范
 *
 * <p>它替代了在1.0.x到1.7.x版本中，原有的基于静态绑定的机制。
 *
 * @author Ceki G&uml;lc&uml;
 * @since 1.8
 */
public interface SLF4JServiceProvider {

    /**
     * 返回ILoggerFactory的实现类，用于LoggerFactory类的绑定
     *
     * @return ILoggerFactory实现类
     */
    ILoggerFactory getLoggerFactory();

    /**
     * Return the instance of {@link IMarkerFactory} that
     * {@link org.slf4j.MarkerFactory} class should bind to.
     *
     * @return instance of {@link IMarkerFactory}
     */
    IMarkerFactory getMarkerFactory();

    /**
     * Return the instnace of {@link MDCAdapter} that
     * {@link MDC} should bind to.
     *
     * @return instance of {@link MDCAdapter}
     */
    MDCAdapter getMDCAdapter();

    /**
     * 此处方法名单词拼写错误，但每个实现类都实现了该方法，也只能将错就错了。
     */
    String getRequesteApiVersion();

    /**
     * 初始化，实现类中一般用于初始化ILoggerFactory等
     *
     * <p><b>WARNING:</b> This method is intended to be called once by
     * {@link LoggerFactory} class and from nowhere else.
     */
    void initialize();
}
