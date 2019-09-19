### netty学习（一） ----- JAVA网络编程基础

**ISO七层模型**

提出的意义就是如果你能实现OSI模型，就能够实现网络的互连效果。

OSI分为以下几层：

![1568101668324](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568101668324.png)

每层的具体作用如下：

![1568101829482](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568101829482.png)

**物理层**

DTE（数据终端，比如电脑）、DCE（光猫，拨号上网的猫）

* 提供数据传输的实际通道

> 事实上，osi七层模型没有被广泛使用

我们一般经常使用TCP/IP协议，将OIS七层协议缩减成只有四层

![1568103390270](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568103390270.png)

**传输层中TCP和UDP之间的区别**

TCP是面向连接的，可靠的，连接时要进行三次握手，释放连接时会进行四次挥手。发送数据包，有超时重传，滑动窗口等机制保证数据包完整的传输到另一端。

UDP是不建立连接的，UDP通讯的时候，不需要对方确认，资源消耗很少，UDP主要面向查询和应答服务。比如我们的聊天室，DNS（域名解析服务），广播通讯等等。这些都是要求高速传输，udp速度比tcp要快的多。但是udp经常会发生丢包现象。对于一些对安全要求不高，可接受偶尔丢包的功能可以使用udp实现。

**简述以下TCP三次握手的过程**

![1568104181753](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568104181753.png)

* 三次握手

1. 服务器端主动启动，打开端口等待客户端的连接
2. 客户端发送请求报文，并讲SYN（同步状态）置为1 ，seq(序列号)设置成一个随机值J。
3. 服务器端接受到请求报文，准备像客户端发送响应报文，并将自己的状态设置为SYN_RCVID
4. 服务端发送响应报文，并讲SYN设置为1 ，ACK设置为1 ，ack设置为客户端传递过来的seq序列号加1，并将seq设置为一个新的随机数K
5. 客户端接受到服务器端传递的响应报文，检测校验通过后，准备再次发送确认报文给客户端。这时客户端会设置ACK=1，并重置ack为服务端传递的序列号（ack）+1. 此时客户端已经处于ESTABLISHED状态了
6. 服务器端收到后进行校验，确认通过后，服务器端将自己的状态调整为ESTABLISHED。
7. 至此3次握手过程结束，客户端和服务端就可以进行报文的传递了。

**为什么不只进行两次握手呢？**

试想一个场景，如果客户端放了服务端的鸽子，怎么办呢？





###  

####TCP/IP中的数据包

![1568106448700](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568106448700.png)



![1568106712731](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568106712731.png)

![1568106864970](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568106864970.png)



![1568106899696](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568106899696.png)

![1568108379957](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568108379957.png)



![1568109041267](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568109041267.png)

![1568108999242](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568108999242.png)



![1568109100961](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568109100961.png)



![1568109177833](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568109177833.png)

![1568109252366](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568109252366.png)

![1568109658724](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568109658724.png)

select 打开连接时有限制

poll 和select差不多，

epoll 限制上限很大，可以接受很多连接