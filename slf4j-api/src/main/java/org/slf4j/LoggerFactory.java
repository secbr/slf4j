package org.slf4j;

import org.slf4j.event.SubstituteLoggingEvent;
import org.slf4j.helpers.NOPServiceProvider;
import org.slf4j.helpers.SubstituteLogger;
import org.slf4j.helpers.SubstituteServiceProvider;
import org.slf4j.helpers.Util;
import org.slf4j.spi.SLF4JServiceProvider;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * LoggerFactory是一个生成各种日志API Logger的通用类，尤其是log4j、logback和JDK 1.4 logging。
 * 其他日志实现，比如NOPLogger和SimpleLogger也是支持的。
 *
 * <p>
 * LoggerFactory本质上是在编译时与LoggerFactory绑定的ILoggerFactory实例的包装。
 *
 * <p>
 * 请将<code>LoggerFactory</code>中所有的方法都定义为静态方法。
 *
 * @author Alexander Dorokhine
 * @author Robert Elliot
 * @author Ceki G&uuml;lc&uuml;
 */
public final class LoggerFactory {

    /**
     * 警告及错误文档地址（前缀）
     */
    static final String CODES_PREFIX = "http://www.slf4j.org/codes.html";

    static final String NO_PROVIDERS_URL = CODES_PREFIX + "#noProviders";
    static final String IGNORED_BINDINGS_URL = CODES_PREFIX + "#ignoredBindings";

    static final String NO_STATICLOGGERBINDER_URL = CODES_PREFIX + "#StaticLoggerBinder";
    static final String MULTIPLE_BINDINGS_URL = CODES_PREFIX + "#multiple_bindings";
    static final String NULL_LF_URL = CODES_PREFIX + "#null_LF";
    static final String VERSION_MISMATCH = CODES_PREFIX + "#version_mismatch";
    static final String SUBSTITUTE_LOGGER_URL = CODES_PREFIX + "#substituteLogger";
    static final String LOGGER_NAME_MISMATCH_URL = CODES_PREFIX + "#loggerNameMismatch";
    static final String REPLAY_URL = CODES_PREFIX + "#replay";

    static final String UNSUCCESSFUL_INIT_URL = CODES_PREFIX + "#unsuccessfulInit";
    static final String UNSUCCESSFUL_INIT_MSG = "org.slf4j.LoggerFactory in failed state. Original exception was thrown EARLIER. See also "
            + UNSUCCESSFUL_INIT_URL;

    /**
     * provider未初始化
     */
    static final int UNINITIALIZED = 0;

    /**
     * provider初始化中
     */
    static final int ONGOING_INITIALIZATION = 1;

    /**
     * 初始化失败
     */
    static final int FAILED_INITIALIZATION = 2;

    /**
     * 成功初始化
     */
    static final int SUCCESSFUL_INITIALIZATION = 3;

    /**
     * 误操作的应急provider初始化
     */
    static final int NOP_FALLBACK_INITIALIZATION = 4;

    static volatile int INITIALIZATION_STATE = UNINITIALIZED;
    static final SubstituteServiceProvider SUBST_PROVIDER = new SubstituteServiceProvider();
    static final NOPServiceProvider NOP_FALLBACK_FACTORY = new NOPServiceProvider();

    // Support for detecting mismatched logger names.
    static final String DETECT_LOGGER_NAME_MISMATCH_PROPERTY = "slf4j.detectLoggerNameMismatch";
    static final String JAVA_VENDOR_PROPERTY = "java.vendor.url";

    static boolean DETECT_LOGGER_NAME_MISMATCH = Util.safeGetBooleanSystemProperty(DETECT_LOGGER_NAME_MISMATCH_PROPERTY);

    static volatile SLF4JServiceProvider PROVIDER;

    private static List<SLF4JServiceProvider> findServiceProviders() {
        ServiceLoader<SLF4JServiceProvider> serviceLoader = ServiceLoader.load(SLF4JServiceProvider.class);
        List<SLF4JServiceProvider> providerList = new ArrayList<>();
        for(SLF4JServiceProvider provider : serviceLoader) {
            providerList.add(provider);
        }
        return providerList;
    }

    /**
     * It is LoggerFactory's responsibility to track version changes and manage
     * the compatibility list.
     * <p>
     * <p>
     * It is assumed that all versions in the 1.6 are mutually compatible.
     */
    static private final String[] API_COMPATIBILITY_LIST = new String[]{"1.8", "1.7"};

    /**
     * 构造方法私有化
     */
    private LoggerFactory() {
    }

    /**
     * Force LoggerFactory to consider itself uninitialized.
     * <p>
     * <p>
     * This method is intended to be called by classes (in the same package) for
     * testing purposes. This method is internal. It can be modified, renamed or
     * removed at any time without notice.
     * <p>
     * <p>
     * You are strongly discouraged from calling this method in production code.
     */
    static void reset() {
        INITIALIZATION_STATE = UNINITIALIZED;
    }

    private static void performInitialization() {
        bind();
        if(INITIALIZATION_STATE == SUCCESSFUL_INITIALIZATION) {
            versionSanityCheck();
        }
    }

    private static void bind() {
        try {
            List<SLF4JServiceProvider> providersList = findServiceProviders();
            // 如果出现多个SLF4JServiceProvider实例，则打印警告信息。
            reportMultipleBindingAmbiguity(providersList);
            // 判断是否扫描到对应的SLF4JServiceProvider实现类
            if(!providersList.isEmpty()) {
                PROVIDER = providersList.get(0);
                // SLF4JServiceProvider.initialize() is intended to be called here and nowhere else.
                PROVIDER.initialize();
                INITIALIZATION_STATE = SUCCESSFUL_INITIALIZATION;
                // 打印绑定了哪个SLF4JServiceProvider
                reportActualBinding(providersList);
            } else {
                // 如果未扫描到对应的实现类，初始化状态变为无操作的应急NOPServiceProvider
                INITIALIZATION_STATE = NOP_FALLBACK_INITIALIZATION;
                // 下面三行警告信息，在项目中未使用日志框架时，经常会看到。
                Util.report("No SLF4J providers were found.");
                Util.report("Defaulting to no-operation (NOP) logger implementation");
                Util.report("See " + NO_PROVIDERS_URL + " for further details.");

                // 打印忽略掉的静态Logger绑定
                Set<URL> staticLoggerBinderPathSet = findPossibleStaticLoggerBinderPathSet();
                reportIgnoredStaticLoggerBinders(staticLoggerBinderPathSet);
            }
            postBindCleanUp();
        } catch (Exception e) {
            failedBinding(e);
            throw new IllegalStateException("Unexpected initialization failure", e);
        }
    }

    private static void reportIgnoredStaticLoggerBinders(Set<URL> staticLoggerBinderPathSet) {
        if(staticLoggerBinderPathSet.isEmpty()) {
            return;
        }
        Util.report("Class path contains SLF4J bindings targeting slf4j-api versions prior to 1.8.");
        for(URL path : staticLoggerBinderPathSet) {
            Util.report("Ignoring binding found at [" + path + "]");
        }
        Util.report("See " + IGNORED_BINDINGS_URL + " for an explanation.");
    }

    // 1.8之前slf4j寻找的Binder类，因此匹配1.8版本之前的slf4j-api的桥接器都会包含一个该类。
    // 1.8及之后的版本中即便是找到了该类，slf4j也不会使用该类完成实现框架的绑定，而是忽略，使用自带的实现类NOPLogger。
    private static final String STATIC_LOGGER_BINDER_PATH = "org/slf4j/impl/StaticLoggerBinder.class";

    static Set<URL> findPossibleStaticLoggerBinderPathSet() {
        // use Set instead of list in order to deal with bug #138
        // LinkedHashSet appropriate here because it preserves insertion order
        // during iteration
        Set<URL> staticLoggerBinderPathSet = new LinkedHashSet<>();
        try {
            ClassLoader loggerFactoryClassLoader = LoggerFactory.class.getClassLoader();
            Enumeration<URL> paths;
            if(loggerFactoryClassLoader == null) {
                paths = ClassLoader.getSystemResources(STATIC_LOGGER_BINDER_PATH);
            } else {
                paths = loggerFactoryClassLoader.getResources(STATIC_LOGGER_BINDER_PATH);
            }
            while(paths.hasMoreElements()) {
                URL path = paths.nextElement();
                staticLoggerBinderPathSet.add(path);
            }
        } catch (IOException ioe) {
            Util.report("Error getting resources from path", ioe);
        }
        return staticLoggerBinderPathSet;
    }

    private static void postBindCleanUp() {
        fixSubstituteLoggers();
        replayEvents();
        // release all resources in SUBST_FACTORY
        SUBST_PROVIDER.getSubstituteLoggerFactory().clear();
    }

    private static void fixSubstituteLoggers() {
        synchronized(SUBST_PROVIDER) {
            SUBST_PROVIDER.getSubstituteLoggerFactory().postInitialization();
            for(SubstituteLogger substLogger : SUBST_PROVIDER.getSubstituteLoggerFactory().getLoggers()) {
                Logger logger = getLogger(substLogger.getName());
                substLogger.setDelegate(logger);
            }
        }
    }

    static void failedBinding(Throwable t) {
        INITIALIZATION_STATE = FAILED_INITIALIZATION;
        Util.report("Failed to instantiate SLF4J LoggerFactory", t);
    }

    private static void replayEvents() {
        final LinkedBlockingQueue<SubstituteLoggingEvent> queue = SUBST_PROVIDER.getSubstituteLoggerFactory().getEventQueue();
        final int queueSize = queue.size();
        int count = 0;
        final int maxDrain = 128;
        List<SubstituteLoggingEvent> eventList = new ArrayList<>(maxDrain);
        while(true) {
            int numDrained = queue.drainTo(eventList, maxDrain);
            if(numDrained == 0) {
                break;
            }
            for(SubstituteLoggingEvent event : eventList) {
                replaySingleEvent(event);
                if(count++ == 0) {
                    emitReplayOrSubstituionWarning(event, queueSize);
                }
            }
            eventList.clear();
        }
    }

    private static void emitReplayOrSubstituionWarning(SubstituteLoggingEvent event, int queueSize) {
        if(event.getLogger().isDelegateEventAware()) {
            emitReplayWarning(queueSize);
        } else if(event.getLogger().isDelegateNOP()) {
            // nothing to do
        } else {
            emitSubstitutionWarning();
        }
    }

    private static void replaySingleEvent(SubstituteLoggingEvent event) {
        if(event == null) {
            return;
        }

        SubstituteLogger substLogger = event.getLogger();
        String loggerName = substLogger.getName();
        if(substLogger.isDelegateNull()) {
            throw new IllegalStateException("Delegate logger cannot be null at this state.");
        }

        if(substLogger.isDelegateNOP()) {
            // nothing to do
        } else if(substLogger.isDelegateEventAware()) {
            substLogger.log(event);
        } else {
            Util.report(loggerName);
        }
    }

    private static void emitSubstitutionWarning() {
        Util.report("The following set of substitute loggers may have been accessed");
        Util.report("during the initialization phase. Logging calls during this");
        Util.report("phase were not honored. However, subsequent logging calls to these");
        Util.report("loggers will work as normally expected.");
        Util.report("See also " + SUBSTITUTE_LOGGER_URL);
    }

    private static void emitReplayWarning(int eventCount) {
        Util.report("A number (" + eventCount + ") of logging calls during the initialization phase have been intercepted and are");
        Util.report("now being replayed. These are subject to the filtering rules of the underlying logging system.");
        Util.report("See also " + REPLAY_URL);
    }

    private static void versionSanityCheck() {
        try {
            String requested = PROVIDER.getRequesteApiVersion();

            boolean match = false;
            for(String aAPI_COMPATIBILITY_LIST : API_COMPATIBILITY_LIST) {
                if(requested.startsWith(aAPI_COMPATIBILITY_LIST)) {
                    match = true;
                }
            }
            if(!match) {
                Util.report("The requested version " + requested + " by your slf4j binding is not compatible with "
                                    + Arrays.asList(API_COMPATIBILITY_LIST).toString());
                Util.report("See " + VERSION_MISMATCH + " for further details.");
            }
        } catch (java.lang.NoSuchFieldError nsfe) {
            // given our large user base and SLF4J's commitment to backward
            // compatibility, we cannot cry here. Only for implementations
            // which willingly declare a REQUESTED_API_VERSION field do we
            // emit compatibility warnings.
        } catch (Throwable e) {
            // we should never reach here
            Util.report("Unexpected problem occured during version sanity check", e);
        }
    }

    private static boolean isAmbiguousProviderList(List<SLF4JServiceProvider> providerList) {
        return providerList.size() > 1;
    }

    /**
     * 如果出现多个SLF4JServiceProvider实例，则打印警告信息。
     */
    private static void reportMultipleBindingAmbiguity(List<SLF4JServiceProvider> providerList) {
        if(isAmbiguousProviderList(providerList)) {
            Util.report("Class path contains multiple SLF4J providers.");
            for(SLF4JServiceProvider provider : providerList) {
                Util.report("Found provider [" + provider + "]");
            }
            Util.report("See " + MULTIPLE_BINDINGS_URL + " for an explanation.");
        }
    }

    private static void reportActualBinding(List<SLF4JServiceProvider> providerList) {
        // binderPathSet can be null under Android
        if(!providerList.isEmpty() && isAmbiguousProviderList(providerList)) {
            Util.report("Actual provider is of type [" + providerList.get(0) + "]");
        }
    }

    /**
     * Return a logger named according to the name parameter using the
     * statically bound {@link ILoggerFactory} instance.
     *
     * @param name The name of the logger.
     * @return logger
     */
    public static Logger getLogger(String name) {
        ILoggerFactory iLoggerFactory = getILoggerFactory();
        return iLoggerFactory.getLogger(name);
    }

    /**
     * Return a logger named corresponding to the class passed as parameter,
     * using the statically bound {@link ILoggerFactory} instance.
     *
     * <p>
     * In case the the <code>clazz</code> parameter differs from the name of the
     * caller as computed internally by SLF4J, a logger name mismatch warning
     * will be printed but only if the
     * <code>slf4j.detectLoggerNameMismatch</code> system property is set to
     * true. By default, this property is not set and no warnings will be
     * printed even in case of a logger name mismatch.
     *
     * @param clazz the returned logger will be named after clazz
     * @return logger
     * @see <a
     * href="http://www.slf4j.org/codes.html#loggerNameMismatch">Detected
     * logger name mismatch</a>
     */
    public static Logger getLogger(Class<?> clazz) {
        Logger logger = getLogger(clazz.getName());
        if(DETECT_LOGGER_NAME_MISMATCH) {
            Class<?> autoComputedCallingClass = Util.getCallingClass();
            if(autoComputedCallingClass != null && nonMatchingClasses(clazz, autoComputedCallingClass)) {
                Util.report(String.format("Detected logger name mismatch. Given name: \"%s\"; computed name: \"%s\".", logger.getName(),
                                          autoComputedCallingClass.getName()));
                Util.report("See " + LOGGER_NAME_MISMATCH_URL + " for an explanation");
            }
        }
        return logger;
    }

    private static boolean nonMatchingClasses(Class<?> clazz, Class<?> autoComputedCallingClass) {
        return !autoComputedCallingClass.isAssignableFrom(clazz);
    }

    /**
     * 返回使用中的ILoggerFactory的实例
     * <p>
     * <p>
     * ILoggerFactory实例在编译时与此类绑定。
     *
     * @return 使用中的ILoggerFactory的实例
     */
    public static ILoggerFactory getILoggerFactory() {
        return getProvider().getLoggerFactory();
    }

    /**
     * 返回使用中的SLF4JServiceProvider
     *
     * @return 使用的provider
     * @since 1.8.0
     */
    static SLF4JServiceProvider getProvider() {
        // 如果未初始化状态，则进行双重枷锁判断处理
        if(INITIALIZATION_STATE == UNINITIALIZED) {
            synchronized(LoggerFactory.class) {
                if(INITIALIZATION_STATE == UNINITIALIZED) {
                    // 初始化之前，先将状态修改为正在初始化
                    INITIALIZATION_STATE = ONGOING_INITIALIZATION;
                    performInitialization();
                }
            }
        }

        switch(INITIALIZATION_STATE) {
            case SUCCESSFUL_INITIALIZATION:
                // 初始化成功，返回对应的provider
                return PROVIDER;
            case NOP_FALLBACK_INITIALIZATION:
                // 没有找到实现，就会使用NOP(no operation的模式)
                return NOP_FALLBACK_FACTORY;
            case FAILED_INITIALIZATION:
                // 初始化失败状态，则抛出异常
                throw new IllegalStateException(UNSUCCESSFUL_INIT_MSG);
            case ONGOING_INITIALIZATION:
                // 这个是针对logback实现做的一个修复，暂且可以不管
                return SUBST_PROVIDER;
            default:
                // 其他状态，抛出异常。PS：此处重构到default中处理。
                throw new IllegalStateException("Unreachable code");
        }
    }
}
