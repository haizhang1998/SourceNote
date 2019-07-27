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

