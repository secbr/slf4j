# 02、slf4j中是如何运用SPI机制兼容各种日志框架的？
阅读分析slf4j的日志源码，发现其中涵盖了许多知识点和优秀的设计，关键它们都是活生生的实践案例。写成系列文章与大家分享，欢迎持续关注。第2篇。

### 前言

在上篇文章中我们讲到了LoggerFactory获取SLF4JServiceProvider实例类时使用到了双重加锁的机制，同时拓展了单例模式的典型场景。在完成加锁及判断之后，调用了初始化方法performInitialization。在该方法中最重要的操作便是调用bind方法。

在bind方法中才真正进行了SLF4JServiceProvider实例的初始化及获取操作。我们将bind方法进行简化处理，核心代码如下：

```
private static void bind() {
    List<SLF4JServiceProvider> providersList = findServiceProviders();
    if(!providersList.isEmpty()) {
        PROVIDER = providersList.get(0);
        PROVIDER.initialize();
        INITIALIZATION_STATE = SUCCESSFUL_INITIALIZATION;
    } else {
        INITIALIZATION_STATE = NOP_FALLBACK_INITIALIZATION;
    }
    postBindCleanUp();
}
```
这节课我们重点来了解findServiceProviders是如何加载和获取SLF4JServiceProvider实例的底层机制。

### Java的SPI机制

findServiceProviders方法的代码如下：

```
private static List<SLF4JServiceProvider> findServiceProviders() {
    ServiceLoader<SLF4JServiceProvider> serviceLoader = ServiceLoader.load(SLF4JServiceProvider.class);
    List<SLF4JServiceProvider> providerList = new ArrayList<>();
    for(SLF4JServiceProvider provider : serviceLoader) {
        providerList.add(provider);
    }
    return providerList;
}
```
通过调用ServiceLoader#load方法，加载了一个具有遍历（实现了Iterable）能力的ServiceLoader对象。然后遍历ServiceLoader中的SLF4JServiceProvider对象，存入List当中并返回List。看起来很简单的一个操作，便涉及到我们今天要讲的SPI机制。ServiceLoader#load方法的调用便是使用SPI的标志。

### 什么是SPI

SPI是Service Provider Interface的缩写，是Java内置的服务发现机制。

在开发的过程中，可以将一些通用的功能抽象成API，对API提供各种具体的实现，这是很正常的场景。但以日志框架为例，是否可以通过提供一套API，当切换底层日志框架时不用修改调用它们API的代码，只用替换成新的实现了该API的jar包即可？很明显slf4j就是用来做这件事的，而它底层便运用了SPI机制。

通过Java的SPI机制，可以实现框架的动态扩展，让第三方的实现能像插件一样嵌入到系统中，我们经常使用数据库API便是另外一个应用场景。

### SPI的简单实现

实现SPI主要分三步：1、定义一个接口；2、提供方的“META-INF/services”目录下新建一个名称为接口全限定名的文本文件，内容为接口实现类的全限定名。3、调用方通过ServiceLoader#load方法加载接口的实现类实例。

在此我们就不单独写示例了，直接以slf4j中的实现为例，来进行讲解。

第一步，定义一个接口，这个很显然，接口便是SLF4JServiceProvider：

```
public interface SLF4JServiceProvider {

    ILoggerFactory getLoggerFactory();
    
    IMarkerFactory getMarkerFactory();
    
    MDCAdapter getMDCAdapter();
    
    String getRequesteApiVersion();
    
    void initialize();
}
```
那么，这个接口是由谁来实现呢？理论说应该是有各个日志框架提供方来实现。比如Logback日志框架就默认实现了该接口，实现类为ch.qos.logback.classic.spi.LogbackServiceProvider。为什么说是理论上呢？这里Logback与slf4j是同一作者，可以在Logback中实现该接口，但像Logback的竞争对手log4j2，并不会主动实现该接口的。

因此，在slf4j的项目中出现了一些名称为：slf4j-log4j12、slf4j-jdk14、slf4j-simple的项目。这些项目存在的目的之一就是来间接实现SLF4JServiceProvider接口。同时，还会提供第二步的配置。

下面来看第二步：提供方的“META-INF/services”目录下新建一个名称为接口全限定名的文本文件，内容为接口实现类的全限定名。

在slf4j中默认提供了一个空的Logger，对应的也有一个NOPServiceProvider实现类。与sl4j的同级项目中单独有一个slf4j-nop项目，就是来提供空日志记录操作的。

首先NOPServiceProvider实现了接口SLF4JServiceProvider，其次在slf4j-nop的META-INF/services目录下有一个名称为org.slf4j.spi.SLF4JServiceProvider的文本文件，文件内的内容为：

```
org.slf4j.helpers.NOPServiceProvider
```

![image](http://www.choupangxia.com/wp-content/uploads/2020/11/slf4j-01.jpg)

完成了接口的定义，接口的实现，以及接口实现方按照指定的格式提供配置文件之后，就可以通过ServiceLoader#load方法加载接口的实现类实例了。关于加载的具体示例，可参考findServiceProviders方法中的代码。值得留意的是ServiceLoader#load方法会加载类路径中所有的jar里面对应的SPI配置类。

其实，ServiceLoader调用load方式并没有进行加载操作，只有当遍历的时候才真正加载和初始化配置文件中的类。

### SPI实现的关键代码

上面我们已经提到，在调用load方法时ServiceLoader并不会加载对应的配置项，而是在遍历的时候进行加载。先来看一下ServiceLoader的load方法源码：

```
public static <S> ServiceLoader<S> load(Class<S> service) {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    return new ServiceLoader<>(Reflection.getCallerClass(), service, cl);
}
```
可以看到只是初始化了一个ServiceLoader，并赋值相应的属性值。而真正进行初始化或加载操作是在foreach方法执行时。在iterator方法中会初始化一个LazyClassPathLookupIterator类，该类为ServiceLoader的内部类，同样实现了Iterator接口，在实现的hasNext和next方法中完成了配置文件的加载和类的实例化。下面来看一下该类的核心内容：

```
private final class LazyClassPathLookupIterator<T> implements Iterator<Provider<T>>{
    // 扫描路径的前缀
    static final String PREFIX = "META-INF/services/";

    // 避免重复，采用Set存储
    Set<String> providerNames = new HashSet<>(); 
    // 扫描到的路径集合
    Enumeration<URL> configs;
    Iterator<String> pending;

    Provider<T> nextProvider;
    ServiceConfigurationError nextError;

    LazyClassPathLookupIterator() { }

    private Class<?> nextProviderClass() {
        if (configs == null) {
            try {
                // 拼接扫描路径+名称
                String fullName = PREFIX + service.getName();
                if (loader == null) {
                    configs = ClassLoader.getSystemResources(fullName);
                } else if (loader == ClassLoaders.platformClassLoader()) {
                    // The platform classloader doesn't have a class path,
                    // but the boot loader might.
                    if (BootLoader.hasClassPath()) {
                        configs = BootLoader.findResources(fullName);
                    } else {
                        configs = Collections.emptyEnumeration();
                    }
                } else {
                    configs = loader.getResources(fullName);
                }
            } catch (IOException x) {
                fail(service, "Error locating configuration files", x);
            }
        }
        while ((pending == null) || !pending.hasNext()) {
            if (!configs.hasMoreElements()) {
                return null;
            }
            // 解析配置文件中的内容
            pending = parse(configs.nextElement());
        }
        String cn = pending.next();
        try {
            // 根据配置进行类的初始化
            return Class.forName(cn, false, loader);
        } catch (ClassNotFoundException x) {
            fail(service, "Provider " + cn + " not found");
            return null;
        }
    }
    // ...
}
```
上述代码省略了细节操作，我们可以看到该内部类会根据默认的路径类名进行拼接扫描，这也就是为什么我们第二步要按照指定的格式进行定义。扫描到对应的文件之后，会对文件中配置的类的全限定名字符串进行加载，最后通过Class#forName进行初始化。

关于ServiceLoader的源代码就讲这么多，大家如果有兴趣，可以通过断点来跟踪一下，更方便理解细节。看完源代码其实就很容易明白SPI机制，本质上SPI机制就是定义一个约定，大家都按照这个约定来，然后使用者根据约定去解析使用即可。明白了这个底层原理，读者自己也可以实现一个简单的SPI。而dubbo对SPI的实现就是基于自身的功能场景进行了拓展，这就是为什么我们要活学活用的原因。

### 小结

通过本篇文章我们了解了LoggerFactory在获取SLF4JServiceProvider时所使用的SPI机制，不同的日志框架直接或间接实现sl4j定义的接口，并遵从SPI机制，sl4j便会对其进行加载和初始化处理。同时，也阅读了一下ServiceLoader的核心代码，更方便学习和了解SPI。

本篇文章就到这里了，阅读源码是不是也很有意思，很有收获吧？下一篇我们继续跟进，期待一下吧。




