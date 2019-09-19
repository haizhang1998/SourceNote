### netty学习（二）----- AIO与BIO编程（二）

### BIO

bio意思是阻塞式的io模型，常常用于客户端交互程序中。最长见的就是**ServerSocket和Socket之间的交互**。

**Socket和ServerSocket简介**

Socket又叫套接字，ServerSocket则可以接受客户端请求，并生成一个新的线程处理对应的客户端传递信息。

ServerSocket 为当前的机器打开一个端口接受客户端的请求

```java
ServerSocket server = new ServerSocket( port )
```

Sokcet 负责连接操作。它要绑定指定ip和指定的端口,用于和服务端建立tcp连接。

```java
Socket socket = new Socket(ip,port);
```

那么完成上面步，先启动服务器（ServerSocket），再启动客户端（Socket）就可以完成双向通信了，通信会用到Socket的输入输出流，传递一些文件，消息之类的。这里就不再讲述。



为什么说ServerSocket 和 Socket通信采用的式BIO模型呢？如下图：

![1568167702584](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568167702584.png)

实际上而言，每个客户端想要连接服务器，就要在客户端处new处Socket对象，绑定ip和port。那么服务端想要接受这些请求信息，就要有一个类似适配器的类（ServerSocket），将所有的请求收集(accept方法)，并为每个请求去new一个新的线程去处理和客户端的交互，就像上图中的左侧图。

那么这个就是典型的BIO通信模型，服务器再没有收到客户端的消息式会阻塞的等待，客户端也是一样。并且BIO通信模型有一个缺点，就是在服务端他会为每一个客户端的请求新建一个线程处理，那么当很多请求去访问同一个server时，服务器的线程数量将会急速上升。每new一个线程就会占用一些内存资源。很容易的就能够造成内存不足而宕机！

这种模式在实际上是不可取的，一般使用一种**伪异步IO模型**去代替这种BIO处理方式。

**伪异步IO模型**

它在BIO基础上，利用了一个线程池处理Server收到的请求，使用**线程池中的newFixedThreadPool() ** 去创建一个持有固定数量的线程的线程池，那么Server将收到的请求都会使用一个Runnable去将请求视为一个任务，并将其抛进线程池中。因为使用了固定数量的线程池，而控制住了多请求引发的线程数飙升的问题，而且线程池可以同时应对多个请求。但是还是要注意，因为newFixedThreadPool()中采用的是一种叫**无边队列的方式处理等待执行的线程任务，一旦任务越来越多，也可能会出现BIO的问题**。

伪异步IO模型的简单模板：

```java
//服务器端
public class Server {
    private ServerSocket serverSocket;
    //存放请求任务的线程池
    private ExecutorService executorService = Executors.newFixedThreadPool(10);
    public BIOServer(){
        try {
            serverSocket = new ServerSocket(BIOConstant.DEFAULT_PORT);
            System.out.println("服务端启动端口:"+serverSocket.getLocalPort());
            while(true){
                Socket client = serverSocket.accept();
                System.out.println("收到一个客户连接");
                //将请求抛入Runnable视为一个任务交给线程池的线程处理
                executorService.execute(new Runnable(....));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


//客户端
public class BIOClient {

   private  Socket socket;


    public BIOClient() {
            //sokcet初始化用来连接服务端serverSocket
            this.socket = new Socket(BIOConstant.DEFAULT_IP,BIOConstant.DEFAULT_PORT) ;
            //一些与服务端互动的操作
             sendMsg(socket);
    }
}
```

伪异步IO模型相对于BIO模型总体的性能提升不少，但是还是不够灵活，有所欠缺，这时候就要介绍下面的主题AIO了，它比伪异步IO更加灵活，但是使用难度也会提升不少。



**AIO**

jdk提供的基本数据结构：

**AsynchronousServerSokcetChannel  服务器端常用(可以看作ServerSocket)**

**AsynchronousSocketChannel  客户端常用(可以看作Socket)**

基本的概念：

![1568169025965](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568169025965.png)

异步IO采用的是一种订阅-通知的模式，这种模式不会阻塞进程的执行，也就是说，操作系统在读取客户端数据再写到用户空间这一过程是不会让进程傻傻的等候的，换句话讲就是客户端在操作系统准备数据的期间，是可以继续自己的业务。

![1568188648272](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568188648272.png)

而当数据准备完成之后，操作系统就会像JVM发送一个信号，然后JVM调用JDK中aio框架的指定函数，回调进程的**指定函数** 来将数据传递给应用程序。那么这个指定函数就是接口**CompletionHandler**

```java
/**
 * 一个统计异步IO执行结果的接口.
 * 当异步IO操作成功的时候，将会调用这个接口的completed方法，其中result存放的是IO的接口，而Attachment可以理解为附件（可能会调用多次，它会作为参数传递给下一次调用completed方法）
   同样的当异步io失败，将回调failed
 * @param   <V>     The result type of the I/O operation
 * @param   <A>     The type of the object attached to the I/O operation
 * @since 1.7
 */
public interface CompletionHandler<V,A> {

    /**
     * Invoked when an operation has completed.
     * @param   result
     *          The result of the I/O operation.
     * @param   attachment
     *          The object attached to the I/O operation when it was initiated.
     */
    void completed(V result, A attachment);

    /**
     * Invoked when an operation fails.
     */
    void failed(Throwable exc, A attachment);
}

```

这个接口主要是用于异步IO执行完毕，回调使用的。我们要声明一个类去实现这个接口，这样无论当系统准备数据是否成功，都会通过这个接口的两个方法进行回调，通知进程去处理。

有了回调接口，我们首先梳理下整体的AIO通信的流程：

客户端：

1. 使用AsynchronousSocketChannel对象的open（）方法创建出一个Channel（用于传输数据）。
2. 在返回的Channel对象中bind一下服务端的ip地址和port端口。并connect服务器即可。
3. 在绑定成功后就可以使用channel进行read和write，读取服务端传递的数据和写数据给服务端了。
4. 无论是connect，read，write方法，在AIO模型下都是异步的，都必须要提供一个实现了Completion接口的类，以便操作系统在准备好数据后通知回调对应的方法，再进行相应的处理。
5. 当操作系统准备数据失败的时候，要在Completion中的failed方法内将对应的channel进行关闭。

服务器端：

1. 使用AsynchronousServerSokcetChannel 对象的open（）方法创建出一个Channel
2. channel绑定好port并调用accept方法，准备接受客户端连接
3. 在读取客户端连接的时候，要对传进来的bufferWriter进行判断是否有剩余数据每录入进来。如果存在的话，操作系统会不停的回调Completion的completed方法！
4. 读取成功后，如果想要继续返回信息给客户端，可以调用channel的write方法，并传入实现了Completion接口的类去专门处理write操作是否成功到达客户端。
5. 如果write操作完毕后，可以选择继续等待客户端的数据，那就重新的accept重复上面的操作。
6. 无论是读还是写操作，jvm只要回调了Completion的failed方法都要讲channel进行关闭（在failed内部）。

上面贴出了大体的AIO交互流程，在开始实例之前，我们还要了解下ByteBuffer。

**ByteBuffer**

![1568172655839](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568172655839.png)

你可以讲ByteBuffer理解成一个存放在内存中的数组。AIO模式下客户端或者服务端读写操作，都是建立在ByteBuffer的基础上，上面给出了Buffer的两种模式：

**Buffer - Write Mode**

这是Buffer的写模式，position代表当前已经写到了何处，而capacity代表最大的容量，limit则表示一个调整最大可写的范围（相当于一个可以上下滑动的阀）。那么可以写数据进ByteBuffer的最大区域就是在[position,limit]之间，超出limit的地方就算有空间也不可以写！

**Buffer -Read Mode**

三个标志和上面WriteMode的解释是差不多的。position代表当前已读到的数据，而【position，limit】之间，则表示未读取到的数据，再操作系统底层如果调用一次read方法后，回头检测还有【position，limit】这么多的数据没有读取，就会再次的进行回调Completion接口的completed方法讲数据传入到应用程序中。

在每次读操作之前，需要讲Buffer的模式进行转换为读模式，在写数据进Buffer后，需要对模式进行转换为写模式；

```java
ByteBuffer byteBuffer =ByteBuffer.allocate(1024);
//flip()进行读写模式转换
byteBuffer.flip();
```

在读取buffer中的数据时，为了防止有数据没读完，要进行检查

```java
if(  byteBuffer.remaining()>0){
    //  sth 
}
```



**AIO客户端服务端交互实战演练**

前言：

我们大致的讲过BIO的客户端和服务端的交互，时通过Socket和ServerSocket实现的，实质上要使用AIO进行交互，难度要大于Socket的BIO模式。不过必须要理解。AIO时时刻刻都在以异步的模式去通信交互的。也就是说

当客户端或服务端完成了读写操作以及连接操作，不用理会是否操作系统底层还在处理这次操作的数据，我们可以继续关注剩下的业务，而当系统层有消息了就会去通知JVM ，JVM再根据操作系统准备数据的结果回调Completion的completed或者failed方法。**读、写、连接**三个操作都是异步执行的，如果你想接收到反馈接口，就要为他们都定义一个专门处理这三个操作的回调接口，也就是为他们三个分别定义一个类，实现Completion接口。

案例：

现在有一个业务场景，要求再AIO模式下（异步模式），服务端主动的打开端口等待客户端的连接，客户端发送一段字符串到服务端，服务端返回给客户端字符串的总长度，客户端去读取字符串，可以选择是否继续发送。直到客户端发送字符串“q”，才终止两方的连接。否则可以实现长连接，持续交互。

**代码的架构**

![1568253742499](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568253742499.png)

**客户端实现**

首先想要进行连接服务器，必须要有一个类进行连接，AsynchronousSocketChannel就是用于打开channel，然后通过channel指定的ip和port进行连接。这个类实现了CompletionHandler<Void,AIOClientHandler>，因为要返回是否连接成功，这个状态需要回调CompletionHandler的接口，其中Void代表处理的结果，因为单纯的连接不管成不成功都不会产生新的对象，故此将其设置为Void。而AIOClientHandler就是代表附件的意思，将当前对象看作成附件，方便外部进行调用。

```java
//用来处理连接
public class AIOClientHandler implements Runnable, CompletionHandler<Void,AIOClientHandler> {
    //定义异步socket
    private AsynchronousSocketChannel channel;
   public CountDownLatch countDownLatch;
    //端口号和ip地址
    private String ip;
    private Integer port;

    public AIOClientHandler(String ip,Integer port){
        try {
            //打开channel，准备传输数据
            channel = AsynchronousSocketChannel.open();
            this.port = port;
            this.ip = ip;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        countDownLatch = new CountDownLatch(1);
        try {
            //尝试的去连接,需要传入ip和port，附件Attachment，连接成功后的回调函数
            channel.connect(new InetSocketAddress(this.ip, this.port), this, this);
            //这里必须使用countDownLatch阻塞线程，不然会出现继续执行并且退出channel的情况。
            countDownLatch.await();
            System.out.println("关闭了客户端的channel");
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(channel.isOpen()){
                try {
                    channel.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    @Override
    public void completed(Void result, AIOClientHandler attachment) {
        System.out.println("客户端成功连接服务器。。。");
    }

    @Override
    public void failed(Throwable exc, AIOClientHandler attachment) {
        System.out.println("连接失败，准备关闭channel");
        if(channel.isOpen()){
            try {
                channel.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        attachment.countDownLatch.countDown();
    }


    //发送信息给服务端
    public void sendMsg(String msg){
        //定义ByteBuffer,每次传输1k大小的数据
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        //将信息put进buffer
        byteBuffer.put(msg.getBytes());
        //切换buffer模式
        byteBuffer.flip();
        //channel传输数据到server端，这里write第一个参数代表源结果，第二个参数代表附件的意思，第三个参数则是写操作完毕后，成功与否的回调接口，必须时实现了Completion接口的类。
        channel.write(byteBuffer,byteBuffer,new        AIOClientWriteHandler(channel,countDownLatch));
    }
}

```

 AIOClientWriteHandler 代表客户端写信息给服务器端的时候，如果系统读取这些数据成功，就会通过jvm回调这个类的completed方法，上面的sendMsg这个方法中调用了该类，并传入byteBuffer。**其中第二个byteBuffer实质上就是充当附件，传递到了 AIOClientWriteHandler中completed方法的byteBuffer里。**注意这里实现的接口是CompletionHandler<Integer,ByteBuffer>，Integer代表处理成功的结果字节数，ByteBuffer就是处理的目标对象。这个类构造要传入countDownLatch，以便如果写操作失败的时候，回调这个方法可以让客户端的run方法继续执行以关闭channel。

```java
public class AIOClientWriteHandler implements CompletionHandler<Integer,ByteBuffer> {
    private AsynchronousSocketChannel channel;
    private CountDownLatch countDownLatch;

    public AIOClientWriteHandler(AsynchronousSocketChannel asynchronousSocketChannel,CountDownLatch countDownLatch){
        this.channel = asynchronousSocketChannel;
        this.countDownLatch = countDownLatch;
    }

    /**
     *
     * @param result 代表服务器端传递的字节数
     * @param attachment 服务器端传输的数据文件
     */
    public void completed(Integer result, ByteBuffer attachment) {
        if(attachment.hasRemaining()){
            channel.write(attachment,attachment,this);
        }else{
        //读取服务端传回的数据
         ByteBuffer readBuffer = ByteBuffer.allocate(1024);
        //写操作完毕后，继续读操作
        channel.read(readBuffer,readBuffer,new AIOClientReadHandler(channel,countDownLatch));
        }
    }


    @Override
    public void failed(Throwable exc, ByteBuffer attachment) {
        //失败则将channel关闭并将countDownLatch释放，终止AIOClientHandler的等待并关闭channel
        System.out.println("服务器端写数据传输过程中失败，客户端读取错误，关闭channel");
        if(channel.isOpen()){
            try {
                channel.close();
                countDownLatch.countDown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        exc.printStackTrace();
    }
}

```

当客户端写操作成功后，就会进入到读取服务器传来的数据状态。该类同样实现了CompletionHandler<Integer, ByteBuffer>接口，Integer代表读取的字节数，ByteBuffer则是读取的内容。注意一点：**在completed方法中，使用了ByteBuffer的flip操作，用于切换ByteBuffer的读写模式，在对ByteBuffer进行读取操作之前，一定要先调用flip，如果不是这样就会抛出异常！比如attachment.remaining语句，如果不进行flip的话，读取的长度将不是传输过来内容的长度！我们需要调用flip方法，将position和limit的位置放置在读取的内容的首尾处才可以！**

```java
public class AIOClientReadHandler implements CompletionHandler<Integer, ByteBuffer> {
    private AsynchronousSocketChannel channel;
    private CountDownLatch countDownLatch;

    public AIOClientReadHandler(AsynchronousSocketChannel asynchronousSocketChannel,CountDownLatch countDownLatch){
        this.channel = asynchronousSocketChannel;
        this.countDownLatch = countDownLatch;
    }

    /**
     *
     * @param result 代表服务器端传递的字节数
     * @param attachment 服务器端传输的数据文件
     */
    @Override
    public void completed(Integer result, ByteBuffer attachment) {
        //首先切换buffer为读模式
        attachment.flip();
        //定义byte【】存放读取的数据字节
        byte[] bytes = new byte[attachment.remaining()];
        attachment.get(bytes);
        String s = new String(bytes,0,bytes.length);
        System.out.println("字符串总长度:"+s);
    }

    @Override
    public void failed(Throwable exc, ByteBuffer attachment) {
        //失败则将channel关闭并将countDownLatch释放，终止AIOClientHandler的等待并关闭channel
        System.out.println("服务器端写数据传输过程中失败，客户端读取错误，关闭channel");
        if(channel.isOpen()){
            try {
                channel.close();
                countDownLatch.countDown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        exc.printStackTrace();
    }
}

```

AIO的客户端启动main方法，其中通过一个while循环不断的接受键盘传递的信息，判断是否为字符串q，如果是就代表退出客户端的意思，进行channel的关闭，如果不是则进行和服务器的长连接，调用sendMsg方法，发送成功后立刻进行监听服务器的反馈，这个监听的过程和发送消息的过程都是异步执行的，不会阻塞在某个地方。比如你敲入absfs，服务器没有来得及统计这个串的长度，你紧接着又敲dadaiso，那么在没有收到服务器反馈之前，是可以传输两次字符串的，服务器将对这两次传输分别做对应的计算再应答。有了结果之后，才会通知客户端取获取。

```java
//AIO客户端启动
public class AIOClient {
    private static AIOClientHandler aioClientHandler;

    public static void start(){
        aioClientHandler= new AIOClientHandler(BIOConstant.DEFAULT_IP, 53004);
        //启动一个线程处理Handler请求。
        new Thread(aioClientHandler,"client").start();
    }

    public static boolean sendMsg(String msg){
        if(!msg.equals("q")){
            aioClientHandler.sendMsg(msg);
            return true;
        }
        System.out.println("return flasse");
        return false;
    }

    public static void main(String[] args) {
        AIOClient.start();
        Scanner scanner = new Scanner(System.in);
        String msg = "";
        while((msg=scanner.nextLine())!=null){
            if(!sendMsg(msg)){
                //将客户端的channel关闭，这里放行run方法，进行关闭channel
                aioClientHandler.countDownLatch.countDown();
                break;
            }
        }
    }
}

```



**服务器端实现**

基本上的原理和客户端的大同小异，下面这个类AIOAcceptHandler，用于处理接受客户端请求的，并实现了CompletionHandler<AsynchronousSocketChannel,AIOServerHandler>接口，这里解释下这两个泛型意思。

`AsynchronousSocketChannel`代表当受到客户端的连接时，会产生一个类似于Socket的和客户端通信的渠道，AIO模式下就是channel。也代表服务器端接受客户端连接禅城的结果的含义(就相当于accept返回socket，socket就是结果)，而AIOServerHandler则是作为附件，连接的操作是通过AIOServerHandler来调用serverChannel中的accept方法实现的，那么当每次新连接成功后，就要在completed方法中继续的调用accept方法，接受下一个新用户的连接。这就是为什么要传入这两个泛型的原因。

```java
public class AIOAcceptHandler implements CompletionHandler<AsynchronousSocketChannel,AIOServerHandler> {


    /**
     * 当系统调用了这个方法的时候，说明已经有新连接进行访问了
     * @param channel   和客户端的连接，可以通过它读取写入数据，和客户端交互，相当于socket
     * @param attachment 这个时AIOServerHandler，使用它用于再次接受客户端连接
     */
    @Override
    public void completed(AsynchronousSocketChannel channel, AIOServerHandler attachment) {
        //代表连接成功,将有效连接++
        AIOServer.connectCounter++;
        System.out.println("当前客户连接数："+AIOServer.connectCounter);

        //继续接受客户端连接，连接成功还是回调这个方法统计连接数
        attachment.serverSocketChannel.accept(attachment,this);
        //使用channel的read方法读取客户端数据
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        //读取数据，连接成功后就是要读取客户端数据。
        channel.read(byteBuffer,byteBuffer,new AIOServerReadHandler(channel));
    }

    @Override
    public void failed(Throwable exc, AIOServerHandler attachment) {

    }
}

```

AIOServerHandler同时实现了Runnable和CompletionHandler<AsynchronousSocketChannel,AIOServerHandler>方法。在run方法处就通过serverSocketChannel进行接受客户端连接，成功失败将会回调上面的类。

这个类主要用于初始化服务器并启动接受客户端的连接。

```java
public class AIOServerHandler implements Runnable,CompletionHandler<AsynchronousSocketChannel,AIOServerHandler> {
    //接受客户端请求的channel
   public AsynchronousServerSocketChannel serverSocketChannel;
    private CountDownLatch latch;
    private int port ;

    public AIOServerHandler(int port){
        try {
            serverSocketChannel = AsynchronousServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(53004));
            this.port = port;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Override
    public void run() {
        latch = new CountDownLatch(1);
        try {
            //用于接受客户端连接
            serverSocketChannel.accept(this,new AIOAcceptHandler());
            //这里一定要阻塞线程继续往下执行，因为AIO是异步IO，不会阻塞等待客户端accpet
            latch.await();
            serverSocketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void completed(AsynchronousSocketChannel result, AIOServerHandler attachment)     {
        System.out.println("启动成功...");
    }

    @Override
    public void failed(Throwable exc, AIOServerHandler attachment) {

    }
}

```

读取客户端数据的操作类,这里将读取数据和读取成功后，写数据给客户端这两个操作合并在一起写了。

```java

public class AIOServerReadHandler implements CompletionHandler<Integer, ByteBuffer> {

    private AsynchronousSocketChannel channel;

    public AIOServerReadHandler(AsynchronousSocketChannel channel) {
        this.channel = channel;
    }

    @Override
    public void completed(Integer result, ByteBuffer attachment) {
        //代表客户端主动的放弃tcp连接
        if(result==-1){
            AIOServer.connectCounter--;
            try {
                System.out.println("客户端放弃连接"+channel.getRemoteAddress());
                channel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        else{
            //读取到客户端传递的消息,因为缓存写入了channel传递的数据，如果要进行读取，必须要调用flip转为读取模式！
            attachment.flip();
            byte[] bytes = new byte[attachment.remaining()];
            attachment.get(bytes);
            String clientMsg = null;
            try {
                clientMsg = new String(bytes,"UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            System.out.println("收到客户端传递的消息:"+clientMsg);

            //计算长度
            int msgLen = clientMsg.length();
            byte [] msgByte =String.valueOf(msgLen).getBytes();
            //将长度返回给客户端
            final ByteBuffer byteBuffer = ByteBuffer.allocate(msgByte.length);
            byteBuffer.put(msgByte);
            //因为你在缓存中写入了msgByte数据，pos已经发生了变化了，此时必须要调用flip
            //将pos置为0，因为byteBuffer会通过channel进行传递到客户端，客户端的内存会对channel传递的数据进行读取
            //正确读取的前提必须要保证position和limit所处的位置正确，否则remaining方法将无法计算处正确的剩余字节！
            byteBuffer.flip();
            System.out.println("ppaf:"+byteBuffer);
            channel.write(byteBuffer, byteBuffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    //写操作完成
                    if(attachment.hasRemaining()){
                        channel.write(attachment,attachment,this);
                    }else{
                        //开始读操作
                        ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                        //继续读取操作。这些方法都是异步执行的。
                        channel.read(readBuffer,readBuffer,new AIOServerReadHandler(channel));
                    }

                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    try {
                        System.out.println("失败发送数据到客户端");
                        //失败的话就关闭和客户端的连接
                        channel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    @Override
    public void failed(Throwable exc, ByteBuffer attachment) {
        try {
            //失败的话就关闭和客户端的连接
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

```

启动服务器类

```java
public class AIOServer {
    //统计连接数量
    static int connectCounter;
    AIOServerHandler aioServerHandler;

    public static void startServer() throws IOException {
        AIOServerHandler aioServerHandler = new AIOServerHandler(53004);
        new Thread(aioServerHandler,"SERVER").start();
        System.out.println("服务端打开。。。"+aioServerHandler.serverSocketChannel.getLocalAddress());
    }

    public static void main(String[] args) throws IOException {
        AIOServer.startServer();
    }

}

```

在启动的时候需要先启动服务器，再启动客户端。就可以完成长连接基于AIO的端对端通信了！



以上就是AIO中通信的介绍和实现。