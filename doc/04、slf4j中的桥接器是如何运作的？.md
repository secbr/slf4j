# slf4j中的桥接器是如何运作的？

阅读分析slf4j的日志源码，发现其中涵盖了许多知识点和优秀的设计，关键它们都是活生生的实践案例。写成系列文章与大家分享，欢迎持续关注。第4篇。

### 前言

在日志框架slf4j中有一组项目，除了核心的slf4j-api之外，还有slf4j-log4j12、slf4j-jdk14等项目。这一类项目统称桥接器项目，针对不同的日志框架有不同的桥接器项目。

在使用logback日志框架时，并没有针对的桥接器，这是因为logback与slf4j是一个作者所写，在logback中直接实现了slf4j的SPI机制。

但如果使用其他日志框架，那么就必须要用到桥机器相关依赖。比如，当我们基于log4j使用slf4j时，除了需要引入log4j的jar包依赖，还需要引入slf4j的下面两个依赖：

```
<dependency>
  <groupId>org.slf4j</groupId>
  <artifactId>slf4j-api</artifactId>
</dependency>
<dependency>
  <groupId>org.slf4j</groupId>
  <artifactId>slf4j-log4j12</artifactId>
</dependency>
```
slf4j-api为核心依赖，必须引入，而slf4j-log4j12就是桥接器用来在slf4j和log4j之间进行过渡和封装。下面，我们就聊聊桥接器项目的核心实现。

### slf4j-log4j12桥接器的价值

要了解桥接器的运作，首先需要回顾一下slf4j的SPI机制。在我们通过LoggerFactory.getLogger(Foo.class);时，slf4j会通过SPI机制寻找并初始化SLF4JServiceProvider的实现类。

然后，通过SLF4JServiceProvider的实现类来获取日志相关的具体工厂类对象，进而进行日志功能的处理。先来看一下SLF4JServiceProvider的接口定义：

```
public interface SLF4JServiceProvider {

    /**
     * 返回ILoggerFactory的实现类，用于LoggerFactory类的绑定
     */
    ILoggerFactory getLoggerFactory();

    /**
     * 返回IMarkerFactory实例
     */
    IMarkerFactory getMarkerFactory();

    /**
     * 返回MDCAdapter实例
     */
    MDCAdapter getMDCAdapter();

    /**
     * 获取请求版本
     */
    String getRequesteApiVersion();

    /**
     * 初始化，实现类中一般用于初始化ILoggerFactory等
     */
    void initialize();
}
```
SLF4JServiceProvider接口是在slf4j-api中定义的，具体的实现类由其他日志框架来完成。但是像log4j（logback“敌对阵营”）是不会在框架内实现该接口的。那么，怎么办？

针对此问题，slf4j提供了slf4j-log4j12这类桥接器的过渡项目。在其中实现SLF4JServiceProvider接口，并对Log4j日志框架接口进行封装，将Logger(slf4j)接收到的命令全部委托给Logger(log4j)去完成，在使用者无感知的情况下完成偷天换日。

### slf4j-log4j12的核心实现类

理解了桥接器的存在价值及原理，下面就来看看slf4j-log4j12是如何实现这一功能的。

首先来看看核心实现类之一Log4j12ServiceProvider。它实现了SLF4JServiceProvider接口，主要功能就是完成接口中定义的相关工厂接口的实现。源代码如下：
```
public class Log4j12ServiceProvider implements SLF4JServiceProvider {

    public static String REQUESTED_API_VERSION = "1.8.99"; 

    private ILoggerFactory loggerFactory; 
    private IMarkerFactory markerFactory; 
    private MDCAdapter mdcAdapter;
    
    public Log4j12ServiceProvider() {
        try {
            @SuppressWarnings("unused")
            Level level = Level.TRACE;
        } catch (NoSuchFieldError nsfe) {
            Util.report("This version of SLF4J requires log4j version 1.2.12 or later. See also http://www.slf4j.org/codes.html#log4j_version");
        }
    }

    @Override
    public void initialize() {
        loggerFactory = new Log4jLoggerFactory();
        markerFactory = new BasicMarkerFactory();
        mdcAdapter = new Log4jMDCAdapter();
    }
    
    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markerFactory;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return mdcAdapter;
    }

    @Override
    public String getRequesteApiVersion() {
        return REQUESTED_API_VERSION;
    }
}
```
该类的实现看起来很简单，构造方法中通过尝试使用log4j的Level.TRACE调用来验证log4j的版本是否符合要求。log4j1.2.12之前并没有Level.TRACE，所以会抛出异常，并打印日志信息。不得不赞叹作者在此处检查版本的巧妙用法。

而这里对接口中返回的实现类主要通过initialize()方法来实现的。这里我们重点看Log4jLoggerFactory类的实现。
```
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

    ConcurrentMap<String, Logger> loggerMap;

    public Log4jLoggerFactory() {
        loggerMap = new ConcurrentHashMap<>();
        // force log4j to initialize
        org.apache.log4j.LogManager.getRootLogger();
    }

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

            Logger newInstance = new Log4jLoggerAdapter(log4jLogger);
            Logger oldInstance = loggerMap.putIfAbsent(name, newInstance);
            return oldInstance == null ? newInstance : oldInstance;
        }
    }
}
```
在Log4j12ServiceProvider中进行了Log4jLoggerFactory的实例化操作，也就直接new出来一个对象。我们知道，在new对象执行会先执行static代码块，本类的静态代码块的核心工作就是检查依赖文件中是否同时存在反向桥接器的依赖。

其中，org.apache.log4j.Log4jLoggerFactory是反向桥接器log4j-over-slf4j项目中的类，如果加装到了，说明存在，则抛出异常，打印日志信息。此处再次赞叹作者运用的技巧的巧妙。

在Log4jLoggerFactory的构造方法中，做了两件事：第一，初始化一个ConcurrentMap变量，用于存储实例化的Logger；第二，强制初始化log4j的组件，其中强制初始化log4j的组件是通过getRootLogger方法，来初始化一些静态的变量。

构造方法时初始化了ConcurrentMap变量，在Log4jLoggerFactory实现的getLogger方法中，先从Map中获取一下是否存在对应的Logger，如果存在直接返回，如果不存在则进行构造。而构造的Log4jLoggerAdapter类很显然使用了适配器模式，它内部持有了log4j的Logger对象，自身又实现了slf4j的Logger接口。

下面看一下Log4jLoggerAdapter的部分代码实现：
```
public final class Log4jLoggerAdapter extends LegacyAbstractLogger implements LocationAwareLogger, Serializable {

    final transient org.apache.log4j.Logger logger;

    Log4jLoggerAdapter(org.apache.log4j.Logger logger) {
        this.logger = logger;
        this.name = logger.getName();
        traceCapable = isTraceCapable();
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }


    @Override
    public void log(Marker marker, String callerFQCN, int level, String msg, Object[] arguments, Throwable t) {
        Level log4jLevel = toLog4jLevel(level);
        NormalizedParameters np = NormalizedParameters.normalize(msg, arguments, t);
        String formattedMessage = MessageFormatter.basicArrayFormat(np.getMessage(), np.getArguments());
        logger.log(callerFQCN, log4jLevel, formattedMessage, np.getThrowable());
    }

    public void log(LoggingEvent event) {
        Level log4jLevel = toLog4jLevel(event.getLevel().toInt());
        if (!logger.isEnabledFor(log4jLevel))
            return;

        org.apache.log4j.spi.LoggingEvent log4jevent = toLog4jEvent(event, log4jLevel);
        logger.callAppenders(log4jevent);

    }
    
    // 省略其他方法
}
```
源码中，通过构造方法传入log4j的Logger对象，而Log4jLoggerAdapter对外提供的方法，都是通过log4j的Logger进行具体实现。

总之，slf4j的Logger接口的方法通过Log4jLoggerAdapter进行包装和转换，交由log4j的Logger去执行，这就达到了连接slf4j-api和log4j的目的。而此时，slf4j-api不并关系日志是如何实现记录，对此也无感知。

### 小结

本文通过源码跟踪，逐步分析了slf4j项目中桥接器项目的运作机制，其中还涉及到了SPI机制、版本及依赖检查小技巧、桥接器运作本质（适配器模式）等。其实，在slf4j项目中还有文中提到的反向桥接器，其实基本机制也是如此，感兴趣的朋友可以阅读一下log4j-over-slf4j中的源码。