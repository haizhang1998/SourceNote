## 多线程学习（一） ----- OS层面的理解

引入一个问题： 什么是进程？ 它和线程是什么关系呢？

**进程：**是一个活跃的东西，是动态的，是资源分配的最小单位（cpu,内存,磁盘）	

**线程:**  cpu调度的最小单位，是归宿于进程的！它的使用资源都是从进程拿到的! 而线程独有的资源有，栈,程序计数器。 进程和线程之间，线程必须依赖于进程而存在，就像寄生虫寄生在本体中，寄生虫就如同线程，本体就是进程。

> 同一时间，1个cpu只能处理1个线程 ，那么如果你的机器是多核的（多个cpu），就可以达到同一时间内执行多个线程（有几核就执行多少个），在Java中，java程序本身就是一个多线程，如果想要得到机器上可用的cpu数目（得到的逻辑处理器数，一般是cpu真实数目的2倍，Intel使用了一种超线程技术达成这种）可以使用Runtime.getRuntime().availableProcessors();

![1565704486571](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565704486571.png)



**并行和并发**

在我们了解了线程和进程的概念后，并行和并发是对线程来说必不可少的。我们知道cpu会去控制这些操作。

并发是应用交替执行的任务（有个时间概念，于cpu的时间轮转机制有关）。并行是同时执行（可以是完完全全的同时进行两个程序），能同时执行多少个进程，得看你是几核的cpu。cpu的数目越多，可同时执行的线程就越多，效率就越高。

图解：

* 并发

![1565705313173](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565705313173.png)

就好似有两个队列，大家都要等待区买可乐，可乐贩卖机就一台，所有的人只能够排队等候，轮到它的时候才能在贩卖机上执行它想要买的可乐（类似于线程的任务）

* 并行

  ![1565705431758](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565705431758.png)

有两个队列， 并且每个队列对应1个独立的贩卖机，2个队列的人群可以同时在不同的贩卖机上购买可乐，这里强调**同时，独立** ，这是并行的要点。



### Java里的程序天生就是多线程的，那么有几种新启线程的方式？

我们上面只停留在一些理论方面，那么在java中，线程是如何创建呢？新启线程的方式有多少种呢？

这个问题的答案是**2种！** ，哪两种? 

我们来观摩下Thread的源码：

```html
There are two ways to create a new thread of execution. One is to
declare a class to be a subclass of <code>Thread</code>. This
 subclass should override the <code>run</code> method of class
 <code>Thread</code>. The other way to create a thread is to declare a class that
implements the <code>Runnable</code> interface
```

这个注解清清楚楚的写道只有2个途径新启一个线程，一个就是定义一个子类继承Thread，另一个就是实现Runnable接口。**此时你或许会抱怨，为什么Callable接口不算新启一个线程呢？**

```java
public
class Thread implements Runnable 
```

可以看到，Thread类，不论如何，只要想运行一个线程，必须新建一个Thread对象，然后再将线程启动，赋予线程任务生命力。而我们的Thread类实现了Runnable接口，当然，为什么不实现Callable接口呢？这就是问题关键处，并且Thread文档所说的新启线程的方式2种最正规。而Callable接口其实也间接实现了Runnable接口。可以说，Runnable和Callable接口的run和call方法相当于一个**任务**！ 这两个接口是以**任务**作为抽象的。而Thread才是对**线程的抽象** 。 Runnable和Callable并不能被成为线程！

