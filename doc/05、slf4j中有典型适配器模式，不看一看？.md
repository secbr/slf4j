# slf4j中有典型适配器模式，不看一看?

slf4j的日志源码分析第5篇，我们来讲解其中适配器模式的运用案例。

### 什么是适配器模式

在看slf4j中适配器模式的具体实现之前，我们先来了解一下适配器模式的基础概念和运用场景。

适配器模式，运用的场景用一句通俗的话来说就是：用一个包装来包装不兼容接口对象。

这里涉及到两个角色：包装类和被包装的对象。包装类，就是所谓的适配器，通常以Adapter为后缀；被包装对象，也就是所谓的适配者（Adaptee）、被适配的类。

那么，什么场景下会用到适配器模式呢？以slf4j框架为例，slf4j对外提供的统一Logger类为org.slf4j.Logger，而具体的日志框架采用了log4j，log4j框架对应的Logger为org.apache.log4j.Logger。很显然，log4j的Logger无法满足为项目提供统一的slf4j的Logger，那么就需要通过适配器将log4j的Logger进行包装和兼容。这就是适配器模式的典型运用场景。

### 适配器模式类图

这里我们直接以slf4j的Logger、Log4jLoggerAdapter和log4j的Logger为基础，展示一下类图结构。

![适配器模式](http://www.choupangxia.com/wp-content/uploads/2020/12/adapter-01.jpg)

通过上图我们可以看出：业务中想使用slf4j提供的统一Logger，但log4j的Logger并不满足slf4j定义的标准，那么就出现了不兼容的情况。为了解决不兼容，slf4j中就创建了一个中间类Log4jLoggerAdapter，该类实现了slf4j的Logger接口，便拥有了其对应的接口能力，同时通过委派关系（构造方法传入）连接到log4j的Logger，在实现slf4j的Logger的方法时，调用log4j的Logger对应的实现。通过这一流程，便完成了适配器的作用。

### slf4j的具体实现

关于适配器模式的实现并不是在slf4j-core中（只定义了Logger），而具体实现是在针对log4j的桥接器项目slf4j-log4j12中。首先看一下slf4j的Logger，也就是我们在项目中直接使用的Logger。

```
public interface Logger {

    String getName();

    void debug(String msg);

    void info(String msg);
    
    void warn(String msg);

    void error(String msg);
    
    // ...省略其他方法
}
```
而待适配的log4j的Logger部分源码如下：
```
public class Logger extends Category {

    protected Logger(String name) {
        super(name);
    }

    public static Logger getLogger(String name) {
        return LogManager.getLogger(name);
    }

    public static Logger getLogger(Class clazz) {
        return LogManager.getLogger(clazz.getName());
    }

    public static Logger getRootLogger() {
        return LogManager.getRootLogger();
    }

    public static Logger getLogger(String name, LoggerFactory factory) {
        return LogManager.getLogger(name, factory);
    }

    public void trace(Object message) {
        if (!this.repository.isDisabled(5000)) {
            if (Level.TRACE.isGreaterOrEqual(this.getEffectiveLevel())) {
                this.forcedLog(FQCN, Level.TRACE, message, (Throwable)null);
            }

        }
    }
    
    // ...省略部分方法
}
```
很显然log4j的Logger与目标Logger差距还是有点大的。在其继承的Category中，实现了最核心的log方法：

```
public void log(String callerFQCN, Priority level, Object message, Throwable t) {
    if (!this.repository.isDisabled(level.level)) {
        if (level.isGreaterOrEqual(this.getEffectiveLevel())) {
            this.forcedLog(callerFQCN, level, message, t);
        }

    }
}
```
而slf4j的Logger中对应方法的封装也重点围绕该log方法进行。

下面再看适配器类Log4jLoggerAdapter，它先是继承了LegacyAbstractLogger，LegacyAbstractLogger继承了AbstractLogger，AbstractLogger实现了Logger，等于说Log4jLoggerAdapter实现了Logger。我们具体使用的过程中可能没这么多层级。

```
public final class Log4jLoggerAdapter extends LegacyAbstractLogger implements LocationAwareLogger, Serializable {

    private static final long serialVersionUID = 6182834493563598289L;

    final transient org.apache.log4j.Logger logger;

    Log4jLoggerAdapter(org.apache.log4j.Logger logger) {
        this.logger = logger;
        this.name = logger.getName();
        traceCapable = isTraceCapable();
    }

    @Override
    public void log(Marker marker, String callerFQCN, int level, String msg, Object[] arguments, Throwable t) {
        Level log4jLevel = toLog4jLevel(level);
        NormalizedParameters np = NormalizedParameters.normalize(msg, arguments, t);
        String formattedMessage = MessageFormatter.basicArrayFormat(np.getMessage(), np.getArguments());
        logger.log(callerFQCN, log4jLevel, formattedMessage, np.getThrowable());
    }

    // 父类中定义的log、info、warn、error等方法最终都调用了该方法。
	@Override
	protected void handleNormalizedLoggingCall(org.slf4j.event.Level level, Marker marker, String msg, Object[] arguments,
			Throwable throwable) {
		Level log4jLevel = toLog4jLevel(level.toInt());
		String formattedMessage = MessageFormatter.basicArrayFormat(msg, arguments);
		logger.log(getFullyQualifiedCallerName(), log4jLevel, formattedMessage, throwable);
	}
	
	// ...省略部分方法
}
```
Log4jLoggerAdapter中主要通过构造方法将org.apache.log4j.Logger的对象传入类中，并在各个场景中进行封装和转化。重点方法是handleNormalizedLoggingCall方法，在其中调用了Log4j对应的log方法。而slf4j的Logger的抽象实现方法，比如log、info、warn、error等都调用了该方法。

最后，看一下slf4j的Logger获取，对应在slf4j项目中的Log4jLoggerFactory中：

```
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
```
可以看到Logger本质上就是通过new一个Log4jLoggerAdapter，并传入对应的log4j的Logger实例化对象。

如果将适配器模式按照实现的不同进行分类，可分为：类的适配器模式和对象的适配器模式。上面slf4j的实现属于对象的适配器模式，也就是说适配器类不是使用继承关系连接到Adaptee类，而是使用委派关系连接到Adaptee类。

### 类的适配器模式

除了对象的适配器模式，还有类的适配器模式，适配器类主要通过继承关系来进行实现的。修改一下slf4j的类图，可以得到如下类图（非slf4j实现）：

![适配器模式](http://www.choupangxia.com/wp-content/uploads/2020/12/adapter-02.jpg)

相关的具体实现与对象的适配器模式类似，这里就不再贴代码具体演示了。

### 缺省适配器模式

当不需要全部实现接口提供的方法时，可以设计一个适配器抽象类实现接口，并为接口中的每个方法提供默认方法，抽象类的子类就可以有选择的覆盖父类的某些方法实现需求，适用于一个接口不想使用所有的方法的情况。

在java8后，接口中可以有default方法，就不需要这种缺省适配器模式了。接口中方法都设置为default，实现为空，这样同样可以达到缺省适配器模式同样的效果。

### 适配器模式的优缺点

优点：

- 复用性：不用修改现有的类，通过适配器便可让现有类更好的复用；
- 透明、简单：以slf4j为例，项目中只需要面向统一的接口编程即可，并不用关系底层实现是哪个日志框架；
- 更好的扩展性：slf4j的适配类中，不仅可以实现log4j的功能，还可以添加slf4j自身的功能，很容易达到扩展；
- 解耦性：通过适配器，目标接口和适配者达到了解耦的效果，对适配者来说并不需要修改任何代码。
- 符合开闭原则：以slf4j为例，可以为不同的日志框架提供不同的适配器，也可以统一接口在不同场下提供不同的适配器，而这都不影响适配者类的源代码。


缺点：过多的使用适配器，会让系统变得零乱，不易整体进行把握。针对类的适配器模式，由于使用了继承，耦合性较高。

### 小结

本篇文章通过阅读slf4j源码，发现适配器模式的典型运用，并对适配器模式进行延伸讲解。其实在Spring框架中，我们经常看到以Adapter为后缀的类，基本上都是基于适配器模式，可以留意一下是怎么实现的。


