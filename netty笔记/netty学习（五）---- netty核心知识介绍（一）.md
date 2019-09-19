###netty学习（五）---- netty介绍以及第一个netty程序

###Netty简介

Netty是一个高性能、异步事件驱动的NIO框架，它提供了对TCP、UDP和文件传输的支持，作为一个异步NIO框架，Netty的所有IO操作都是异步非阻塞的，通过Future-Listener机制，用户可以方便的主动获取或者通过通知机制获得IO操作结果。

作为当前最流行的NIO框架，Netty在互联网领域、大数据分布式计算领域、游戏行业、通信行业等获得了广泛的应用，一些业界著名的开源组件也基于Netty的NIO框架构建。

### Reactor模型

Netty中的Reactor模型主要由多路复用器(Acceptor)、事件分发器(Dispatcher)、事件处理器(Handler)组成，可以分为三种。

1、单线程模型：所有I/O操作都由一个线程完成，即多路复用、事件分发和处理都是在一个Reactor线程上完成的。

![img](https://upload-images.jianshu.io/upload_images/2184951-67e4d230991bbd84.png?imageMogr2/auto-orient/strip%7CimageView2/2)


对于一些小容量应用场景，可以使用单线程模型。但是对于高负载、大并发的应用却不合适，主要原因如下：

- 一个线程同时处理成百上千的链路，性能上无法支撑，即便CPU负荷达到100%，也无法满足海量消息的编码、解码、读取和发送；
- 当负载过重后，处理速度将变慢，这会导致大量客户端连接超时，超时之后往往会进行重发，最终会导致大量消息积压和处理超时，成为系统的性能瓶颈；
- 一旦单线程意外跑飞，或者进入死循环，会导致整个系统通信模块不可用，不能接收和处理外部消息，造成节点故障，可靠性不高。

2、多线程模型：为了解决单线程模型存在的一些问题，演化而来的Reactor线程模型。

![img](https://upload-images.jianshu.io/upload_images/2184951-069b2818fa205975.png?imageMogr2/auto-orient/strip%7CimageView2/2)


多线程模型的特点：

- 有专门一个Acceptor线程用于监听服务端，接收客户端的TCP连接请求；
- 网络IO的读写操作由一个NIO线程池负责，线程池可以采用标准的JDK线程池实现，包含一个任务队列和N个可用的线程，由这些NIO线程负责消息的读取、解码、编码和发送；
- 一个NIO线程可以同时处理多条链路，但是一个链路只能对应一个NIO线程，防止发生并发操作问题。

在绝大多数场景下，Reactor多线程模型都可以满足性能需求；但是，在极特殊应用场景中，一个NIO线程负责监听和处理所有的客户端连接可能会存在性能问题。例如百万客户端并发连接，或者服务端需要对客户端的握手消息进行安全认证，认证本身非常损耗性能。在这类场景下，单独一个Acceptor线程可能会存在性能不足问题，为了解决性能问题，产生了第三种Reactor线程模型-主从Reactor多线程模型。

3、主从多线程模型：采用多个reactor，每个reactor都在自己单独的线程里执行。如果是多核，则可以同时响应多个客户端的请求，一旦链路建立成功就将链路注册到负责I/O读写的SubReactor线程池上。

![img](https://upload-images.jianshu.io/upload_images/2184951-7af88a4ac1742965.png?imageMogr2/auto-orient/strip%7CimageView2/2)

事实上，Netty的线程模型并非固定不变，在启动辅助类中创建不同的EventLoopGroup实例并通过适当的参数配置，就可以支持上述三种Reactor线程模型。正是因为Netty对Reactor线程模型的支持提供了灵活的定制能力，所以可以满足不同业务场景的性能需求。



###Netty核心知识介绍

netty中有几个核心的组件：

![1568622122975](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568622122975.png)

*** Channel以及ChannelHander、ChannelPipeline**

Channel不同于jdk提供的Channel。Channel对应于Socket，用于网络通讯。Channel在netty中所有的io操作都是异步的，也就是说只要你进行io操作就会立刻得到响应。



先简略了解一下`ChannelPipeline`和`ChannelHandler`.

想象一个流水线车间.当组件从流水线头部进入,穿越流水线,流水线上的工人按顺序对组件进行加工,到达流水线尾部时商品组装完成.

可以将`ChannelPipeline`当做流水线,`ChannelHandler`当做流水线工人.源头的组件当做event,如read,write等等.



### 1.1 Channel

```java
Channel`连接了网络套接字或能够进行I/O操作的组件,如`read, write, connect, bind.
```

我们可以通过`Channel`获取一些信息.

- `Channel`的当前状态(如,是否连接,是否打开)
- `Channel`的配置参数,如buffer的size
- 支持的I/O操作
- 处理所有I/O事件的`ChannelPipeline`和与通道相关的请求

`Channel`接口定义了一组和`ChannelInboundHandler`API密切相关的状态模型.

![52896437562](https://note.youdao.com/yws/api/personal/file/254597514A924A3A8D810C245E398E4E?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

> 当`Channel`的状态改变,会生成对应的event.这些event会转发给`ChannelPipeline`中的`ChannelHandler`,handler会对其进行响应.

![img](https://note.youdao.com/yws/api/personal/file/C59E46BC97324AFC8022974077413875?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

### 1.2 ChannelHandler生命周期

> 下面列出了 interface ChannelHandler 定义的生命周期操作， 在 ChannelHandler被添加到 ChannelPipeline 中或者被从 ChannelPipeline 中移除时会调用这些操作。这些方法中的每一个都接受一个 ChannelHandlerContext 参数

![img](https://note.youdao.com/yws/api/personal/file/4D2E136DA9564B01821709BC19A65F78?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

### 1.3 ChannelInboundHandler 接口

> `ChannelInboundHandler`处理入站数据以及各种状态变化,当`Channel`状态发生改变会调用`ChannelInboundHandler`中的一些生命周期方法.这些方法与`Channel`的生命密切相关.

入站数据,就是进入`socket`的数据.下面展示一些该接口的生命周期API

![img](https://note.youdao.com/yws/api/personal/file/36D2281DCFB5423591974200D98BC4F2?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

> 当某个 `ChannelInboundHandler`的实现重写 `channelRead()`方法时，它将负责显式地
> 释放与池化的 ByteBuf 实例相关的内存。 Netty 为此提供了一个实用方法`ReferenceCountUtil.release()`.

```java
@Sharable
public class DiscardHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        //释放资源
        ReferenceCountUtil.release(msg);
    }
}
```

这种方式还挺繁琐的,Netty提供了一个`SimpleChannelInboundHandler`,重写`channelRead0()`方法,就可以在调用过程中会自动释放资源.

```java
public class SimpleDiscardHandler
    extends SimpleChannelInboundHandler<Object> {
    @Override
    public void channelRead0(ChannelHandlerContext ctx,
                                    Object msg) {
            // 不用调用ReferenceCountUtil.release(msg)也会释放资源
    }
}
```

原理就是这样,`channelRead`方法包装了`channelRead0`方法.

```java
@Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        boolean release = true;
        try {
          
            if (acceptInboundMessage(msg)) {
                @SuppressWarnings("unchecked")
                I imsg = (I) msg;
                channelRead0(ctx, imsg);
            } else {
                release = false;
                ctx.fireChannelRead(msg);
            }
        } finally {
            if (autoRelease && release) {
                ReferenceCountUtil.release(msg);
            }
        }
    }
```

### 1.4 ChannelOutboundHandler

出站操作和数据将由 ChannelOutboundHandler 处理。它的方法将被 Channel、 ChannelPipeline 以及 ChannelHandlerContext 调用。
ChannelOutboundHandler 的一个强大的功能是可以按需推迟操作或者事件，这使得可以通过一些复杂的方法来处理请求。例如， 如果到远程节点的写入被暂停了， 那么你可以推迟冲刷操作并在稍后继续。



**ChannelPromise**与**ChannelFuture**: ChannelOutboundHandler中的大部分方法都需要一个ChannelPromise参数， 以便在操作完成时得到通知。 ChannelPromise是ChannelFuture的一个子类，其定义了一些可写的方法，如setSuccess()和setFailure()， 从而使ChannelFuture不可变.



### 1.5 ChannelHandler适配器

ChannelHandlerAdapter顾名思义,就是handler的适配器.你需要知道什么是适配器模式,假设有一个A接口,我们需要A的subclass实现功能,但是B类中正好有我们需要的功能,不想复制粘贴B中的方法和属性了,那么可以写一个适配器类Adpter继承B实现A,这样一来Adpter是A的子类并且能直接使用B中的方法,这种模式就是适配器模式.

就比如Netty中的`SslHandler`类,想使用`ByteToMessageDecoder`中的方法进行解码,但是必须是`ChannelHandler`子类对象才能加入到`ChannelPipeline`中,通过如下签名和其实现细节(`SslHandler`实现细节就不贴了)就能够作为一个Handler去处理消息了.

```java
public class SslHandler extends ByteToMessageDecoder implements ChannelOutboundHandler
```

下图是ChannelHandler和Adpter的UML图示.

![img](https://note.youdao.com/yws/api/personal/file/E0A7CA5B3C4D4C268BD8C768DE2146B7?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

> ChannelHandlerAdapter提供了一些实用方法`isSharable()`如果其对应的实现被标注为 Sharable， 那么这个方法将返回 true， 表示它可以被添加到多个 ChannelPipeline中 .
>
> 如果想在自己的ChannelHandler中使用这些适配器类,只需要扩展他们,重写那些想要自定义的方法即可.

### 1.6 资源管理

在使用`ChannelInboundHandler.channelRead()`或`ChannelOutboundHandler.write()`方法处理数据时要避免资源泄露,ByteBuf那篇文章提到过引用计数,当使用完某个ByteBuf之后记得调整引用计数.

Netty提供了一个`class ResourceLeakDetector`来帮助诊断资源泄露,这能够帮助你判断应用的运行情况,但是如果希望提高吞吐量(比如搞一些竞赛),关闭内存诊断可以提高吞吐量.

![img](https://note.youdao.com/yws/api/personal/file/BC758FCAE8ED407EA584D1ECD502BE34?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

> 泄露检测级别可以通过将下面的 Java 系统属性设置为表中的一个值来定义：
> `-Dio.netty.leakDetectionLevel=ADVANCED`
>
> 如果带着该 JVM 选项重新启动你的应用程序，你将看到自己的应用程序最近被泄漏的缓冲
> 区被访问的位置(前提是要设置日志打印)。下面是一个典型的由单元测试产生的泄漏报告：

*** 回调的含义**

通常来说，将你的方法托管给操作系统去调用，这种情况算作一种回调。类似于aio模型中，Completion接口有两个方法，completed和failed，那么每当我们完成一个操作的时候，就会不阻塞去等待操作系统的处理完成，而当操作系统处理完毕，或者某些事件满足条件的时候，就会去调用Completion这个接口对应的方法，这个过程称作回调；简单的讲就是 A 线程 将一个方法托管给 B线程，B线程处理完后再某个时刻调用A线程所给的方法去通知A线程做相应的逻辑处理，这个过程叫做回调。

#### 应用程序处理消息释放资源

**消费入站消息释放资源**

```java
@Sharable
public class DiscardInboundHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ReferenceCountUtil.release(msg);// 用于释放资源的工具类
    }
}
```

`SimpleChannelInboundHandler`中的channelRead0()会消费消息之后自动释放资源.

**出站释放资源**

```java
@Sharable
public class DiscardOutboundHandler
                        extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx,
        Object msg, ChannelPromise promise) {
        // 还是通过util工具类释放资源
        ReferenceCountUtil.release(msg);
        // 通知ChannelPromise,消息已经处理
        promise.setSuccess();
    }
}
```

重要的是， 不仅要释放资源，还要通知 ChannelPromise。否则可能会出现 Channel的FutureListener 收不到某个消息已经被处理了的通知的情况。总之，如果一个消息被消费或者丢弃了， 并且没有传递给 ChannelPipeline 中的下一个ChannelOutboundHandler， 那么用户就有责任调用ReferenceCountUtil.release()。如果消息到达了实际的传输层， 那么当它被写入时或者 Channel 关闭时，都将被自动释放。

### 2. ChannelPipeline接口

#### Channel和ChannelPipeline

> 每一个新创建的 Channel 都将会被分配一个新的 ChannelPipeline。这项关联是永久性的； Channel 既不能附加另外一个 ChannelPipeline，也不能分离其当前的。在 Netty 组件的生命周期中，这是一项固定的操作，不需要开发人员的任何干预。



#### ChannelHandler和ChannelHandlerContext

根据事件的起源，事件将会被 ChannelInboundHandler 或者 ChannelOutboundHandler 处理。随后， 通过调用 ChannelHandlerContext 实现，它将被转发给同一超类型的下一个ChannelHandler。

ChannelHandlerContext使得ChannelHandler能够和它的ChannelPipeline以及其他的ChannelHandler 交 互 。 ChannelHandler 可 以 通 知 其 所 属 的 ChannelPipeline 中 的 下 一 个ChannelHandler，甚至可以动态修改它所属的ChannelPipeline.

![1568622016129](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568622016129.png)

这是一个同时具有入站和出站 ChannelHandler 的 ChannelPipeline 的布局，并且印证了我们之前的关于 ChannelPipeline 主要由一系列的 ChannelHandler 所组成的说法。 ChannelPipeline 还提供了通过 ChannelPipeline 本身传播事件的方法。如果一个入站事件被触发，它将被从 ChannelPipeline 的头部开始一直被传播到 Channel Pipeline 的尾端。

你可能会说， 从事件途经 ChannelPipeline 的角度来看， ChannelPipeline 的头部和尾端取决于该事件是入站的还是出站的。然而 Netty 总是将 ChannelPipeline 的入站口（图 的左侧）作为头部，而将出站口（该图的右侧）作为尾端。
当你完成了通过调用 ChannelPipeline.add*()方法将入站处理器（ ChannelInboundHandler）和 出 站 处 理 器 （ ChannelOutboundHandler ） 混 合 添 加 到 ChannelPipeline 之 后 ， 每 一 个ChannelHandler 从头部到尾端的顺序位置正如同我们方才所定义它们的一样。因此，如果你将图 6-3 中的处理器（ ChannelHandler）从左到右进行编号，那么第一个被入站事件看到的 ChannelHandler 将是1，而第一个被出站事件看到的 ChannelHandler 将是 5。

在 ChannelPipeline 传播事件时，它会测试 ChannelPipeline 中的下一个 ChannelHandler 的类型是否和事件的运动方向相匹配。如果不匹配， ChannelPipeline 将跳过该ChannelHandler 并前进到下一个，直到它找到和该事件所期望的方向相匹配的为止。 （当然， ChannelHandler 也可以同时实现ChannelInboundHandler 接口和 ChannelOutboundHandler 接口。）



### 2.1 修改ChannelPipeline

修改指的是添加或删除`ChannelHandler`

![img](https://note.youdao.com/yws/api/personal/file/23EC020DA10B468DBBA5801DE8FAE52D?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

代码示例

```java
ChannelPipeline pipeline = ..;
FirstHandler firstHandler = new FirstHandler();
// 先添加一个Handler到ChannelPipeline中
pipeline.addLast("handler1", firstHandler);
// 这个Handler放在了first,意味着放在了handler1之前
pipeline.addFirst("handler2", new SecondHandler());
// 这个Handler被放到了last,意味着在handler1之后
pipeline.addLast("handler3", new ThirdHandler());
...
// 通过名称删除
pipeline.remove("handler3");
// 通过对象删除
pipeline.remove(firstHandler);
// 名称"handler2"替换成名称"handler4",并切handler2的实例替换成了handler4的实例
pipeline.replace("handler2", "handler4", new ForthHandler());
```

这种方式非常灵活,按照需要更换或插入`handler`达到我们想要的效果.

ChannelHandler的执行和阻塞

**通常 ChannelPipeline 中的每一个 ChannelHandler 都是通过它的 EventLoop（ I/O 线程）来处理传递给它的事件的。所以至关重要的是不要阻塞这个线程，因为这会对整体的 I/O 处理产生负面的影响。**

但有时可能需要与那些使用阻塞 API 的遗留代码进行交互。对于这种情况， ChannelPipeline 有一些接受一个 EventExecutorGroup 的 add()方法。如果一个事件被传递给一个自定义的 EventExecutorGroup ,它将被包含在这个 EventExecutorGroup 中的某个 EventExecutor 所处理，从而被从该Channel 本身的 EventLoop 中移除。对于这种用例， Netty 提供了一个叫 DefaultEventExecutorGroup 的默认实现。

**pipeline对handler的操作**

![img](https://note.youdao.com/yws/api/personal/file/EF874390E09942E0883D5543662B7A51?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)



### 2.2 ChannelPipeline的出入站api

**入站**

![img](https://note.youdao.com/yws/api/personal/file/C0DEC15616024AE18EB07EF187E688FC?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

**出站**

![img](https://note.youdao.com/yws/api/personal/file/07D5D501AABA4B1AB3F776788A45FFD5?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

- ChannelPipeline 保存了与 Channel 相关联的 ChannelHandler
- ChannelPipeline 可以根据需要，通过添加或者删除 ChannelHandler 来动态地修改
- ChannelPipeline 有着丰富的 API 用以被调用，以响应入站和出站事件



### 3 ChannelHandlerContext接口

每当有`ChannelHandler`添加到`ChannelPipeline`中,都会创建`ChannelHandlerContext`.如果调用`Channel`或`ChannelPipeline`上的方法,会沿着整个`ChannelPipeline`传播,如果调用`ChannelHandlerContext`上的相同方法,则会从对应的当前`ChannelHandler`进行传播.

#### API

![img](https://note.youdao.com/yws/api/personal/file/D25881AF9D1B4D9F8568769AE6AA5260?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

![img](https://note.youdao.com/yws/api/personal/file/2B78270B7FBA4A4991046300E2158824?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

- `ChannelHandlerContext` 和 `ChannelHandler`之间的关联（绑定）是永远不会改变的，所以缓存对它的引用是安全的；
- 如同我们在本节开头所解释的一样，相对于其他类的同名方法，`ChannelHandlerContext`的方法将产生更短的事件流， 应该尽可能地利用这个特性来获得最大的性能。

### 3.1 使用CHannelHandlerContext

![img](https://note.youdao.com/yws/api/personal/file/20DC76DBEA664952AD7B853553BD451A?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

从ChannelHandlerContext访问channel

```java
ChannelHandlerContext ctx = ..;
// 获取channel引用
Channel channel = ctx.channel();
// 通过channel写入缓冲区
channel.write(Unpooled.copiedBuffer("Netty in Action",
CharsetUtil.UTF_8));
```

从ChannelHandlerContext访问ChannelPipeline

```java
ChannelHandlerContext ctx = ..;
// 获取ChannelHandlerContext
ChannelPipeline pipeline = ctx.pipeline();
// 通过ChannelPipeline写入缓冲区
pipeline.write(Unpooled.copiedBuffer("Netty in Action",
CharsetUtil.UTF_8));
```

![img](https://note.youdao.com/yws/api/personal/file/3B5B19DDCE1947D592D1E467DA09E52A?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

有时候我们不想从头传递数据,想跳过几个handler,从某个handler开始传递数据.我们必须获取目标handler之前的handler关联的ChannelHandlerContext.

```java
ChannelHandlerContext ctx = ..;
// 直接通过ChannelHandlerContext写数据,发送到下一个handler
ctx.write(Unpooled.copiedBuffer("Netty in Action", CharsetUtil.UTF_8));
```



![img](https://note.youdao.com/yws/api/personal/file/BF69ACA726B74324B80CE992D9E1B2E2?method=download&shareKey=952006455eadac7758f7ae04b1d59c7f)

好了,ChannelHandlerContext的基本使用应该掌握了,但是你真的理解ChannelHandlerContext,ChannelPipeline和Channelhandler之间的关系了吗.我们老看一下Netty的源码.

先看一下`AbstractChannelHandlerContext`类,这个类像不像双向链表中的一个Node,

```java
abstract class AbstractChannelHandlerContext extends DefaultAttributeMap
        implements ChannelHandlerContext, ResourceLeakHint {
        ...
        volatile AbstractChannelHandlerContext next;
        volatile AbstractChannelHandlerContext prev;
        ...
        }
```

再来看一看`DefaultChannelPipeline`,`ChannelPipeline`中拥有`ChannelHandlerContext`这个节点的head和tail,

而且`DefaultChannelPipeline`类中并没有`ChannelHandler`成员或handler数组.

```java
public class DefaultChannelPipeline implements ChannelPipeline {
    ...
        
    final AbstractChannelHandlerContext head;
    final AbstractChannelHandlerContext tail;
    ...
```

所以`addFirst`向pipeline中添加了handler到底添加到哪了呢.看一下pipeline中的addFirst方法

```java
  @Override
    public final ChannelPipeline addFirst(String name, ChannelHandler handler) {
        return addFirst(null, name, handler);
    }

    @Override
    public final ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler) {
        final AbstractChannelHandlerContext newCtx;
        synchronized (this) {
            // 检查handler是否具有复用能力,不重要
            checkMultiplicity(handler);
            // 名称,不重要.
            name = filterName(name, handler);
// 这个方法创建了DefaultChannelHandlerContext,handler是其一个成员属性
// 你现在应该明白了上面说的添加handler会创建handlerContext了吧
            newCtx = newContext(group, name, handler);
// 这个方法
            addFirst0(newCtx);
```

```java
// 这个方法是调整pipeline中HandlerContext的指针,
// 就是更新HandlerContext链表节点之间的位置
private void addFirst0(AbstractChannelHandlerContext newCtx) {
        AbstractChannelHandlerContext nextCtx = head.next;
        newCtx.prev = head;
        newCtx.next = nextCtx;
        head.next = newCtx;
        nextCtx.prev = newCtx;
    }
```

简单总结一下,pipeline拥有context(本身像一个链表的节点)组成的节点的双向链表首尾,可以看做pipeline拥有一个context链表,context拥有成员handler,这便是三者之间的关系.实际上,handler作为消息处理的主要组件,实现了和pipeline的解耦,我们可以只有一个handler,但是被封装进不同的context能够被不同的pipeline使用.

### 3.2 handler和context高级用法

缓存ChannelHandlerContext引用

```java
@Sharable
public class WriteHandler extends ChannelHandlerAdapter {
    private ChannelHandlerContext ctx;
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }
    public void send(String msg) {
        ctx.writeAndFlush(msg);
    }
}
```

> 因为一个 ChannelHandler 可以从属于多个 ChannelPipeline，所以它也可以绑定到多个 ChannelHandlerContext 实例。 对于这种用法指在多个ChannelPipeline 中共享同一个 ChannelHandler， 对应的 ChannelHandler 必须要使用@Sharable 注解标注； 否则，试图将它添加到多个 ChannelPipeline 时将会触发异常。

**@Sharable错误用法**

```java
@Sharable
public class UnsharableHandler extends ChannelInboundHandlerAdapter {
    private int count;
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        count++;
        System.out.println("channelRead(...) called the "
            + count + " time");
        ctx.fireChannelRead(msg);
    }
}
```

这段代码的问题在于它拥有状态 ， 即用于跟踪方法调用次数的实例变量count。将这个类的一个实例添加到ChannelPipeline将极有可能在它被多个并发的Channel访问时导致问题。（当然，这个简单的问题可以通过使channelRead()方法变为同步方法来修正。）

总之，只应该在确定了你的 ChannelHandler 是线程安全的时才使用@Sharable 注解。

### 4.1 入站异常处理

处理入站事件的过程中有异常被抛出，那么它将从它在ChannelInboundHandler里被触发的那一点开始流经 ChannelPipeline。要想处理这种类型的入站异常，你需要在你的 ChannelInboundHandler 实现中重写下面的方法。

```java

// 基本处理方式
public class InboundExceptionHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,
                                    Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
```

因为异常将会继续按照入站方向流动（就像所有的入站事件一样）， 所以实现了前面所示逻辑的 ChannelInboundHandler 通常位于 ChannelPipeline 的最后。这确保了所有的入站异常都总是会被处理，无论它们可能会发生在ChannelPipeline 中的什么位置。

- ChannelHandler.exceptionCaught()的默认实现是简单地将当前异常转发给ChannelPipeline 中的下一个 ChannelHandler；
- 如果异常到达了 ChannelPipeline 的尾端，它将会被记录为未被处理；
- 要想定义自定义的处理逻辑，你需要重写 exceptionCaught()方法。然后你需要决定是否需要将该异常传播出去。

### 4.2 出站异常处理

- 每个出站操作都将返回一个 ChannelFuture。 注册到 ChannelFuture 的 ChannelFutureListener 将在操作完成时被通知该操作是成功了还是出错了。
- 几乎所有的 ChannelOutboundHandler 上的方法都会传入一个 ChannelPromise
  的实例。作为 ChannelFuture 的子类， ChannelPromise 也可以被分配用于异步通
  知的监听器。但是， ChannelPromise 还具有提供立即通知的可写方法：

```java
ChannelPromise setSuccess();
ChannelPromise setFailure(Throwable cause);
```

1.添加ChannelFutureListener到ChannelFuture

```java
  ChannelFuture future = channel.write(someMessage);
    future.addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture f) {
            if (!f.isSuccess()) {
                f.cause().printStackTrace();
                f.channel().close();
            }
         }
    });
```

2.添加ChannelFutureListener到ChannelPromise

```java
public class OutboundExceptionHandler extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg,
        ChannelPromise promise) {
            promise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture f) {
                    if (!f.isSuccess()) {
                        f.cause().printStackTrace();
                        f.channel().close();
                    }
                }
        });
    }
}
```





> netty中的Future可以注册一个监听器，当系统操作完毕后将结果成功与否调用相应的成功或失败的业务处理方法。而不是一直循环get，然后再判断使用哪个处理逻辑。
>
> netty中的事件：要在选择器上不断的轮询有没有事件的发生，判断事件的类型。
>
> netty的事件分为：入站事件（别人给我的事件），比如用户自定义事件，读事件等。
>
> ​								出站事件（我给别人的事件），把我们的数据写给客户端

