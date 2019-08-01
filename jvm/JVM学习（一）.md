# JVM学习（一）

**这一篇介绍volatile关键字以及jvm的位置**

1. jvm的体系结构

   ![1564132023130](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564132023130.png)

JVM是建立在我们的操作系统之上的。JVM的内存模型和cpu的缓存模型很类似，如下图：

![1564133496035](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564133496035.png)



其中，cpu和主内存模型中，中间层加入了CPU缓存模型，主要解决主内存读写速度比CPU慢的问题。CPU如果想要获取磁盘中存放的某些数据，那么主内存会先将磁盘数据读取，然后将数据放入cpu缓存中，cpu运算过程再不断的访问cpu缓存读写数据，当cpu操作完成后，把结果写入缓存再从缓存中写入主内存中，主内存再持久化到磁盘。

![1564132697863](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564132697863.png)

java内存模型其实和cpu缓存模型很类似，我们看到上图中，其中每个线程都有自己独立的工作内存（可以直白的把这个工作内存理解为cpu中的缓存），当线程A/B/C想要访问主内存中的某个共享变量时。JVM就会控制将主内存的共享变量拷贝一个副本到各个线程中的工作内存之中。然后线程再从工作内存中获取主存的共享变量副本。**现在我们考虑下这种模式有没有并发问题呢？**肯定时存在的。下面实例代码说明这种问题：

```java
public class VolitaileTest {

    private static boolean initFlag = false ;

    public static void main(String[] args) throws InterruptedException {
        new Thread(new Runnable() {
            public void run() {
                System.out.println("准备数据....");
                while(!initFlag){}
                System.out.println("数据准备完成!");
            }
        }).start();


        Thread.sleep(2000L);
        new Thread(new Runnable() {
            public void run() {
                prepareData();
            }
        }).start();
    }

    public static void prepareData(){
        System.out.println("prepareData");
        initFlag = true;
        System.out.println("prepareFinish");
    }
}

```

上面代码定义了一个initFlag并赋予初始值false ，定义一个线程A 去检测initFlag ，当initFlag为true时会打印数据准备完成！否则一直等待。

而线程B 就去调用prepareData方法尝试改变initFlag状态。正常来讲initFlag如果为true就会再线程A处跳出while循环然后打印数据准备完成！

![1564135085881](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564135085881.png)

上面时运行的结果，你会发现无论怎么运行，线程A不可能打印得出数据准备完成!这几个字，究竟为什么呢？我们想以下，竟然每个不同的线程都拥有类中共享变量的独立的一个副本，那么线程B在修改initFlag状态时，线程A能监视到？ 在不做任何干涉的情况下，线程A拥有的initFlag的值，并不会监视到线程B对它做的修改，因为他们两个在线程的工作区中属于不同的副本，但是又来源同一个共享变量。怎么让他们都能监听到彼此修改的值呢？？

**volatile** 关键字就起到了这个作用，它让标记的元素对其他线程可见，也就时保证了可见性。简单的讲它能使得被标注的共享变量在拷贝不同的副本到线程的工作区时，线程对副本的修改可以让另一个线程的副本监视到值的改变（就相当于另一个同一共享变量的副本的值也会发生改变）。

现在在initFlag前加入volatile关键字，再看效果

![1564217237053](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564217237053.png)

任务完成！



上面看到我们的volatile关键字起到了作用，那么到底是如何起作用的呢？这就要深入理解volatile的底层了。

**volatile底层研究**

在这之前，我们需要了解下jvm的一些原子操作

![1564364744011](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564364744011.png)

好，我们现在讲解下这些操作（就拿我们上面的代码为例）

线程1和线程2 在读取主内存的共享变量的时候的步骤（也就是分析bug的原因）

**线程1：**

1. jvm首先对主内存的initFlag进行**read** ，读取数据（此时并没有在线程1工作内存中）
2. jvm使用**load**操作将initFlag真正的写入线程1的工作内存中（这一步也就是创建主内存变量的副本）
3. jvm使用**use**操作，将工作内存变量交给线程1送去cpu执行 （这里一直做wihle判断）

**线程2**

1. 同线程1中的步骤1和步骤2，讲initFlag副本创建在工作内存中后，jvm使用**use**操作，将工作内存的副本交给线程2送去是、cpu处理
2. cpu处理完结果后jvm调用assign操作将结果放入工作内存中(这一步修改了initFlag的值！)
3. jvm调用**store**命令，将工作内存的数据写入主内存，注意！**此时主内存中有initFlag副本且其值为true但是没有真正的同步到主内存的真正的目标变量中喔**
4. jvm调用**write**命令，真正的把initFlag副本的值同步到真实目标变量中！

![1564367011768](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564367011768.png)好，我们明白了jvm最基础的原子操作，我们看一下jvm缓存速度不一致的几种解决方案。

![1564366214185](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564366214185.png)

先说下早期计算机为了解决jvm缓存速度不一致问题用的方法:

**总线加锁** (总线就是连接cpu和主内存的，cpu和主内存传递数据通过总线传递)

就拿上面原子操作的图解释，在线程读取（read）数据前会把**主内存的变量lock**也就是枷锁，其他线程与此同时再去读取或写入这个变量，将不能得到执行（要排队等待unlock）。这里当然没有可见性问题的发生，因为线程2读取完主内存变量后，再没有进行write操作将变量真正写入主内存变量之前，该变量一直都是加锁状态的，其他线程不能读取。这会导致原本是**并发执行的流程编程串行执行，性能急剧下降!**

**MESI缓存一致性协议**

先上一张图

![1564367473908](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564367473908.png)

这个协议是加在总线上的，其底层实现了cpu总线嗅探机制，就相当于一个监听器监听各个线程传递到总线上的变量情况。

依旧是上面的例子，线程2在进行initFlag状态修改的时候，把值置为true，**它必定是要经过总线才能到达主内存做操作的** ，此时cpu总线嗅探机制就会监听到自己感兴趣的变量正在通过总线写数据，然后就会立刻去将工作内存的变量副本值置为失效状态，而cpu在执行该变量的时候检查到这个变量失效了，就会重新的通过read操作读取主内存中的变量，再重新运行。这就是cpu总线嗅探机制的作用。保证工作内存中变量的最新状态。



**明白了上面的MESI协议，我们研究Volatile（c语言实现的）关键词的底层实现**

我们通过汇编语言可以看到Volatile的底层真正流程

我们需要下载hsdis.dll 下载地址https://pan.baidu.com/s/1-FD5lRH-5i9wY-JtmldGCA

下载完成后放在你的jdk版本对应的re\bin 目录下面 ，然后配置下VM options 赋值黏贴下面的命令

```java
-server -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:+PrintAssembly -XX:CompileCommand=compileonly,*VolitaileTest.prepareData
```

其中*号后的VolatileVisibilityTest.prepareData替换成你的类名.方法名即可

![1564370360462](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564370360462.png)

配置号vmOptions以及勾选你添加了hsdis-amd64.dll的的jdk后，我们运行测试程序,发现很多汇编指令在命令行上，我们只关注这一行

![1564370257623](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564370257623.png)

这一行是什么意思呢，我们看下面的IA-32架构软件开发者手册对lock指令的解释

![1564369068146](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564369068146.png)

也就是说它会对汇编前缀为lock 的指令的数据立刻协会到主内存中（也就是立刻更新主内存的变量），这里就会用到总线中的MESI机制了，我们上面lock的对象是initFlag变量，也就是线程2中调用完prepareData后就修改了initFlag值，随后jvm将这个修改结果**立刻store并write**到主内存的变量中，注意重点在这个过程，在执行store操作前会对内存的变量进行加锁，防止期间线程的访问与修改，而write后才进行释放锁。注意这一切的加锁解锁过程都是在内存中进行操作的！ 速度十分的快，力度相比总线加锁大大的降低！

而在锁解放后，其他的线程监听到这个变化就会导致其cpu缓存中存放该变量的副本值失效。cpu执行发现副本失效就重新会主内存进行read等操作，同步最新值。

**上面就是volatile为什么能够使得共享变量对线程的可见性原因**

我们继续研究volatile的其他特性

首先说明，并发编程的三大特性：**原子性，可见性，有序性**

Volatile保证了**可见性和有序性**，但是不保证**原子性**，保证原子性要synchronized关键字

下面对不保证原子性进行说明：

我们重新新建一个程序如下：

```java
public class AtomVolitaleProblem {

    //用volatile关键字修饰number，number是这个类的共享变量，对线程保证可见性但不保证原子性
    public volatile static int number = 0;

    public static void increase(){
        number++;
    }
    public static void main(String[] args) throws InterruptedException {
        //创建10个线程并且每个线程运算number++ 1000次
        Thread []threads = new Thread[10];
        for(int k=0;k<threads.length;k++) {
            threads[k]=new Thread(new Runnable() {
                public void run() {
                    for (int i = 0; i < 1000; i++) {
                        increase();
                    }
                }
            });
            threads[k].start();
        }

        //join，当所有线程都结束，才执行主线程,也就是将主线程的打印语句加到所有线程运算完毕才执行
        for(int k=0;k<threads.length;k++){
            //将这10个线程排在主线程之前执行
            threads[k].join();
        }

        System.out.println("总结果number:"+number);
    }


}

```

运行上面的代码结果：

![1564399346977](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564399346977.png)

发现什么问题？ 是不是number预期值应该是10000，怎么少了900多呢？ 而且你会发现每次运行结果都不一样，并且有时候又刚刚好等于10000。 出现这种情况，我们必须要了解原子性这一概念，我们看看volatile为什么会导致这种情况发生。

先上一张图：

![1564398695881](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564398695881.png)

这里记录了我们刚刚代码的那个程序（为了方便就画出两个线程）。 一开始两个线程都在工作内存存放变量num的副本，并且各自送往cpu执行++操作，assign完毕后，假设两个线程同时并发的想主内存存放自己的运算结果，因为加了volatile关键字，所以**哪个线程先把数据传递在总线处，哪个线程就可以获得lock锁(锁住主内存的num变量)**然后再对num进行赋值，并且释放锁。假设线程1抢到了锁并更新了主内存的num值。此时线程2没有锁只能等着线程1更新完成，这时候**cpu总线嗅探机制起作用了**，**它会立刻监听主内存的num发生了变化，立即将线程2工作内存的num副本值置为失效，然后cpu再去重新read并load主内存中的num值（相当于丢失了上一次cpu处理的结果），最终导致许多线程丢失了上一次更新的值，从而不等于1000乘10次++操作，最终的结果是<=1000*0。**关键的这句话就是导致volatile为什么不能保证原子性操作的原因了！



拓展：

volatile关键字还可以保证有序性，下面这段代码可能会出现**指令重排序**的结果，会出现意想不到的bug

```java
/**
 * 有序性
 */
public class SequenceProblem {
   public static int x =0 , y=0;

    public static void main(String[] args) throws InterruptedException {
        Thread t1= new Thread(new Runnable() {
            public void run() {
                int a = y ;
                x=1;
                System.out.println("a:"+a);
            }
        });
        Thread t2= new Thread(new Runnable() {
            public void run() {
                int b = x ;
                y=1;
                System.out.println("b:"+b);
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }
}

```

如果你运行上面的代码（可以自己加上100w次循环测试），可能会出现a=1,b=1的结果，但是正常的去想怎么可能会呢？ 如果你a=1 那肯定y=1并且在a=y之前执行完成（**也就是t2线程在t1线程执行前执行完**）。b=1那就代表x=1，x=1又说明**t1线程在t2线程执行前已经完成了** ，互相矛盾，究其原因就是指令重排序搞的鬼。

如何消除这种现象呢？ 只需要在 x,y变量声明处加上**volatile关键字** ，为什么这就能保证有序性能，还是结合上面的汇编结果，volatile声明的变量在赋值的时候会在汇编命令加上lock前缀，cpu发现lock前缀的命令就一定不会把它放在赋值读命令之后执行，也就是说，就拿t1讲，t1的run方法中a=y一定会在x=1之前执行，不会出现x=1,再执行a=y。并且x=1赋值时会在主内存处将x变量加上锁，保证了t2线程再t1赋值的时候不能读取x的值。(但是不保证原子性喔)

这就保证了程序执行的有序性！

