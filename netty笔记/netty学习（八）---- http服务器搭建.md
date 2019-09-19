###netty学习（八）---- http服务器搭建

在搭建之前我们先明确Http请求需要了解的知识点

## Http的Get，POST

Get请求包括两个部分：

- request line(包括method，request uri，protocol version))
- header

基本样式:

```java
GET /?name=XXG&age=23 HTTP/1.1       -----> request line
------------------------------------------------------------------
Host: 127.0.0.1:8007
Connection: keep-alive              
Cache-Control: max-age=0             -----> header
Upgrade-Insecure-Requests: 1
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36
Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8
Accept-Encoding: gzip, deflate, br
Accept-Language: zh-CN,zh;q=0.9
```

POST请求包括三个部分

- request line(包括method，request uri，protocol version))
- header
- message body

```java
GET / HTTP/1.1                       -----> request line
------------------------------------------------------------------
Host: 127.0.0.1:8007
Connection: keep-alive  
Content-Length: 15            
Cache-Control: max-age=0             -----> header
Upgrade-Insecure-Requests: 1
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36
Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8
Accept-Encoding: gzip, deflate, br
Accept-Language: zh-CN,zh;q=0.9
------------------------------------------------------------------
name=XXG&age=23                     ------>message body
```

上一篇提及过的编码解码器，其实在实现一个自己的http服务器的时候，也必须要用到编解码器来实现，对于客户端传递过来的HttpRequest请求，我们需要用到HttpRequestDecoder对其进行解码，而服务器发送给客户端的HttpResponse需要使用HttpResponseDecoder进行编码。与此同时，如果对方发送的是Post请求，就要使用HttpObjectAggregator去处理

## HttpObjectAggregator

http请求在netty中会分为如下图：

![1568864143568](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568864143568.png)

其中一个完整的HttpRequest( FullHttpRequest) ,会分为Http头，n个http请求体，以及一个标志Http请求结束的标记。如果你不想要自行的去合并处理这些分开的传送的

从上可以看出，当我们用POST方式请求服务器的时候，对应的参数信息是保存在`message body`中的,如果只是单纯的用`HttpServerCodec`是无法完全的解析Http POST请求的，因为`HttpServerCodec`只能获取uri中参数，所以需要加上`HttpObjectAggregator`



了解了上面的Http服务器所需的编解码器之后，就可以使用netty搭建了！

#### 利用netty手写Http服务器

首先给出整个服务器的包图

![1568874592713](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568874592713.png)

1. **首先老套路，先搭建ServerBootstrap，绑定好端口等待客户端连接：**(HttpServer类)

```java
public class HttpServer {

    public void startServer(){
        //用于专门处理连接请求的反应器
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        //专门进行read，write等逻辑业务处理的反应器
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        //服务器启动必备
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        //使用nio进行和客户端通讯
        serverBootstrap.channel(NioServerSocketChannel.class)
            //绑定反应器
                .group(bossGroup,workerGroup)
            //绑定本低地址和端口
                .localAddress(HttpConstants.PORT)
            //当客户端连接到服务器时，为每个channel进行绑定一些编解码器来处理请求和响应的数据
                .childHandler(new HttpServerInitilizer());
        try {
            //绑定端口准备连接
            ChannelFuture f = serverBootstrap.bind().sync();
            //
            System.out.println("服务器已经启动成功,端口"+HttpConstants.PORT);
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            //服务器关闭的时候要关闭连接池
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
      HttpServer server = new HttpServer();
      server.startServer();
    }
}



//定义一些常量
public class HttpConstants {
    public final static String HTTP_IP = "127.0.0.1";
    public final static int PORT = 8989;
    public static String httpResponseMsg = "hello ! httpserver tell you now is"+ new Date();
    
}
```

上面的类时整个HttpSever的一个启动类，有了启动类，在接收到连接的时候，需要创建handler进行绑定监听器

2. **为每个连接到服务器的channel添加编解码器**（ HttpServerInitilizer）

```JAVA
public class HttpServerInitilizer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        //服务器向客户端发送响应报文要编码
        pipeline.addLast("encode",new HttpResponseEncoder());
        //服务器接收来自客户端的请求要解码
        pipeline.addLast("decode",new HttpRequestDecoder());
        //对http内容进行聚合成一个FullHttpRequest/FullHttpResponse
        pipeline.addLast("aggregate",new HttpObjectAggregator(1024*10*1024));
        //处理接收客户端的数据
        pipeline.addLast("businiss",new BusinessHandler());
    }
}
```

上面的类在服务器接收到连接的时候自动调用initChannel方法注册编解码器，还有其他的handler。当这个方法调用结束，这个Initilizer将自动的从channel的pipeline删除

3. **处理接收客户端数据和响应客户端数据的Handler**

```java
public class BusinessHandler extends ChannelInboundHandlerAdapter {
    String result;
    //向客户端发送数据的方法
    public void sendMsg(String responseMsg,ChannelHandlerContext ctx,HttpResponseStatus status){
        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,status,
                Unpooled.copiedBuffer(responseMsg,CharsetUtil.UTF_8));
        fullHttpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE,"text/plain;charset=utf-8");
        ctx.writeAndFlush(fullHttpResponse).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

          //注意这里接收到的msg是FullyHttpRequest！
          FullHttpRequest fullHttpRequest = (FullHttpRequest) msg;

        try {
            //检测发送过来的request的uri
          String uri = fullHttpRequest.uri();
          String content = fullHttpRequest.content().toString(CharsetUtil.UTF_8);
          HttpMethod httpMethod = fullHttpRequest.method();
          if (!uri.equalsIgnoreCase("/test")) {
              System.out.println("服务器接收到非法请求:"+uri+";"+httpMethod.name());
              //如果不是/test路径就告诉客户端这是个非法请求
              result = "非法请求！";
              //4XX状态码表示错误
              sendMsg(result, ctx, HttpResponseStatus.BAD_REQUEST);
          } else if (httpMethod.equals(HttpMethod.GET)) {
              System.out.println("服务端接收到客户端的get请求:" + content);
              sendMsg(HttpConstants.httpResponseMsg, ctx, HttpResponseStatus.OK);
          } else if (httpMethod.equals(HttpMethod.POST)) {
              System.out.println("服务端接收到客户端的post请求:" + content);
              sendMsg(HttpConstants.httpResponseMsg, ctx, HttpResponseStatus.OK);
          }
      }finally {
          //释放请求,实际上请求体content是ByteBuf类型，故此释放ByteBuf的资源
            fullHttpRequest.release();
      }
    }
}
```

这个类主要用来处理客户端传来的数据和响应客户端。其中channelRead方法接收到的进站消息的msg参数就是客户端的HttpRequest请求，由于我们添加了HttpObjectAggregator将多个Http请求段整合成一个完整的Http请求（FullHttpRequest）。故此我们要把msg强转成FullHttpRequest的对象。然后我们就可以进行获取请求的uri，content请求体，进行一些逻辑判断。那么当请求的uri不是/test的时候，就会响应非法请求（sendMsg方法发送Http响应）。还有要注意HttpResponseStatus响应状态的设置。在每次读取客户端数据完毕后，一定要释放HttpReqeust中的ByteBuf缓冲区，`  fullHttpRequest.release();`



以上就完成了http简易服务器的构造，测试下：

客户端：

![1568875511437](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568875511437.png)

![1568875602493](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568875602493.png)

服务器端的结果：

![1568875529856](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568875529856.png)

成功的拦截了非法路径的访问，并将响应信息返还给客户端。我们在浏览器按下F12进入开发者模式查看具体的请求信息：

![1568875669266](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568875669266.png)

其中：

* 1处代表请求的结果，正好时我们设置的状态码BadRequest。
* 2处是我们设置的响应体格式，文本型并且字符集是utf-8，注意字符集不要写错，否则出现乱码！
* **3处表示客户端可以接收的压缩数据类型，也就是可以将我们的请求体进行压缩，下面将进行配置压缩格式。**



如何将服务端的请求内容进行压缩呢？其实很简单，只需要添加netty提供的HttpContentCompressor这个事件监听器到我们的pipeline中即可，这样当发现请求头有Accpt-Encoding字段的时候，就可以对响应体进行压缩传输给客户端。

```java
protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("compress",new HttpContentCompressor());
        .....
    }
```

> 这里顺序要注意，Http的编解码器以及HttpContentCompressor这些功能需要在处理客户端消息的Handler添加之前放入管道（假设你按默认的顺序调用Handler的话）否则将呈现不出效果。

再进行测试下，观测客户端的结果:

![1568876412003](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568876412003.png)

这里就代表了对数据进行了压缩。



###手写Http客户端

有了服务端之后，客户端也同样的道理，也可以用netty去构建

分为以下步骤：

1. 创建BootStrap客户端启动类，并设置远程ip和host，设置http请求以及为channel添加对应的编解码器和事件处理器

```java
public class MyHttpClient {

    public void start()
    {
        EventLoopGroup eventExecutors = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventExecutors)
                .channel(NioSocketChannel.class)
                .remoteAddress(HttpConstants.HTTP_IP,HttpConstants.PORT)
                .option(ChannelOption.SO_KEEPALIVE,true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        //相当于添加了客户端channel的编码器和解码器 , HttpServerCode是为服务端添加的
                        pipeline.addLast(new HttpClientCodec());
                        //添加aggregator将响应体聚合，成为一个完整的http请求
                        pipeline.addLast(new HttpObjectAggregator(10*1024*1024));
                        //添加事件处理器
                        pipeline.addLast(new MyHttpCLientHandler());
                    }
                });
        try {
            ChannelFuture future = bootstrap.connect().sync();
            sendMsgToServer(future,"hello netty!");
            //这里将向服务端发送请求
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } finally {
            eventExecutors.shutdownGracefully();
        }
    }

   //专门用来传输数据给服务器
public void sendMsgToServer(ChannelFuture channelFuture ,String msg) throws URISyntaxException {
        URI uri = new URI("/test");
        FullHttpRequest request = null;
        try {
            request = new DefaultFullHttpRequest(
                    HttpVersion.HTTP_1_1,
                    HttpMethod.GET,
                    uri.toASCIIString(),
                    Unpooled.wrappedBuffer(msg.getBytes("UTF-8"))
            );
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        request.headers().set(HttpHeaderNames.HOST,"localhost:"+HttpConstants.PORT);
        request.headers().set(HttpHeaderNames.ACCEPT,"text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
     //这个必须要告诉服务器端有多少字节可读！否则服务器收不到content！
          request.headers().set(HttpHeaderNames.CONTENT_LENGTH,request.content().readableBytes());
    //与服务器建立长连接
       request.headers().set(HttpHeaderNames.CONNECTION,HttpHeaderValues.KEEP_ALIVE);

        channelFuture.channel().write(request);
       channelFuture.channel().flush();
    }

    public static void main(String[] args) {
        MyHttpClient httpClient = new MyHttpClient();
        httpClient.start();
    }
}

```

上面类中定义了两个方法，一个start就是启动客户端的方法，只要连接上服务器，那就发送请求到服务器中，而消息发送就通过channel进行发送。

其中sendMsgToserver方法中定义了一个完整的请求，并定义了请求头部，请求体内容，请求的类型以及Host

等信息。

2. 编写接收数据的事件处理器

```java
public class MyHttpCLientHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //因为是客户端的，所以接收到的是服务器返回的response
        FullHttpResponse httpResponse = (FullHttpResponse)msg;
        String content =  httpResponse.content().toString(CharsetUtil.UTF_8);
        HttpHeaders headers = httpResponse.headers();
        HttpResponseStatus status = httpResponse.status();
        System.out.println("客户端接收到响应状态:"+headers.toString());
        System.out.println("客户端接收到响应体内容:"+content);
    }
}

```

这个类很简单，就是打印收到的服务器反馈的头部和响应体内容。

3. 测试

服务器端结果：

![1568879209627](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568879209627.png)

客户端结果：

![1568879219963](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568879219963.png)



> 平常时使用apache提供的HttpClient就可以了

### 

### 为Netty搭建的服务器进行SSL保护

其实SSL保护Netty服务器说多了也就是往Netty服务器对应的channel中的pipeline添加进一个SSL事件监听器

首先我们要根据服务器是否开启ssl保护来新建处一个SsLContext（ssl上下文）,注意使用的时netty提供的SsLContext

```java
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import javax.net.ssl.SSLException;
import java.security.cert.CertificateException;

public class HttpServer {

    //标记是否为服务器开启SSL保护
    private static boolean ssl = true;

    public void startServer(){
        SslContext sslContext = null;
        if(ssl){
            //netty为我们提供的ssl加密，缺省
            try {
                SelfSignedCertificate ssc = new SelfSignedCertificate();
                sslContext = SslContextBuilder.forServer(ssc.certificate(),ssc.privateKey()).build();
            } catch (CertificateException | SSLException e) {
                e.printStackTrace();
            }
        }else{
              sslContext = null;
        }

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.channel(NioServerSocketChannel.class)
                .group(bossGroup,workerGroup)
                .localAddress(HttpConstants.PORT)
                .childHandler(new HttpServerInitilizer(sslContext));
        try {
            ChannelFuture f = serverBootstrap.bind().sync();
            System.out.println("服务器已经启动成功,端口"+HttpConstants.PORT);
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            //服务器关闭的时候要关闭连接池
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) {
      HttpServer server = new HttpServer();
      server.startServer();
    }
}

```

HttpServerInitilizer

```java

public class HttpServerInitilizer extends ChannelInitializer<SocketChannel> {
    private  SslContext sslContext;
    public HttpServerInitilizer( SslContext sslContext){
        this.sslContext = sslContext;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        //ssl功能必须要添加在pipeline的第一位
        if(sslContext!=null) {
            pipeline.addLast(sslContext.newHandler(ch.alloc()));
        }
        //服务器向客户端发送响应报文要编码
        pipeline.addLast("encode",new HttpResponseEncoder());
        //服务器接收来自客户端的请求要解码
        pipeline.addLast("decode",new HttpRequestDecoder());
        //对http内容进行聚合成一个FullHttpRequest/FullHttpResponse
        pipeline.addLast("aggregate",new HttpObjectAggregator(1024*10*1024));
        pipeline.addLast("compress",new HttpContentCompressor());
        pipeline.addLast("businiss",new BusinessHandler());
    }
}

```

只需添加到pipeline中的第一位就可以完成ssl的保护，那么客户端在进行localhost:8080/test请求的时候将会收到无效连接，只有在连接前加上**https:** 才可以进行安全访问！

![1568881453388](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568881453388.png)

正确的访问：

![1568881475117](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568881475117.png)

只需要将这个连接添加信任就可以消除不安全的红字；

> https在数据传输的过程中，别人看到的时密文，而直接使用http的话就是明文，很容易被攻击拦截！这就是https的好处

