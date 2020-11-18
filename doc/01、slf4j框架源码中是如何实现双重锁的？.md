# 01、slf4j框架源码中是如何实现双重锁的

阅读分析slf4j的日志源码，发现其中涵盖了许多知识点和优秀的设计，关键它们都是活生生的实践案例。因此专门写成系列文章与大家分享，欢迎持续关注公众号【程序新视界】。第1篇。

### 前言

阅读源码，必然需要先将源代码下载到本地，本篇为本系列第一篇，因此特意建议大家在阅读本篇文章时看一下配套的源代码（持续更新）。本文章已经将原有的代码fork到笔者的GitHub仓库，后续代码均以fork过来的代码为基准进行讲解。地址：https://github.com/secbr/slf4j

好了，本篇文章我们就从使用日志框架最常见的入口类及方法入手。当我们使用slf4j时，通常通过如下代码获取对应的Logger：

```
Logger logger = LoggerFactory.getLogger(NoBindingTest.class);
```
在LoggerFactory的getLogger方法中，最主要的功能就是获得Logger，获得Logger需要先获得对应的ILoggerFactory，而ILoggerFactory又是通过SLF4JServiceProvider初始化和返回的。

本文重点聊聊获取和初始化SLF4JServiceProvider过程中使用到的双重锁机制。

### 双重锁实现源码

在LoggerFactory类最后有一个名为getProvider的方法，提供了返回当前正在使用的SLF4JServiceProvider实例的功能。具体代码如下：

```
static SLF4JServiceProvider getProvider() {
    if (INITIALIZATION_STATE == UNINITIALIZED) {
        synchronized (LoggerFactory.class) {
            if (INITIALIZATION_STATE == UNINITIALIZED) {
                INITIALIZATION_STATE = ONGOING_INITIALIZATION;
                performInitialization();
            }
        }
    }
    switch (INITIALIZATION_STATE) {
    case SUCCESSFUL_INITIALIZATION:
        return PROVIDER;
    case NOP_FALLBACK_INITIALIZATION:
        return NOP_FALLBACK_FACTORY;
    case FAILED_INITIALIZATION:
        throw new IllegalStateException(UNSUCCESSFUL_INIT_MSG);
    case ONGOING_INITIALIZATION:
        // support re-entrant behavior.
        // See also http://jira.qos.ch/browse/SLF4J-97
        return SUBST_PROVIDER;
    }
    throw new IllegalStateException("Unreachable code");
}
```
从上面的代码可以大概看出获取SLF4JServiceProvider分两步，第一步就是初始化，第二步就是通过switch来比对当前实例化的状态（或阶段），然后返回对应的实例对象或抛出异常。

其中第一步操作便使用到了双重锁。下面根据代码分析一下源码中双重锁的使用流程。

如果只是简单的使用锁机制，防止重复实例化SLF4JServiceProvider对象，直接在getProvider方法上添加synchronized便可。但这就面临性能问题，每次调用该方法时都是同步处理的。而通常情况下只有第一次初始化时有锁的需求。

那么此时可以将锁缩小范围，判断当前情况，只有当未初始化（UNINITIALIZED）时再进行加锁，然后调用初始化操作。但此时如果初始化操作比较耗时，两个线程判断时都是未初始化，都进行初始化操作，只不过一先一后，就有可能初始化两次。

此时，进入锁之后，再进行一次判断，如果是未初始化再进行初始化，由于此时已经进入了锁内部，判断不会存在并发情况（这里并不完全准确，还涉及到指令重排情况），那么就避免了初始化两次的情况。

同时，经过第一次初始化之后，再次获取单例对象时，每次判断都不符合初始化的条件，也就不会走锁的逻辑，大大提高了并发。

整个双重锁的实现步骤便是：1、判断是否符合初始化条件；2、加锁当前类；3、再次判断是否符合初始化条件；4、初始化。

### 单例模式中的双重锁

slf4j框架源码中的双重锁主要是用来初始化SLF4JServiceProvider对象，基本上就是我们在实践或面试过程中经常提到的单例模式。

而且通过slf4j的源码可以看出此处的单例模式属于懒汉模式，也就是只有当我们第一次调用LoggerFactory#getLogger方法时才会进行初始化。

下面以一个简单的单例模式再回顾一下双重锁的实现示例：

```
public class Singleton {
    private volatile static Singleton instance;

    private Singleton() {
    }

    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();
                }
            }
        }

        return instance;
    }
}
```
在上述代码当中我们看到Singleton变量使用到了volatile进行修饰。这是因为synchronized并不是对instance实例进行加锁（此时还并没有实例），所以在线程执行完初始化赋值操作之后，应该将修后的instance立即写入主内存（main memory），而不是暂时存在寄存器或者高速缓冲区（caches）中，以保证新的值对其它线程可见。

另外在上述单例模式中，new指令并不是原子操作，一般分为三步：1、分配对象内存；2、调用构造器方法，执行初始化；3、将对象引用赋值给变量。

而虚拟机在执行的时候并不一定按照上面1、2、3步骤进行执行，会发生“指令重排”，那就有可能执行的顺序为1、3、2。那么，第一个线程执行完1、3之后，第二个线程进来了，判断变量已经被赋值，就直接返回了，此时会便会发生空指针异常。而当对象通过volatile修饰之后，便禁用了虚拟机的指令重排。

因此，此处volatile是必须添加的，有两个作用：保证可见性和禁止指令重排优化。

回到slf4j中返回的成功初始化的对象PROVIDER时，PROVIDER变量对应的定义同样使用了volatile关键字修饰：

```
static volatile SLF4JServiceProvider PROVIDER;
```

如果面试单例模式，你能回答到单例模式的双重锁已经很不错了，但如果还能说清楚待实例化的变量使用volatile修饰的原因，那就完美了。认真阅读源码，还是有所收获的吧。

### 小结

本篇文章带大家初步了解了slf4j框架中双重锁实现的案例，同时分析了基本的原理与机制，有兴趣的朋友可翻阅一下相关源码，更加直观的进行学习。下篇文章我们继续阅读源码，提炼知识点，既学习了源码又学习了知识点，赶紧关注一下公众号【程序新视界】吧。
