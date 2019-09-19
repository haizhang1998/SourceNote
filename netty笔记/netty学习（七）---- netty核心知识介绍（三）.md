### netty学习（七）---- netty核心知识介绍（三）

####Netty的通信传输模式

在选择netty中的Channel模式的时候，netty为我们提供了5中内置的通信传输模式：

![1568709903742](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568709903742.png)

netty在实现NIO的时候要考虑不同的操作系统，这有可能就会将一些特性屏蔽掉了。

Epoll这种传输模式是在linux中的。性能上要比NIO通讯模式要块。

OIO就是我们所说的阻塞式通讯模式。比如原始的Socket和ServerSocket通讯

Local式在我们虚拟机内部的一个通讯模式，在传输数据的时候不再走网卡传输。

Embedded是内嵌的，专门做单元测试的使用，专门测试我们的Handler是否按我们预期作用。



**在netty程序创建的时候，要为服务端/客户端指定通讯模式**

```java
  		EventLoopGroup eventExecutors = new NioEventLoopGroup();
    	//客户端启动必须要 
        Bootstrap bootstrap = new Bootstrap();
        //因为参数较多，bootstrap采用建造者模式构建设置参数
        bootstrap.group(eventExecutors)//必须绑定线程组
                .channel(NioSocketChannel.class)  //绑定NIO操作类
        .remoteAddress(new InetSocketAddress(ip,port))//绑定连接到远程指定的ip和port
        .handler(new NettyClientHandler());
```

上面指定了以NIO通讯模式作为客户端的通讯模式。主要的步骤是创建EventLoopGroup指定其具体的子类：

![1568711059123](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568711059123.png)

并且在bootstrap.channel选择对应模式的SocketChannel，比如NIO模式的就如上面代码指定channel

Epoll模式的话就

```java
bootstrap.channel(EpollSocketChannel.class)
```

> 注意： 不能使channel的通讯模式和线程组的通讯模式不一样，否则运行时会报错！





###Netty写数据到客户端的三种方法

假设我们现在在入站handler中的read方法，读取完数据后将反馈信息传递回客户端

```java
 protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
     // ....  省略数据读取操作
     //写数据出站给客户端的三种方式
        ctx.writeAndFlush("msg");
        ctx.channel().writeAndFlush("channel write msg");
        ctx.pipeline().writeAndFlush("pipeline write msg");
 }
```

1. 通过ChannelHandlerContext上下文的方式写数据并出站

假设现在的channel对应的pipeline的Handler事件处理器如下：

![1568712600556](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568712600556.png)

假设当前所在的入站处理器编号为3，那么此时此刻想要进行出站写操作，**它不会从出站处理器5开始依次的传播处理，而是采用一种就近原则，寻找入站处理器编号的上一个出站处理器，也就是从出站处理器2开始处理出站请求**

![](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568712762421.png)

这种模式效率更加的高，通常的话如果用户请求的数据经过入站处理器3处理的时候发生异常的话，就没有必要再往下继续处理了，我们只需要将异常信息及时反馈给客户端。更不需要从出站处理器5开始处理逐个往前处理。那此时采用这种模式就达到了所需要的效果。

2. 通过Channel写数据出站和通过pipeline写数据出站

而采用Channel和pipeline写出数据的操作，必须要逐个从出站处理器5开始一直走完出战处理器的所有流程。

> 开发中使用ctx.writeAndFlush(msg)的方式最多。



### Netty中启动组件：Bootstrap和ServerBootstrap

Bootstrap作用于客户端的启动，ServerBootstrap用于服务器端的启动。

*** Bootstrap和ServerBootstrap的主要区别**

1. Bootstrap使客户端，要远程连接指定的端口。而ServerBootstrap则使服务器端，它要绑定本低端口，并且bind端口等待客户端的连接。

2. Bootstrap中只能添加一个group，而ServerBootstrap可以添加两个group。如下服务器可以这样操作：

   ```java
   //这个线程组的线程专门接受连接
     EventLoopGroup eventExecutors = new NioEventLoopGroup();
     //这个线程组的线程相当于工作者线程，用来处理连接之后的read，write等业务
     EventLoopGroup worker = new NioEventLoopGroup();
     ServerBootstrap serverBootstrap = new ServerBootstrap();
     serverBootstrap.group(eventExecutors).channel(NioServerSocketChannel.class)
   ```

3. 客户端和服务端指定的channel不一样，比如客户端指定的channel为NioSocketChannel,而服务器端指定的channel为NioServerSocketChannel

**补充**

为什么客户端在Bootstrap绑定handler时直接使用new出handler对象，而服务器端使用C

hannelInitializer并用pipeline进行绑定handler？

```java
//客户端添加handler的一般模式
Bootstrap bootstrap = new Bootstrap();
bootstrap.handler(new NettyClientHandler());

//服务端添加handler的一般模式
ServerBootstrap serverBootstrap = new ServerBootstrap();
serverBootstrap. .childHandler(new ChannelInitializer<SocketChannel>() {
    protected void initChannel(SocketChannel ch) throws Exception {
       //注意这里就是把handler添加到这个channel的pipline,让pipline管理
       //入站出站事件，这里可能多个channel公用一个handler，必须将handler标注为
       //给加入pipline的handler加上名字
       ch.pipeline().addLast("nettyServerHandler",new NettyServerHandler());
       }
});
```

对比上面两种添加handler的形式，客户端直接在调用Bootsteap的hanlder方法实际上只能够依次添加一个Handler

```java
   public B handler(ChannelHandler handler) {
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        this.handler = handler;
        return self();
    }
```

那么服务器端使用ChannelInitializer的目的是：

* 它允许在initChannel方法中一次性为channel添加多个Handler到pipeline
* 在initChannel方法中添加完Handler之后，会将ChannelInitializer从改channel的pipeline中移除出去，因为ChannelInitializer其实是继承了ChannelInboundHandlerAdapter。

* ChannelInitializer只有当服务器收到客户端请求，并将创建的SocketChannel和其中一个EventLoop成功绑定的时候调用。

故此服务器使用ChannelInitializer进一步的降低了编程的复杂性，提高了效率。

**channelOption为handler添加于tcp/ip协议相关的一些参数**

这里截取了一部份ChannelOption中定义的参数

```java
    public static final ChannelOption<Boolean> AUTO_CLOSE = valueOf("AUTO_CLOSE");

    public static final ChannelOption<Boolean> SO_BROADCAST = valueOf("SO_BROADCAST");
//2个小时以内如果没有活跃的报文那就传输一个报文过去探测
    public static final ChannelOption<Boolean> SO_KEEPALIVE = valueOf("SO_KEEPALIVE");
//发送缓冲区的大小
    public static final ChannelOption<Integer> SO_SNDBUF = valueOf("SO_SNDBUF");
    public static final ChannelOption<Integer> SO_RCVBUF = valueOf("SO_RCVBUF");
    public static final ChannelOption<Boolean> SO_REUSEADDR = valueOf("SO_REUSEADDR");
    public static final ChannelOption<Integer> SO_LINGER = valueOf("SO_LINGER");
//是tcp/ip中的一个参数，指定服务器的客户端连接等待队列的大小，服务器同一时刻中能处理一个客户端连接。如果同一时刻无数的客户端请求服务器，那么多出的客户端将会存放进等待队列中，这个参数就控制了等待队列的大小
public static final ChannelOption<Integer> SO_BACKLOG = valueOf("SO_BACKLOG");
    public static final ChannelOption<Integer> SO_TIMEOUT = valueOf("SO_TIMEOUT");

    public static final ChannelOption<Integer> IP_TOS = valueOf("IP_TOS");
    public static final ChannelOption<InetAddress> IP_MULTICAST_ADDR = valueOf("IP_MULTICAST_ADDR");
    public static final ChannelOption<NetworkInterface> IP_MULTICAST_IF = valueOf("IP_MULTICAST_IF");
    public static final ChannelOption<Integer> IP_MULTICAST_TTL = valueOf("IP_MULTICAST_TTL");
    public static final ChannelOption<Boolean> IP_MULTICAST_LOOP_DISABLED = valueOf("IP_MULTICAST_LOOP_DISABLED");
  //没有延迟，我们发送数据包的时候可能很小，也可能很大。把四次发送的数据包累计在一起，然后发送出去，这是Nagle算法，将小的数据包组装在一起发送出去，提高了网络负载，但是会有一定的网络延迟，因为要把报文包粘在一起，如果使用了TCP_NODELAY 就会有多少数据包就发多少，就不进行粘接，响应数据较块。
    public static final ChannelOption<Boolean> TCP_NODELAY = valueOf("TCP_NODELAY");
```

使用方法，只有通道打开之前配置Option参数才有用。否则打开了之后就修改不了了。

```java
 serverBootstrap.childOption(channelOption.TCP_NODELAY,true)
```



**ByteBuf的理解**

之前我们在定义Hanlder类的时候，都会传入一个泛型参数ByteBuf

为什么要传入ByteBuf，而不是String等类型呢？实际上NIO通讯的时候是使用ByteBuffer去接受channel传递的数据的，在netty中认为ByteBuffer太过于繁琐，故此综合了ByteBuffer创造出了代替它的ByteBuf，它只维护了两个指针，读（read）指针和写(write)指针。

![1568770958561](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568770958561.png)



**ByteBuf分配**

通常可以再堆分配ByteBuf也可以再直接缓冲区分配

* 堆

一般而言，操作系统有一个内核空间，应用程序当想读取网卡上的数据，首先操作系统会将网卡的数据读取到操作系统内核，再将数据送往应用程序之中，这相比直接缓冲区多了一步读入内存的操作。

* 直接缓冲区  （不属于JVM管辖，但是可以被回收）

网卡把数据拷贝在直接缓冲区中，用户应用程序直接访问缓冲区的数据。这就加快了访问的速度。但是直接缓冲区的操作属于一种系统的调度，这会比较费时。

**ByteBuf的分配通常由ByteBufAllocator接口进行**

ByteBufAllocator可以通过ChannelHandlerContext对象调用alloc函数进行获取，还可以通过channel.alloc函数调用进行获取。

```java
public void channelRead(ChannelHandlerContext ctx ,Object msg){
   //下面两种方式都可以获取ByteBufAllocator的实例对象
    ctx.alloc();
    ctx.channel().alloc();
    
    //下面时通过ByteBufAllocator实例创建直接缓冲区和堆缓冲区。
      ByteBuf byteBuf = ctx.alloc().directBuffer();
        ctx.alloc().heapBuffer();
        ctx.channel().alloc().heapBuffer();
        ctx.channel().alloc().directBuffer();
}
```

ByteBufAllocator接口有几个实现类

![1568773597375](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568773597375.png)

其中关注Unpooled非池化创建缓冲区，以及Pooled池化创建缓冲区。

```java
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.writeAndFlush(Unpooled.copiedBuffer("i have close!".getBytes())).addListener(ChannelFutureListener.CLOSE);
    }
```

Unpooled.copiedBuffer通常而言时创建非池化的缓冲区，netty4.1版本默认采用池化缓冲区的方式。

池化的含义其实和线程池差不多，在使用完ByteBuf之后，并不会立刻销毁ByteBuf实例，而是在内存中暂存下，准备下次接受客户端的数据。而非池化则不会这样，每一次客户端请求都会new出一个新的ByteBuf实例接受传送数据，而旧的ByteBuf将被回收掉。池化的作用就是提高ByteBuf的利用率，熟读相对来说会提高一些。

**ByteBuf派生缓冲区：**

派生缓冲区的含义是，原来的缓冲区不懂，抽取其中一部分作为子缓冲区，子缓冲区也拥有自己的read和write指针。

copy()f昂发完全拷贝当前缓冲区，slice()切片和duplicate方法都会返回一个BytBuf，但是指向的还是原来的ByteBuf,也就是说返回的

**ByteBuf操作**

在进行读写操作时要注意，**ByteBuf实则上时存储了字节，当访问的时候要注意访问类型，比如：**

```java
ByteBuf b = .....
    b.getInt();
```

实际上ByteBuf内部会去将read指针向后移动4个字节（int类型的字节长度），如果时带指定下标的读操作，比如

```java
b.getInt(2)
```

就会从下标为2的地方往后读4个字节！所以必须要注意读取时候的类型，读long数据的时候往后追加8个字节等等。

**ByteBuf查找操作**

比如我们想要查找一个字符串中指定字符的下标 'a','v''b' ,  比如查找v字符的下标，通常就会用到

```java
public abstract int indexOf(int fromIndex, int toIndex, byte value);
```

这里就会根据你给出的初始坐标和截止坐标以及value的byte值去匹配字符，并返回第一个匹配到的位置。

如果出现更复杂的情况，我们会使用ByteBuf中‘ForEach’开头的方法。

![1568774654070](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568774654070.png)

内部要求自定义ByteProcessor，其实netty已经帮我们定义了一部分，比如查找空格，换行符之类的，但是如果你想要进行自定义化查询，就要自行的实现这个接口，并实现对比方法。

**ByteBuf中的clear操作和ByteBuffer的一样，都是将read，write索引置为0，而数据却没有改变！ByteBuf中还提供了discard方法，可以将已读过的数据清空并将没有读的数据（read指针指向处）拷贝到缓冲数组的首端，这样写指针的范围就会增加。**





### 粘包半包问题

首先给出一份实例代码：

客户端Handler

```java
public class MyNettyClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
    //这个方法会自动的释放msg暂用的缓存
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        System.out.println(msg.toString(CharsetUtil.UTF_8));
    }

    //当和远程建立连接的时候调用
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        //向服务器发送消息
        String msg = "jone ,mark , james, lisi , deer";
        //重复发送100条
        for(int i=0;i<100;i++){
            ByteBuf byteBuf = ctx.alloc().directBuffer();
            byteBuf.writeBytes(msg.getBytes());
            ctx.writeAndFlush(byteBuf);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        //读取完毕后的操作,采用直接内存分配ByteBuf
        System.out.println("completed!");
    }
}
```

客户端:

```java
public class MyNettyClient {

    private int port;
    private String ip;

    public MyNettyClient(int port, String ip) {
        this.port = port;
        this.ip = ip;
    }

    //启动客户端
    public void start(){
        EventLoopGroup clientGroup = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.channel(NioSocketChannel.class)
                .remoteAddress(new InetSocketAddress(ip,port))
                .group(clientGroup)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new MyNettyClientHandler());
                    }
                });
        try {
            //阻塞式等待连接
            ChannelFuture channelFuture = b.connect().sync();
            System.out.println("客户端已经启动");
            //注册监听事件，以后某个时刻可能要关闭
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            clientGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        MyNettyClient myNettyClient = new MyNettyClient(NIOConstant.PORT,NIOConstant.IP);
        myNettyClient.start();
    }
}

```

服务端handler

```java

public class MyNettyServerHandler extends ChannelInboundHandlerAdapter {
    AtomicInteger atomicInteger = new AtomicInteger(0);
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf byteBuf = (ByteBuf) msg;
        System.out.println("服务器端接受到信息:"+byteBuf.toString(CharsetUtil.UTF_8)+"总共:"+atomicInteger.incrementAndGet());
        ReferenceCountUtil.release(msg);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        System.out.println("completed");
        ctx.writeAndFlush(Unpooled.copiedBuffer("i have accept your msg".getBytes())).addListener(ChannelFutureListener.CLOSE);
    }
}
```

服务端

```java
public class MyNettyServer {

    private int port;


    public MyNettyServer(int port) {
        this.port = port;

    }

    public void start(){
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup,workerGroup).channel(NioServerSocketChannel.class)
                .localAddress(port)
                .childOption(ChannelOption.TCP_NODELAY,true)
        .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new MyNettyServerHandler());
            }
        });

        try {
            //阻塞接受连接
            ChannelFuture future = serverBootstrap.bind().sync();
            System.out.println("服务器启动完毕:"+port);
            //等待关闭
            future.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
        MyNettyServer myNettyServer = new MyNettyServer(NIOConstant.PORT);
        myNettyServer.start();

    }
}
```

上面整个程序的含义是，客户端不断的发送100条信息，服务器端每接收一条就记一次数，按理来说应该为100

先启动服务器，再启动客户端，观测结果

![1568794482373](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568794482373.png)

服务器端只有接收到了2条记录。这中间就产生了TCP半包和粘包问题

**问题的描述**

先说TCP：由于TCP协议本身的机制（面向连接的可靠地协议-三次握手机制）客户端与服务器会维持一个连接（Channel），数据在连接不断开的情况下，可以持续不断地将多个数据包发往服务器，但是如果发送的网络数据包太小，那么他本身会启用Nagle算法（可配置是否启用）**对较小的数据包进行合并**（基于此，TCP的网络延迟要UDP的高些）然后再发送（超时或者包大小足够）。**那么这样的话，服务器在接收到消息（数据流）的时候就无法区分哪些数据包是客户端自己分开发送的，这样产生了粘包**；服务器在接收到数据库后，放到缓冲区中，如果消息没有被及时从缓存区取走，下次在取数据的时候可能就会出现一次取出多个数据包的情况，造成粘包现象（确切来讲，对于基于TCP协议的应用，不应用包来描述，而应 用 流来描述），下面是数据包传输过程中可能的情况：



![1568792470520](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568792470520.png)

对上面的图解释：Client端想要向Server端发送2个数据包D2和D1，有四种结果：

* 第一种：2个数据包没有发生半包粘包的现象，成功的被服务器接收一整个包
* 第二种：2个数据包粘合在了一起(有可能是发送的时候，数据包大小过小，tcp采用Negle算法进行合并)，那么服务端接收到的就是D1,D2粘合在一起的数据包，读出的是粘包的信息。
* 第三种：D1数据包整体和D2数据包的一部分粘结在一起发送，D2的另一部分又单独的发送，那么服务器先后接收到粘包和半包的数据。
* 第四种：D1的一部分先发送，而后一部分和D2数据包整体面和，也是和第三种情况一样。

因为服务端操作系统的接收缓冲区大小有限制，那么可能就会出现一次性来不急读取完毕缓冲区的现象，那么假设操作系统只读取了缓冲区中的一半的数据，那么此时客户端又发来另一部分数据，就会放在那一般缓冲区中。操作系统再次读的时候，半包粘包问题就发生了。

那么还有一种情况就是网络协议各层所能够接收的数据量不一致导致的，比如客户端传来1500字节大小的数据，而ip层最多只能接收1024字节大小的数据，那么此时ip层就会对传输过来的数据包分片，那么往下传递给数据链路层的时候，数据链路层只能接收512字节大小的数据，那这个过程中，数据链路层又会进行对数据包分片，于是就可能导致半包粘包问题的出现了。

总之就是一个数据包被分成了多次接收是导致半包问题的主要原因。

> ip分片，tcp分段可能还会发生在中间路由器转发的过程中。解决粘包半包问题不能依靠tcpip协议做调整去解决。必须要在应用程序发送数据包和接收数据包的过程中加以控制。



**解决半包和粘包问题的办法：**

1） **加分隔符 (比较适合文本)**

* 如系统回车换行符（windows/linux/mac都式不一样的，用jdk提供的的System.getProperty（"line.sparator"）获取）

```java
//在服务器端channel中添加 LineBasedFrameDecoder，1024是最大的字符数，超过了会产生异常
   ch.pipeline().addLast(new LineBasedFrameDecoder(1024));
//标注完之后，我们要在待发送给服务器端的数据后加上System.getProperty("line.separator");来获取系统换行符
        String msg = "jone ,mark , james, lisideer"+System.getProperty("line.separator");
```

同理，如果客户端想要接收服务端传来的数据包并保证不发生粘包和半包问题，也同上处理服务端的数据，并在客户端的channel加上LineBasedFrameDecoder并指定最大接收的字符量。

* 自定义的分隔符 。

```java
 //1. 在服务器端和客户端自定义分隔符解决半包粘包问题,可以是任意组合的字符
    public static final String CUSTOM_DELIMETER = "@~" ;
 //2. 将自定义字符注册在channel中
  //注意这里的delimiter要进行ByteBuf的封装
    ByteBuf byteBuf = ch.alloc().directBuffer(CUSTOM_DELIMETER.length());
    byteBuf.writeBytes(CUSTOM_DELIMETER.getBytes());
    ch.pipeline().addLast(new DelimiterBasedFrameDecoder(1024,byteBuf));

 //3.当有数据发送给对方的时候，在消息最后加上自定以的分隔符!否则对方不认识，接收不到消息！
    ctx.writeAndFlush(Unpooled.copiedBuffer(("i have accept your"+CUSTOM_DELIMETER).getBytes()));

```



2） 消息定长，规定每个报文长度就是200个字节，不够就补空格，比较受限制，但是某些应用场景，比如Rsa，MD5的数据传输，就是定长的。

```java
//向chnnal中加入固定的字符长度FixedLengthFrameDecoder，当你传输的内容如果大于你设置的长度，还是会出现半包的问题！
  ch.pipeline().addLast(new FixedLengthFrameDecoder("i have accept your msg".getBytes().length));
```



3） 在消息中带上长度字段 ，比如我这次发数据报文，带上长度150字节，那么服务器那一头就要等待这次的153个字节全部接收完成，才可以继续往下运行





### 编解码器

　　当你通过Netty发送或者接受一个消息的时候，就将会发生一次数据转换。入站消息会被**解码**：从字节转换为另一种格式（比如java对象）；如果是出站消息，它会被**编码**成字节。

　　Netty提供了一系列实用的编码解码器，他们都实现了ChannelInboundHadnler或者ChannelOutcoundHandler接口。在这些类中，channelRead方法已经被重写了。以入站为例，对于每个从入站Channel读取的消息，这个方法会被调用。随后，它将调用由已知解码器所提供的decode()方法进行解码，并将已经解码的字节转发给ChannelPipeline中的下一个ChannelInboundHandler。

### 解码器

#### 抽象基类ByteToMessageDecoder

![img](https://img2018.cnblogs.com/blog/1500605/201810/1500605-20181027214943982-609065143.png)

由于你不可能知道远程节点是否会一次性发送一个完整的信息，tcp有可能出现粘包拆包的问题，这个类会对入站数据进行缓冲，直到它准备好被处理。

```java
public abstract class ByteToMessageDecoder extends ChannelInboundHandlerAdapter {

    protected abstract void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception;

    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.isReadable()) {
            // Only call decode() if there is something left in the buffer to decode.
            // See https://github.com/netty/netty/issues/4386
            decodeRemovalReentryProtection(ctx, in, out);
        }
    }
}
```

**decode方法：**

　　必须实现的方法，ByteBuf包含了传入数据，List用来添加解码后的消息。对这个方法的调用将会重复进行，直到确定没有新的元素被添加到该List，或者该ByteBuf中没有更多可读取的字节时为止。然后如果该List不会空，那么它的内容将会被传递给ChannelPipeline中的下一个ChannelInboundHandler。

**decodeLast方法：**

　　当Channel的状态变成非活动时，这个方法将会被调用一次。

![1568809747249](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568809747249.png)



如上图中的ToIntegerDecoder对入站的ByteBuf进行解码操作具体逻辑如下：

```java
public class ToIntegerDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() >= 4) {
            out.add(in.readInt());
        }
    }
}
```

这个例子，每次入站从ByteBuf中读取4字节，将其解码为一个int，然后将它添加到下一个List中。当没有更多元素可以被添加到该List中时，它的内容将会被发送给下一个ChannelInboundHandler。int在被添加到List中时，会被自动装箱为Integer。在调用readInt()方法前必须验证所输入的ByteBuf是否具有足够的数据。

一个实用的例子

 ```java
public class MyDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 4) {
            return;
        }
　　　　　//在读取前标记readerIndex
        in.markReaderIndex();
　　　　　//读取头部
        int length = in.readInt();
        if (in.readableBytes() < length) {
　　　　　//消息不完整，无法处理，将readerIndex复位
            in.resetReaderIndex();
            return;
        }
        out.add(in.readBytes(length).toString(CharsetUtil.UTF_8));
    }
}
 ```

#### 抽象类ReplayingDecoder

```
public abstract class ReplayingDecoder<S> extends ByteToMessageDecoder
```

　ReplayingDecoder扩展了ByteToMessageDecoder类，使用这个类，我们不必调用readableBytes()方法。参数S指定了用户状态管理的类型，其中Void代表不需要状态管理。

以上代码可以简化为：

```java
public class MySimpleDecoder extends ReplayingDecoder<Void> {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        //传入的ByteBuf是ReplayingDecoderByteBuf
        //首先从入站ByteBuf中读取头部，得到消息体长度length，然后读取length个字节， 
        //并添加到解码消息的List中
        out.add(in.readBytes(in.readInt()).toString(CharsetUtil.UTF_8));
    }
```

如何实现的？

ReplayingDecoder在调用decode方法时，传入的是一个自定义的ByteBuf实现：

```java
final class ReplayingDecoderByteBuf extends ByteBuf 
```

eplayingDecoderByteBuf在读取数据前，会先检查是否有足够的字节可用，以readInt()为例：

```java
final class ReplayingDecoderByteBuf extends ByteBuf {

    private static final Signal REPLAY = ReplayingDecoder.REPLAY;

    ......    

     @Override
    public int readInt() {
        checkReadableBytes(4);
        return buffer.readInt();
    }

    private void checkReadableBytes(int readableBytes) {
        if (buffer.readableBytes() < readableBytes) {
            throw REPLAY;
        }
    }  

    ......

}
```

如果字节数量不够，会抛出一个Error（实际是一个Signal public final class Signal extendsError implements Constant<Signal> ），然后会在上层被捕获并处理，它会把ByteBuf中的ReadIndex恢复到读之前的位置，以供下次读取。当有更多数据可供读取时，该decode()方法将会被再次调用。最终结果和之前一样，从ByteBuf中提取的String将会被添加到List中。



下一张我们主要进行netty搭建http服务器的实战