# slf4j是如何绑定具体的日志框架的？

阅读分析slf4j的日志源码，发现其中涵盖了许多知识点和优秀的设计，关键它们都是活生生的实践案例。写成系列文章与大家分享，欢迎持续关注公众号【程序新视界】。第3篇。

### slf4j日志框架绑定流程

在《slf4j中是如何运用SPI机制兼容各种日志框架的》一文当中，我们讲到slf4j通过SPI机制，可以扫描到对应日志框架jar包中META-INF/services下配置的SLF4JServiceProvider的实现类。进而通过SLF4JServiceProvider的实现类获得对应Logger的工厂类。

关于相关SPI的更多内容，可以回头看上篇文章。本篇文章开始，我们首先想一个问题，如果项目中出现多个日志框架，也就出现多个SLF4JServiceProvider的SPI相关配置，针对这种情况slf4j会如何处理？我们在使用的过程中又该注意些什么？

### LoggerFactory的bind方法

要解决上面的问题，先来看一下LoggerFactory中绑定SLF4JServiceProvider的bind方法的具体实现代码：

```
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
```
通过bind方法源码，我们可以看出绑定SLF4JServiceProvider（也就是对应的日志框架实现该接口的实例）的流程为：
- 1、通过SPI机制扫描项目类路径下所有实现SLF4JServiceProvider接口并按照SPI机制配置的类；
- 2、如果出现多个SLF4JServiceProvider实例，会打印警告信息；
- 3、如果实例列表中存在1个或多个实例，只获取第一个实例。此处需要注意，如果配置多个日志框架，只会使用最先被加载的那一个；
- 4、如果一个日志框架都不存在，则打印警告信息。

上述流程中我们需要注意什么？

第一，尽量避免在使用slf4j的时候出现多个日志框架，即便其他日志框架因三方框架引入，我们也可以通过调整SLF4JServiceProvider的引入来避免。比如当我们引入了logback框架时，对应的SPI配置位于logback-classic-xx.jar中，而logback-core-xx.jar中并没有，也就是如果不引入logback-classic的jar包，就不会触发SPI机制。同样的，如果想使用slf4j来集成log4j，那仅仅引入log4j的日志框架是无效的，还需要引入slf4j为其配置的SPI的依赖slf4j-log4j12，slf4j才能够扫描的到。

第二，如果启动程序时，看到控制台打印如下警告信息，你应该知道slf4j并没有成功扫描到日志框架。

```
SLF4J: No SLF4J providers were found.
SLF4J: Defaulting to no-operation (NOP) logger implementation
SLF4J: See http://www.slf4j.org/codes.html#noProviders for further details.
```
上面的警告信息在我们使用了slf4j，但并未引入其他日志框架，或没有引入桥接的依赖时经常会出现。通过阅读上面的源代码，我们很明确的知道该异常信息为什么会发生，如何去解决了。

第三，如果扫描到多个日志框架，怎么确定当前使用了哪个？可通过上述代码中reportActualBinding打印的日志来判断："Actual provider is of type [" + providerList.get(0) + "]"。此时如果并不是项目需要使用的日志框架，可参考第一条，在pom文件中进行排查依赖处理。

绑定多个日志框架，日志输出示例：
```
SLF4J: Class path contains multiple SLF4J bindings.
SLF4J: Found binding in [jar:file:/Users/liyi/.m2/repository/ch/qos/logback/logback-classic/1.2.3/logback-classic-1.2.3.jar!/org/slf4j/impl/StaticLoggerBinder.class]
SLF4J: Found binding in [jar:file:/Users/liyi/.m2/repository/org/apache/logging/log4j/log4j-slf4j-impl/2.10.0/log4j-slf4j-impl-2.10.0.jar!/org/slf4j/impl/StaticLoggerBinder.class]
SLF4J: See http://www.slf4j.org/codes.html#multiple_bindings for an explanation.
SLF4J: Actual binding is of type [ch.qos.logback.classic.util.ContextSelectorStaticBinder]

```

找到多个实现类会取第一个，但是谁是第一个呢？看官方解释：

```
The warning emitted by SLF4J is just that, a warning. Even when multiple bindings are present, SLF4J will pick one logging framework/implementation and bind with it. The way SLF4J picks a binding is determined by the JVM and for all practical purposes should be considered random. As of version 1.6.6, SLF4J will name the framework/implementation class it is actually bound to.
```
答案就是：取决于JVM，你可以认为就是随机的。


### 小结

我们经常在使用日志框架，也经常在使用slf4j，我们还经常看到一些SLF4J的警告信息，只有像本篇文章这样，认证阅读了相应的源码，了解了底层处理的业务逻辑，我们才能够更好的知道什么情况下该做怎样的处理，什么异常是什么原因导致的。

