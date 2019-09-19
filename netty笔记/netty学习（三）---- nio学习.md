### netty学习（三）---- nio学习

在nio学习开始前，我们需要对ByteBuffer进行详细的讲解，nio的操作很多都是基于ByteBuffer进行的

**ByteBuffer讲解**

先摆上一张ByteBuffer的图

![1568256968595](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568256968595.png)

其中有三个重要的属性分别如图中所示，这里讲下含义：

**limit:** 可以理解为一个阀，代表当前buffer数组最大可以写/读的位置，他和capacity不同，limit<=capacity ，当limit小于capacity，那么我们只能操作[position,limit]之间的字符，而(limit,capacity]之间的就算有空闲位置，也不可以进行读写操作。

**capacity**: buffer数组总容量，也是最大容量

**position:** 记录现在操作的的位置，在写模式下则代表当前可进行写操作的位置，每写入一个字符position+1,从0开始。在读模式下则代表当前读取到的字符位置，从0开始，每读一次position就+1。 

> position的变化在下面会有讲，并不是每次读写都会改变，具体要看方法。



**如何为ByteBuffer分配内存空间？**

为ByteBuffer分配内存空间主要有两种方式，一种时直接在**堆内存**进行分配，另一种时**直接内存**进行分配

> 直接内存大多时候也被称为堆外内存，自从 JDK 引入 NIO 后，直接内存的使用也越来越普遍。通过 native 方法可以分配堆外内存，通过 DirectByteBuffer 对象来操作。直接内存不属于 Java 堆，所以它不受堆大小限制，但是它受物理内存大小的限制。

分配内存空间代码：

```java
//演示使用堆内存和直接内存创建ByteBuffer
public class AllocateByteBuffer {

    public static void main(String[] args) {
        System.out.println("在堆上创建byteBuffer分配空间前before，可用的虚拟机内存大小:"+Runtime.getRuntime().freeMemory());
        //注意要演示明西显的话必须将空间分配大一点，否则得不到对应的效果，这处于jvm底层的一些调优
        ByteBuffer byteBufferByheap = ByteBuffer.allocate(10240000);
        System.out.println("在堆上创建byteBuffer分配空间后after，可用的虚拟机内存大小:"+Runtime.getRuntime().freeMemory());
        //直接内存分配同样的空间，打印虚拟机可用大小
        System.out.println("在直接内存上创建byteBuffer分配空间前before，可用的虚拟机内存大小:"+Runtime.getRuntime().freeMemory());
        //注意要演示明西显的话必须将空间分配大一点，否则得不到对应的效果，这处于jvm底层的一些调优
        ByteBuffer byteBufferByDirect = ByteBuffer.allocateDirect(10240000);
        System.out.println("在直接内存上创建byteBuffer分配空间后after，可用的虚拟机内存大小:"+Runtime.getRuntime().freeMemory());
    }
}
```

上面使用` ByteBuffer.allocate(10240000);` 进行分配10240000字节的**堆内存空间** ，使用`ByteBuffer.allocateDirect(10240000);`j进行分配10240000字节的**直接内存空间**

我们运行下上面的程序：
![1568269452236](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568269452236.png)

通过打印的结果，发现分配堆内存会占用虚拟机内存大小，而分配直接内存的话，对虚拟机内存大小没有影响。

这两种分配方式分别在不同的场景使用：

* 在网络通讯频繁的情景下，可以使用直接内存分配的方式取处理 （一般直接内存较慢点）
* 在计算逻辑比较多的情景下，可以使用堆内存的方式分配处理（堆内存分配象对直接内存快）



**ByteBuffer中flip函数的作用**

我们一般使用byteBuffer.flip() 将ByteBuffer的模式切换成读模式，如下图：

![1568270296200](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568270296200.png)

左侧时在写模式下，ByteBuffer将数组录入后，position，limit，capacity所处的位置。 当进行flip函数调用之后，就会发生position重置到最顶层（也就是下标为0处），limit则变成之前position所在的位置。  那么flip之后，我们就可以进行读取数据的操作了，每次读取一次，position就会+1 。

我们看下flip的源码：

```java
   /**
      将limit置为以前position所指向的位置，将position置为0的位置，如果mark有标记（mark用于保存之前position的标记，以便还原数组）就将mark废弃，置为-1
     * @return  This buffer
     */
    public final Buffer flip() {
        limit = position;
        position = 0;
        mark = -1;
        return this;
    }
```

展示下flip的操作效果，这里给出一个demo

```java
   public static void main(String[] args) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        System.out.println("装载数据前"+byteBuffer);
        byteBuffer.put((byte) 'a') //放置在index为0处
                .put((byte) 'b') //放置在index为1处
                .put((byte) 'c') //放置在index为2处
                .put((byte) 'd') //放置在index为3处
                .put((byte) 'e') //放置在index为4处
                .put((byte) 'f');//放置在index为5处
        System.out.println("装载数据后"+byteBuffer);
        byteBuffer.flip();
        System.out.println("调用flip后"+byteBuffer);
    }
```

结果：

![1568270877314](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568270877314.png)

我们通过put方法将不同的byte投放进数组中，观测到装载数据之前，pos指向位置0，limit和capacity都是1024，而装载后pos变换到位置6，limit和capacity没变化。而flip切换成读模式之后，将pos指向index为0处，limit指向到了原先pos所指的地方。

**读取数据和写数据的时候，三者指针会发生什么变化呢？**

同样的我们装载进6个字符，分别比较byteBuffer中的put,get和rewind等方法，观测指针的改变

```java
   public static void main(String[] args) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        System.out.println("装载数据前"+byteBuffer);
        byteBuffer.put((byte) 'a') //放置在index为0处
                .put((byte) 'b') //放置在index为1处
                .put((byte) 'c') //放置在index为2处
                .put((byte) 'd') //放置在index为3处
                .put((byte) 'e') //放置在index为4处
                .put((byte) 'f');//放置在index为5处
        System.out.println("装载数据后"+byteBuffer);
        byteBuffer.flip();
        System.out.println("调用flip后"+byteBuffer);
        //使用象对索引的方式获取pos指向的数据，获取完毕后pos+1
        byteBuffer.get();
        System.out.println("调用byteBuffer.get()用象对索引的方式获取pos指向的数据，读取记录后:"+byteBuffer);
        //使用绝对索引方式获取索引下标为2的数据
        byteBuffer.get(2);
       System.out.println("调用byteBuffer.get(2);使用绝对索引方式获取索引下标为2的数据，读取记录后:"+byteBuffer);
        //使用指定索引范围的方式获取数据
        byte [] b = new byte[3];
        byteBuffer.get(b,0,2);
        System.out.println("使用指定索引范围方式获取数据  byteBuffer.get(b,0,2);"+byteBuffer);
    }
```

结果：

![1568271635208](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568271635208.png)

注意，**在使用get方法的时候，分为相对索引下标获取和绝对索引下标获取数据，使用绝对索引下标，也就是指定下标的情况下get(index)是不会将pos改变的！ ** 而使用byteBuffer(dst, index1 ,length),指定下标并指定数据的长度，获取到的数据实际上是从pos开始往后的length个数据。它也会改变数组pos的坐标！

和get方法类似，在使用put方法时，也是同理

```java
 public static void main(String[] args) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        System.out.println("装载数据前"+byteBuffer);
        byteBuffer.put((byte) 'a') //放置在index为0处
                .put((byte) 'b') //放置在index为1处
                .put((byte) 'c') //放置在index为2处
                .put((byte) 'd') //放置在index为3处
                .put((byte) 'e') //放置在index为4处
                .put((byte) 'f');//放置在index为5处
        System.out.println("装载数据后"+byteBuffer);
        byteBuffer.flip();
        System.out.println("调用flip后"+byteBuffer);
        byteBuffer.put((byte)'s');
        System.out.println("put后"+byteBuffer);
        byteBuffer.put(3,(byte)'o');
        System.out.println("byteBuffer.put(3,(byte)'o');后"+byteBuffer);

    }
```

结果：

![1568272218726](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568272218726.png)

当你指定坐标去put的时候，就会将原先的数据进行覆盖，但是不会修改pos的位置。当你使用相对路径去put的时候，也就是put()，则会修改pos位置。



**演示rewind，clear，mark，reset的作用**

rewind方法的目的是将当前position坐标置为0,而limit则保持不变

```java
public final Buffer rewind() {
        position = 0;
        mark = -1;
        return this;
}
```

clear方法目的和rewind大同小异，我们看其源码

```java
 public final Buffer clear() {
        position = 0;
        limit = capacity;
        mark = -1;
        return this;
    }
```

mark则是记录当前的position，可以理解成记录当前的状态，以便后续想要进行还原（但是不要调用clear和rewind和flip这些方法，回将mark标记重置）。

```java
    public final Buffer mark() {
        mark = position;
        return this;
    }

```

reset则是还原上一次mark记录的position地址

```java
public final Buffer reset() {
        int m = mark;
        if (m < 0)
            throw new InvalidMarkException();
        position = m;
        return this;
}
```

实例代码：

```java
public static void main(String[] args) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
        System.out.println("装载数据前"+byteBuffer);
        byteBuffer.put((byte) 'a') //放置在index为0处
                .put((byte) 'b') //放置在index为1处
                .put((byte) 'c') //放置在index为2处
                .put((byte) 'd') //放置在index为3处
                .put((byte) 'e') //放置在index为4处
                .put((byte) 'f');//放置在index为5处
        System.out.println("装载数据后"+byteBuffer);
        byteBuffer.flip();
        System.out.println("调用flip后"+byteBuffer);
        byteBuffer.get();
        byteBuffer.get();
        System.out.println("调用两次get之后"+byteBuffer);
        byteBuffer.mark();
        System.out.println("mark当前的position用于reset还原");
        byteBuffer.get();
        System.out.println("再次调用一次get:"+byteBuffer);
        System.out.println("reset操作后："+byteBuffer.reset());
        byteBuffer.rewind();
        System.out.println("调用rewind之后"+byteBuffer);
        byteBuffer.clear();
        System.out.println("调用clear之后:"+byteBuffer);
    }
```

结果：

![1568273569344](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568273569344.png)

> 我们经常会用byteBuffer中的remainning方法去统计byteBuffer还有多少字节没有读取。
>
> public final int remaining()
>
>  {    return limit - position;}



**如何使用ByteBuffer配合Channel完成数据的读写？**

Channel中只能允许传输Byte类型的数据，而它回合Buffer进行数据的交换。ByteBuffer中我们通常用以下方式读取channel中的数据：

**读取channel中的数据并写到buffer**  :channel.read(buf)

**buffer读取channel中传递过来的数据**：channel.write(buffer)

**通过buffer的put方法写数据（将data中存放的字节数据写入到buffer中） **: buffer.put(data);



**写数据到buffer再从buffer中读出来的常用步骤:**

1. 写入数据到buf
2. 调用flip
3. 从buf读数据
4. 清空buffer



