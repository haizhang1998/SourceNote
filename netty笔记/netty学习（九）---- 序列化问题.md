### netty学习（九）---- 序列化问题

jdk序列化的优点：

**简单易用，**我们只需要实现Serializable接口既可以对bean进行序列化，其中要配合ByteArrayOutputStream和ObjectOutputStream进行，不需要额外的类库支持。

jdk序列化的缺点：

1. java的序列化机制对于其他的语言，比如python，c++等是模糊的，别的语言构建的程序不知道java做了先什么操作，那么进行跨应用程序交互的时候就会有问题；
2. jdk提供的序列化方法产生的字节数组，码流过于大，比自定义的序列化方法大的多。并且性能上jdk的速度远远大于自定义的序列化方法

**jdk和自定义序列化方法产生的字节数组大小对比**

jdk提供序列化方法主要是通过ByteArrayOutputStream和ObjectOutputStream进行的

![1568885094058](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568885094058.png)



实际上外界有很多成熟的序列化框架可以使用。比如protobuf



### MessagePack

@Message表示实体类是需要序列化的实体类

1.序列化类MsgPachEncoder extends MessageToByteEncoder<Object>

要实现编码工作

```java
encode(ctx,obj,ByteBuf out){
    MessagePack messagePack = new MessagePack();
    byte[] raw = messagePack.write(msg);
    out.writeBytes(raw); //客户端发送的数据的编码
    
}
```

2. 设置报文的包头长度，避免粘包半包问题 new  LengthFieldPrepender(2)