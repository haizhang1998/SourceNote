### netty学习（六）---- netty核心知识介绍（二）

上一张提及了Channel，和Channel的入站出站事件，以及每个事件对应一个独特的ChannelHandlerContex ，还有每个Channel对应一个自己的pipeline。他们的用法以及原理分析都介绍完毕了。本章要介绍的要点如下：

* **EventLoop接口和EventLoopGroup（类似于线程组）的使用**
* **ByteBuf的概念和用法**



**1. EventLoop接口和EventLoopGroup**

EventLoop 可以看作成只有一个线程的线程池，下面是它的继承关系图：

![1568625637809](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568625637809.png)



从图中可以看到：

* EventLoop简介的继承了ExecutorService ,而所有的线程池都继承到了这个类，说明EventLoop可能和线程池一样的作用。
* EventLoop继承了ScheduleExecutorService，表明该接口可以进行定时任务调度；可以完成服务器的心跳检测、轮询等操作
* EventLoop继承了Iterable接口，表明可以进行迭代。

**这里讲下EventLoop支持的任务调度功能：**

我们可以用这个特性去完成对服务器的心跳检测，防止服务器处于僵尸态，比如客户端交给服务器某个请求，但是此时的服务器不工作但是仍然存活！不会对请求做任何处理，但是又不报错。这个时候就要对服务器发送心跳报文来确认是否出现这种现象，以及时的采取相应的政策，类似于keep-alive机制。

>  tcp-ip协议也会有keep-alive机制，但是时间太久，大概24小时发送一次心跳报文



EventLoopGroup则类似于线程组，保存着多个EventLoop，他们的关系图如下：

![1568625974885](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568625974885.png)

EventLoopGroup则负责管理EventLoop，而EventLoop内部关联一个Thread去处理它所有Channel的所有的事件（你可以把EventLoop看成只有1个线程的线程池）。





### 2. Netty中的ByteBuf

**Netty ByteBuf 优势**

Netty 提供了ByteBuf，来替代Java NIO的 ByteBuffer 缓存，来操纵内存缓冲区。

与Java NIO的 ByteBuffer 相比，ByteBuf的优势如下：

1. Pooling (池化，这点减少了内存复制和GC，提升效率)
2. 可以自定义缓冲类型
3. 通过一个内置的复合缓冲类型实现零拷贝
4. 扩展性好，比如 StringBuffer
5. 不需要调用 flip()来切换读/写模式
6. 读取和写入索引分开
7. 方法链
8. 引用计数

**手动获取与释放ByteBuf**

Netty环境下，业务处理的代码，基本上都在Handler处理器中的各个入站和出站方法中。

一般情况下，采用如下方法获取一个Java 堆中的缓冲区：

> ByteBuf heapBuffer = ctx.alloc().heapBuffer();

使用完成后，通过如下的方法，释放缓冲区：

> ReferenceCountUtil.release(heapBuffer );

上面的代码很简单，通过release方法减去 heapBuffer 的使用计数，Netty 会自动回收 heapBuffer 。

缓冲区内存的回收、二次分配等管理工作，是 Netty 自动完成的。



###自动获取和释放 ByteBuf

**方式一：TailHandler 自动释放**

 **Netty自动创建 ByteBuf实例**

 Netty 的 Reactor 线程会在 AbstractNioByteChannel.NioByteUnsafe.read() 处调用 ByteBufAllocator创建ByteBuf实例，将TCP缓冲区的数据读取到 Bytebuf 实例中，并调用 pipeline.fireChannelRead(byteBuf) 进入pipeline 入站处理流水线。

**默认情况下，TailHandler自动释放掉ByteBuf实例**

Netty默认会在ChannelPipline的最后添加的那个 TailHandler 帮你完成 ByteBuf的release。

 先看看，自动创建的ByteBuf实例是如何登场的？

 **Netty自动创建 ByteBuf实例**

 Netty 的 Reactor 线程会在 AbstractNioByteChannel.NioByteUnsafe.read() 处调用 ByteBufAllocator创建ByteBuf实例，将TCP缓冲区的数据读取到 Bytebuf 实例中，并调用 pipeline.fireChannelRead(byteBuf) 进入pipeline 入站处理流水线。

**默认情况下，TailHandler自动释放掉ByteBuf实例**

 Netty的ChannelPipleline的流水线的末端是TailHandler，默认情况下如果每个入站处理器Handler都把消息往下传，TailHandler会释放掉ReferenceCounted类型的消息。
![在这里插入图片描述](https://img-blog.csdnimg.cn/20181118141229704.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NyYXp5bWFrZXJjaXJjbGU=,size_16,color_FFFFFF,t_70)

> 说明：
>
> 上图中，**TailHandler 写成了TailContext**，这个是没有错的。
>
> 对于流水线的头部和尾部Hander来说， Context和Hander ，是同一个类。
>
> HeadContext 与HeadHandler ，也是同一个类。
>
> 关于Context与Handler 的关系，请看 疯狂创客圈 的系列文章。

 **如果没有到达末端呢？**

 一种没有到达入站处理流水线pipeline末端的情况，如下图所示：
![在这里插入图片描述](https://img-blog.csdnimg.cn/20181118141415696.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NyYXp5bWFrZXJjaXJjbGU=,size_16,color_FFFFFF,t_70)

 这种场景下，也有一种自动释放的解决办法，它就是：

 可以继承 SimpleChannelInboundHandler，实现业务Handler。 SimpleChannelInboundHandler 会完成ByteBuf 的自动释放，释放的处理工作，在其入站处理方法 channelRead 中。

### 方式二：SimpleChannelInboundHandler 自动释放



 如果业务Handler需要将 ChannelPipleline的流水线的默认处理流程截断，不进行后边的inbound入站处理操作,这时候末端 TailHandler自动释放缓冲区的工作，自然就失效了。

 这种场景下，业务Handler 有两种选择：

- 手动释放 ByteBuf 实例
- 继承 SimpleChannelInboundHandler，利用它的自动释放功能。

本小节，我们聚焦的是第二种选择：看看 SimpleChannelInboundHandler是如何自动释放的。

利用这种方法，业务处理Handler 必须继承 SimpleChannelInboundHandler基类。并且，业务处理的代码，必须 **移动到** 重写的 channelRead0(ctx, msg)方法中。

如果好奇，想看看 SimpleChannelInboundHandler 是如何释放ByteBuf 的，那就一起来看看Netty源码。

截取的代码如下所示：

```java
public abstract class SimpleChannelInboundHandler<I> extends ChannelInboundHandlerAdapter 
{
//...
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

源码中，执行完重写的channelRead0()后，在 finally 语句块中，ByteBuf 的生命被结束掉了。

> 上面两种，都是**入站处理**（inbound）过程中的自动释放。
>
> **出站处理（outbound）**流程，又是如何自动释放呢？

### 方式三：HeadHandler 自动释放

出站处理流程中，申请分配到的 ByteBuf，通过 HeadHandler 完成自动释放。

出站处理用到的 Bytebuf 缓冲区，一般是要发送的消息，通常由应用所申请。在出站流程开始的时候，通过调用 ctx.writeAndFlush(msg)，Bytebuf 缓冲区开始进入出站处理的 pipeline 流水线 。在每一个出站Handler中的处理完成后，最后消息会来到出站的最后一棒 HeadHandler，再经过一轮复杂的调用，在flush完成后终将被release掉。

![在这里插入图片描述](https://img-blog.csdnimg.cn/20181118141458951.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NyYXp5bWFrZXJjaXJjbGU=,size_16,color_FFFFFF,t_70)

> 强调一下，**HeadContext （HeadHandler）是出站处理流程的最后一棒**。
>
> **出站处理的全过程，请查看疯狂创客圈的专门文章**。

## 如何避免内存泄露

 基本上，在 Netty的开发中，通过 ChannelHandlerContext 或 Channel 获取的缓冲区ByteBuf 默认都是Pooled，所以需要再合适的时机对其进行释放，避免造成内存泄漏。

### 自动释放的注意事项

> 我们已经知道了三种自动释放方法：

- 通过 TailHandler 自动释放入站 ByteBuf

- 继承 SimpleChannelInboundHandler 的完成 入站ByteBuf 自动释放

- 通过HeadHandler自动释放出站 ByteBuf

  **自动释放，注意事项如下**：

- 入站处理流程中，如果对原消息不做处理，默认会调用 ctx.fireChannelRead(msg) 把原消息往下传，由流水线最后一棒 TailHandler 完成自动释放。

- 如果截断了入站处理流水线，则可以继承 SimpleChannelInboundHandler ，完成入站ByteBuf 自动释放。

- 出站处理过程中，申请分配到的 ByteBuf，通过 HeadHandler 完成自动释放。

  出站处理用到的 Bytebuf 缓冲区，一般是要发送的消息，通常由应用所申请。在出站流程开始的时候，通过调用 ctx.writeAndFlush(msg)，Bytebuf 缓冲区开始进入出站处理的 pipeline 流水线 。在每一个出站Handler中的处理完成后，最后消息会来到出站的最后一棒 HeadHandler，再经过一轮复杂的调用，在flush完成后终将被release掉。

### 手动释放的注意事项

 手动释放是自动释放的重要补充和辅助。

 **手动释放操作，大致有如下注意事项**：

- 入站处理中，如果将原消息转化为新的消息并调用 ctx.fireChannelRead(newMsg)往下传，那必须把原消息release掉;
- 入站处理中，如果已经不再调用 ctx.fireChannelRead(msg) 传递任何消息，也没有继承SimpleChannelInboundHandler 完成自动释放，那更要把原消息release掉;
- 多层的异常处理机制，有些异常处理的地方不一定准确知道ByteBuf之前释放了没有，可以在释放前加上引用计数大于0的判断避免异常； 有时候不清楚ByteBuf被引用了多少次，但又必须在此进行彻底的释放，可以循环调用reelase()直到返回true。

> **特别需要强调的，是上边的第一种情况**。
> 如果在入站处理的 handlers 传递过程中，传递了新的ByteBuf 值，老ByteBuf 值需要自己手动释放。老的ByteBuf 值，就是从pipeline流水线入口传递过来的 ByteBuf 实例。



总之，**只要是在传递过程中，没有传递下去的ByteBuf就需要手动释放，避免不必要的内存泄露**。

## 缓冲区 Allocator 分配器

Netty通过 ByteBufAllocator分配缓冲区。

> Netty提供了ByteBufAllocator的两种实现：PoolByteBufAllocator和UnpooledByteBufAllocator。前者将ByteBuf实例放入池中，提高了性能，将内存碎片减少到最小。这个实现采用了一种内存分配的高效策略，称为 **jemalloc**。它已经被好几种现代操作系统所采用。后者则没有把ByteBuf放入池中，每次被调用时，返回一个新的ByteBuf实例。

### 分配器 Allocator的类型

![在这里插入图片描述](https://img-blog.csdnimg.cn/20181118141551763.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NyYXp5bWFrZXJjaXJjbGU=,size_16,color_FFFFFF,t_70)

**PooledByteBufAllocator：可以重复利用之前分配的内存空间。**

为了减少内存的分配回收以及产生的内存碎片，Netty提供了PooledByteBufAllocator 用来分配可回收的ByteBuf，可以把PooledByteBufAllocator 看做一个池子，需要的时候从里面获取ByteBuf，用完了放回去，以此提高性能。

**UnpooledByteBufAllocator：不可重复利用，由JVM GC负责回收**。

顾名思义Unpooled就是不会放到池子里，所以根据该分配器分配的ByteBuf，不需要放回池子，由JVM自己GC回收。

这两个类，都是AbstractByteBufAllocator的子类，AbstractByteBufAllocator实现了一个接口，叫做ByteBufAllocator。

> **可以做一个对比试验**：
>
> 使用UnpooledByteBufAllocator的方式创建ByteBuf的时候，单台24核CPU的服务器，16G内存，刚启动时候，10000个长连接，每秒所有的连接发一条消息，短时间内，可以看到内存占到10G多点，但随着系统的运行，内存不断增长，直到整个系统内存溢出挂掉。
>
> 把UnpooledByteBufAllocator换成PooledByteBufAllocator，通过试验，内存使用量机器能维持在一个连接占用1M左右，内存在10G左右，经常长期的运行测试，发现都能维持在这个数量，系统内存不会崩溃。

### 默认的分配器

 默认的分配器 ByteBufAllocator.DEFAULT ，可以通过 Java 系统参数（SystemProperty ）选项 io.netty.allocator.type 去配置，使用字符串值："unpooled"，"pooled"。

 关于这一段，Netty的源代码截取如下：

```java
      String allocType = SystemPropertyUtil.get("io.netty.allocator.type", "unpooled").toLowerCase(Locale.US).trim();
        Object alloc;
        if("unpooled".equals(allocType)) {
            alloc = UnpooledByteBufAllocator.DEFAULT;
            logger.debug("-Dio.netty.allocator.type: {}", allocType);
        } else if("pooled".equals(allocType)) {
            alloc = PooledByteBufAllocator.DEFAULT;
            logger.debug("-Dio.netty.allocator.type: {}", allocType);
        } else {
            alloc = UnpooledByteBufAllocator.DEFAULT;
            logger.debug("-Dio.netty.allocator.type: unpooled (unknown: {})", allocType);
        }
```

> 不同的Netty版本，源码不一样。

上面的代码，是4.0版本的源码，默认为UnpooledByteBufAllocator。

 而4.1 版本，默认为 PooledByteBufAllocator。因此，4.1版本的代码，是和上面的代码稍微有些不同的。

### 设置通道Channel的分配器

在4.x版本中，UnpooledByteBufAllocator是默认的allocator，尽管其存在某些限制。

现在PooledByteBufAllocator已经广泛使用一段时间，并且我们有了增强的缓冲区泄漏追踪机制，所以是时候让PooledByteBufAllocator成为默认了。

```java
//服务器启动必备ServerBootstrap，客户端启动必备Bootstrap，采用建造者模式
ServerBootstrap b = new ServerBootstrap()
        .group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .localAddress(port)
        .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
        .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(...);
            }
        });
```

使用Netty带来的又一个好处就是内存管理。只需一行简单的配置，就能获得到内存池带来的好处。在底层，Netty实现了一个Java版的Jemalloc内存管理库，为我们做完了所有“脏活累活”！

## 缓冲区内存的类型

 说完了分配器的类型，再来说下缓冲区的类型。

 依据内存的管理方不同，分为堆缓存和直接缓存。也就是Heap ByteBuf 和 Direct ByteBuf。另外，为了方便缓冲区进行组合，提供了一种组合缓存区。





![在这里插入图片描述](https://img-blog.csdnimg.cn/20181118141630347.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L2NyYXp5bWFrZXJjaXJjbGU=,size_16,color_FFFFFF,t_70)
​



 三种缓冲区的介绍如下：

| 使用模式   | 描述                                                         | 优点                                                      | 劣势                                                         |
| ---------- | ------------------------------------------------------------ | --------------------------------------------------------- | ------------------------------------------------------------ |
| 堆缓冲区   | 数据存存储在JVM的堆空间中，又称为支撑数组，通过 hasArray 来判断是不是在堆缓冲区中 | 没使用池化情况下能提供快速的分配和释放                    | 发送之前都会拷贝到直接缓冲区                                 |
| 直接缓冲区 | 存储在物理内存中                                             | 能获取超过jvm堆限制大小的空间； 写入channel比堆缓冲区更快 | 释放和分配空间昂贵(使用系统的方法) ； 操作时需要复制一次到堆上 |
| 复合缓冲   | 单个缓冲区合并多个缓冲区表示                                 | 操作多个更方便                                            | -                                                            |

 上面三种缓冲区的类型，无论哪一种，都可以通过池化、非池化的方式，去获取。

## Unpooled 非池化缓冲区的使用方法

Unpooled也是用来创建缓冲区的工具类，Unpooled 的使用也很容易。

看下面代码：

> //创建复合缓冲区
>
> CompositeByteBuf compBuf = Unpooled.compositeBuffer();
>
> //创建堆缓冲区
> ByteBuf heapBuf = Unpooled.buffer(8);
> //创建直接缓冲区
> ByteBuf directBuf = Unpooled.directBuffer(16);

Unpooled 提供了很多方法，详细方法大致如下：

| 方法名称                                                     | 描述                  |
| ------------------------------------------------------------ | --------------------- |
| buffer() buffer(int initialCapacity) buffer(int initialCapacity, int maxCapacity) | 返回 heap ByteBuf     |
| directBuffer() directBuffer(int initialCapacity) directBuffer(int initialCapacity, intmaxCapacity) | 返回 direct ByteBuf   |
| compositeBuffer()                                            | 返回 CompositeByteBuf |
| copiedBuffer()                                               | 返回 copied ByteBuf   |

> **Unpooled类的应用场景**
>
> Unpooled类让ByteBuf也同样适用于不需要其他的Netty组件的、无网络操作的项目，这些项目可以从这个高性能的、可扩展的buffer API中获益。