# 多线程----Synchronized关键字

在开始之前，我们先想个问题，为什么要用Synchronized这个关键字？

在多线程编程中，有可能会出现多个线程同时访问同一个**共享**,**可变** 资源的情况，这种资源可以是：**变量，对象，文件等**

其中，共享代表资源可被多线程访问。可变代表资源可在其声明周期内被修改。

**总结来说，由于多线程执行过程是不可控制的（cpu利用时间片的方式运行线程），所以需要同步机制来协同对象可变状态的访问**

下面给出一个具体的例子，这个项目是springBoot的一个项目，模拟多个线程并发对一个变量读写

首先我们定义一个商品服务类(GoodService)

```java
/**
模拟抢购货物的操作，揭示并发问题
*/
@Service
public class GoodService implements GoodServiceInterface {

    //货物的库存
    private static int stock = 5;

    public void buyGoods(){

        if(stock<=0){
            System.out.println(Thread.currentThread().getName()+"货物库存不足，无法购买！");
        }else{
            stock -- ;
            System.out.println(Thread.currentThread().getName()+"成功抢购货物:"+stock);
        }
    }
}
```

其次我们定义控制层

```java
@Controller
@RequestMapping("/goods")
public class GoodsController {

    @Autowired
    @Qualifier("goodService")
    GoodServiceInterface goodServiceInterface;

    @RequestMapping("/buy")
    public void buyGoods(){
        goodServiceInterface.buyGoods();
    }
}
```

ok,现在根据localhost:8080/goods/buy既可以抢购货物。先把springBoot运行起来

```java
/**
 *标注这个类是springboot的一个应用
 */
@SpringBootApplication(scanBasePackages = "com.haizhang")

public class HelloWorldMainApplication {

    public static void main(String[] args) {
        //启动springBoot应用，顺便启动tomcat了，不用配置tomcat可以用localhost:8080加访问路径访问网站,因为它自带了tomcat环境
        SpringApplication.run(HelloWorldMainApplication.class,args);
    }
}
```

直接点击run既可以启动springboot项目，然后我们就可以访问上面的路径减库存（抢购）,我们可以利用Apache下的jmeter软件进行模拟多线程并发访问同一个路径，下载直接去官网下即可。这里讲明步骤

**创建模拟多线程访问同一个http路径**

step1: 在测试计划（根目录）创建一个线程组（threadGroup）

![1564448764511](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564448764511.png)

上面配置了1000个线程，每个线程循环5次。 

step2:在线程组添加Sampler（测试样本）选中httpRequest，编辑按下图编辑

![1564448856327](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564448856327.png)

其中protocol 就是协议 http/https等，Path就是你项目的访问路径。

附上一个Jmeter教程：https://blog.csdn.net/u012111923/article/details/80705141



**配置完毕，开始测试**

我们启动jmeter，直接点击上面菜单的绿色三角箭头就可以直接运行创建线程模拟并发访问http路径。然后你会发现类似于下图的结果（多试几次）

![1564448387923](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564448387923.png)

发现什么问题了没？ 其中exec-19和exec-128同时得到货物且都进行了stock--操作，为什么会同时显示剩余库存为3？ 按理说一个是3一个是2才是对的吧！ 你再数下有多少线程是成功抢购了！ 是不是超出了我们库存5。 这就是并发所带来的问题，方法在没有受到保护的时候多线程访问共享变量，并发问题随之而来！ 

所以为什么现在我们要了解锁这一个概念，我们就说说synchronized是如何保证线程访问共享变量的安全性的。先给出一个图（以上面程序为例子）

![1564412645831](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564412645831.png)

我们称**临界资源是一次仅允许一个进程使用的共享资源。各进程采取互斥的方式，实现共享的资源称作临界资源**。

而上面的例子，stock就是一个临界资源，它一次仅允许一个线程访问。如果对临界资源的访问加锁，多个线程就可以同时访问临界资源，从而导致上面的情况。而加锁（假设互斥锁） 后，所有的线程并发对临界资源的访问就会编程序列化访问临界资源。比如现在有线程1/2/3同时访问stock，因为我们对stock的访问加上了synchronized（锁），故此他们只能去争取这把锁，假设线程2抢到了，那么线程1/3就不能访问stock，它们得呆在等待队列（watting）中，直到线程2释放锁后，线程1/3又去争抢锁。。。循环上面流程。

加锁的目的就是序列化访问临界资源（共享资源），也就是同一时刻只能有一个线程访问临界资源。

**如何加锁呢？**

我们可以把锁加在要并发访问的方法上，但是不建议这样做，性能损耗太大。更好的做法是将锁加在访问方法的内部,我们在加锁之前，可以声明一个对象当作锁。这样只有持有该对象锁才可以访问临界区资源

```java
@Service
public class GoodService implements GoodServiceInterface {
    //货物的库存
    private static int stock = 5;
    //充当锁
    private static Object lock = new Object();

    public void buyGoods(){
        synchronized (lock) {
            if (stock <= 0) {
                System.out.println(Thread.currentThread().getName() + "货物库存不足，无法购买！");
            } else {
                stock--;
                System.out.println(Thread.currentThread().getName() + "成功抢购货物:" + stock);
            }
        }
    }
}

```

看得出lock这把锁是关联这个类的。锁还可以加在buyGoods方法头处

```java
//这里的锁是和方法对象相关联的
public synchronized lock1 (){}

//这里的锁是和类关联的
public static synchronized lock2 (){}
```

**什么是显式锁，什么是隐式锁？**

显式锁：我们需要写代码去控制加锁和解锁

隐式锁： 我们不需要通过自己写代码去加锁和解锁（synchronized是隐式锁不需要手动加锁解锁），加锁解锁的流程是交给jvm内部去处理的。

**既然synchronized式隐式锁，jvm底层是如何达到加锁解锁的目的的呢？**

其实jvm在线程进入临界区的前后加了monitorenter关键字，我们可以查询JVM指令手册知道

1. monitorenter 代表进入并获取对象监视器(出现这个关键字表示上锁成功)

2. monitorexit 释放并退出对象监视器(出现这个关键字表示解锁成功)

![1564452170774](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564452170774.png)

**现在又有个问题，jvm是如何知道对象有没有加锁，以及锁状态如何？**

这里我们就要引入对象在jvm的内存模型

![1564475103488](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564475103488.png)

1. **对象头：**主要是用来记录该对象锁的状态及类型，记录了是否上锁，以及轻量级锁，重量级锁等，还有数组的长度，hash码等信息。
2. **对象的实例数据：**主要包括创建对象时，对象中成员变量，方法等。
3. **对齐填充位：** 主要是规范对象的内存分布，当分布不满时，jvm会自动的进行填充剩余空间

**那么实例对象内存存储时怎么样的呢？**

对象的实例在堆区，对象的元数据存在元空间（就是方法区），对象的引用存在栈空间

**我们上面提到了锁的状态，那么锁的状态有哪些呢？**

这里有一张图代表在不同情况下不同的锁状态：

![1564476207026](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564476207026.png)

在JDK>1.6中，synchronized的实现进行了各种优化如适应性自旋，锁消除，锁粗化，轻量级锁和偏向锁等。JDK>1.6默认会开启偏向锁

下面给出命令来控制开启/关闭偏向锁：

1. 开启偏向锁: -XX:+UseBiasedLocking -XX:BiasedLockingStartupDelay=0

2. 关闭偏向锁: -XX:-UseBiasedLocking

**知道了这些变化我们看看锁的膨胀升级过程**

![1564476523951](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564476523951.png)

整个流程就是jvm一开始去检测对象锁是否只有一个线程在访问，如果是现在的锁状态就是偏向锁，如果jvm发现不只是一个线程，而是多个线程想要去访问临界资源，但是互相竞争不激烈或者说没有竞争，那jvm就会把锁升级为轻量级锁。而当jvm发现轻量级锁进一步被多线程竞争，而且有线程等待时间过长的现象，就会把锁升级为重量级锁，追求吞吐量。

**到此位置，我们需要进一步了解jvm到底是如何升级改变锁状态的**

po上一张图

![1564477120473](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564477120473.png)

上面的图就是锁对象的Mark Word （就是上面例子中标注synchronized的object的对象信息）。其中这32位bit是描述与锁的状态相关的 信息。

1. 无锁： jvm会去检测锁标志位，如果发现是01 状态并且锁标志位的前1bit为0，代表对象（object）处于无锁态，下面举个例子，有线程1和线程2两个线程，其中线程1比线程2先执行访问同步代码块，此时jvm会去检测锁标志位，发现是01处于无锁态的时候，就会调用CAS算法去将Object Mark Word的偏向标志位（锁标志位的前1bit）修改为1。这时候锁在线程1访问同步块期间就升级为了偏向锁。

![1564477627937](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564477627937.png)

那么jvm升级锁为偏向锁之后，又怎么知道这个锁被哪个线程拥有呢？ 这时候jvm将对象头MarkWord中线程ID指向到线程1的ID，这里就将这个偏向锁利用指针和线程1进行绑定了。此时出现一个问题，线程2在jvm升级完偏向锁之后想要访问临界区资源，jvm如何做处理呢？

![1564477685608](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564477685608.png)

首先jvm会去判断检查是否偏向线程ID（ObjectMarkWord中的前23bit是偏向线程ID位）是线程2的ID，如果是就允许访问临界资源，若不是,CAS将准备修改MarkWord的线程ID为自己的ID。如果修改失败（说明此时偏向锁已经被另一个线程持有），**JVM就会撤销偏向锁，并且将锁升级为轻量锁** （如果线程2期间获取到了偏向锁则不会升级） 

![1564477966957](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564477966957.png)

那么此时就会有一个问题，你怎么知道线程1运行完了？你就擅自修改锁状态？线程1是如何释放自己和当初获取到状态不一致的轻量级锁呢？

好，看上图。如果线程1此时没有退出同步块，jvm就将锁升级为轻量锁，而线程1运行完同步代码块后，就需要jvm将ThreadId置为空，此时锁的状态位以及偏向锁位的状态并不会影响锁释放的流程，它们已经被jvm利用CAS进行修改完毕了。

如果线程1在线程2访问同步块之前，已经访问完毕了，就会执行解锁操作。并且将Mark word的偏向锁位置为0（即无锁状态）



**锁在无锁状态如何升级为轻量级锁再到重级锁呢？**

我们依旧举例两个线程1/2，但是现在不一样了，线程1和线程2一开始就同时并发访问同步块区争抢锁，jvm自动检测到存在锁的竞争，那就会将Object Mark Word状态从无锁升级到轻量级锁，那么轻量级锁在jvm内存中又是如何创建的呢？

![1564478113501](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564478113501.png)

jvm会在当前线程的线程栈上开辟一个内存空间并指向object对象锁的markword，markword同时在前23bit中保存了指针指向线程栈中的线程，并且此时markword的锁状态时00（轻量级锁），jvm在底层使用CAS进行操作，通过CAS的比较和交换去处理这些指针的指向的操作，保证了原子性。这就是轻量级锁在jvm中的体现。

![1564478569364](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564478569364.png)

线程1和线程2同时的进行CAS修改，那么当jvm成功的在其中一个线程的线程栈中开辟了内存空间指向对象锁之后（假设时在线程1中开辟），另一个线程用CAS修改MarkWord的线程ID时就会失败！当失败后，这个线程（线程2）就开始自旋（也就时重复尝试用CAS修改MarkWord）**在jdk1.7后我们可以自己设置自旋的次数，而且可以进行自适应自旋的次数**，当然，如果一直失败（也就是锁一直没有释放），此时轻量级锁就不适用了（因为等待时间过长），那么jvm就会将锁晋升到**重量级锁**。

![1564479150344](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564479150344.png)

那么线程1的同步块执行完毕后，线程1才会释放之前获取的轻量级锁（已经变重量级了），这个解锁的操作之中会唤醒之前被组设的线程。唤醒的线程又会加入新一轮锁的竞争（抢夺重量级的锁，此时吞吐量大了）

![1564479291778](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564479291778.png)



**看到这里你可能会想，为什么不一开始就直接用重量级锁呢？而是要使用偏量锁和轻量级锁呢**

使用偏量锁和轻量级锁的目的是因为在加锁的过程中，jvm不会像系统底层申请互斥量，申请互斥量会导致我们的操作系统从用户态到内核态的转变。这个开销是比较大的。一旦你申请了互斥量，就会为他们添加互斥锁。一旦添加了互斥锁，就会阻塞部分的线程，从而会导致线程上下文切换，这个过程开销十分的大，非常耗资源。

**你可能会有疑问，为什么上下文切换会非常耗费资源呢？**

