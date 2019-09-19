### 多线程学习（提升）-----AQS解读

**前言：**

在我们常用了显示锁，比如ReentraLock，Semaphore，CylicBarrier ，CountDownLatch等等 ，都会有一个内部类Sync，那么有公平的和非公平的实现，他们继承了Sync并定制自己独特的使用方法。那么Sync其实就是继承了我们讲到的AQS（全名：AbstractQueuedSynchronizer）



**JUC就是java 并发包常用的工具类，我们接下来提到的AQS在JUC这个包下的类中经常使用，如下图分布：**

![1567818736246](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567818736246.png)



**AQS是JUC的常用框架，AQS的一些特性如下图介绍：**

![1567819392900](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567819392900.png)

那么上面简单的提到了AQS的一些基本概念和特性，本文中利用ReentraLock这个类来深入研究AQS的底层具体逻辑

### AQS源码解读

先给出一个案例:

```java
public class AqsAnalysis {
    static ReentrantLock reentrantLock = new ReentrantLock();

    public static void aqsTest(){
        //在访问该方法前要加锁
        reentrantLock.lock();
        System.out.println(Thread.currentThread().getName()+" 获取到第一把lock");
        //再次加锁
        reentrantLock.lock();
        System.out.println(Thread.currentThread().getName()+" 获取到第二把lock");
        //准备释放
        reentrantLock.unlock();
        System.out.println(Thread.currentThread().getName()+" 释放一把lock，当前线程持有该锁数目:"+reentrantLock.getHoldCount());
        reentrantLock.unlock();
        System.out.println(Thread.currentThread().getName()+" 释放一把lock，当前线程持有该锁数目:"+reentrantLock.getHoldCount());
    }

    public static void main(String[] args) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                aqsTest();
            }
        },"T1").start();
        //        new Thread(new Runnable() {
////            @Override
////            public void run() {
////                aqsTest();
////            }
////        },"T2").start();
    }
}

```

上面代码很简单，定义了2个线程，不过为了演示，先将T2线程注释掉，先研究单线程。然后线程内部调用aqsTest（），其逻辑就是获取两次可重入锁和释放两次可重入锁。我们以此案例进行切入点研究AQS实现原理。如下图打上断点，开始debug。

![1567831098934](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567831098934.png)

我们看到，reentraLock内部的一个关键属性sync（它的父类就是AQS），而它是ReentraLock的内部类Sync的对象实例。其中AQS有几个属性，如下

```java
/**
     * 等待队列的头部指针，懒加载模式，当调用setHead的时候才会初始化。
     */
    private transient volatile Node head;

    /**
     * 等待队列的尾部指针，采用懒加载模式，只有当调用比如enq() addWaiter()等方法的时候才回去初始化，默认为null
     */
    private transient volatile Node tail;

    /**
     * 记录当前线程拥有多少把锁（比如可重入锁，lock.lock() 多少次，state对应的值就增加多少次+1）
     */
    private volatile int state;


    /**
       AbstractOwnableSynchronizer（AQS父类的属性）
     * 在排他锁模式下，标记拥有当前排他锁的线程（exclusive）
     */
    private transient Thread exclusiveOwnerThread;

```

正如上面debug的结果，我们继续往下debug一次，看看在加第一把lock锁的时候，对应的四个属性如何变化的。

![1567994719249](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567994719249.png)

很明显，上图的结果是只有state和exclusiveOwnerThread设置了新的值，state从0变成1，而exclusiveOwnerThread变成了当前抢到锁的线程名字，我们继续加第二把锁：

![1567994832331](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567994832331.png)

state从1变成了2，这个含义就是当前线程持有的当前锁数目为2。继续下面的逻辑释放1把锁后：

![1567994900305](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567994900305.png)

state从2又减为1，那么当前exclusiveOwnerThread持有锁的线程名依然没变化。继续再释放最后一把锁：

![1567994961173](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567994961173.png)

最终恢复了没有加锁的状态，这个时刻代表当前线程全部释放了锁，现在的锁可以被其他的线程抢占了。



**上面的实例中，head和tail两个对象的值一直都是null，接下来就演示在多线程情况下对应的变化**

同样的，在main方法中重新new出5个线程，这里将aqsTest()中的逻辑修改为了讲解问题。

```java
public class AqsAnalysis {
    static ReentrantLock reentrantLock = new ReentrantLock();

    public static void aqsTest(){
        //尝试获取第一把锁，如果顺利的话，该线程将会拥有3把锁，要求释放3次！
        if(reentrantLock.tryLock()){
        reentrantLock.lock();
        System.out.println(Thread.currentThread().getName()+" 获取到第一把lock");
        //再次加锁
        reentrantLock.lock();
        System.out.println(Thread.currentThread().getName()+" 获取到第二把lock");
        //准备释放
        reentrantLock.unlock();
        System.out.println(Thread.currentThread().getName()+" 释放一把lock，当前线程持有该锁数目:"+reentrantLock.getHoldCount());
        reentrantLock.unlock();
        System.out.println(Thread.currentThread().getName()+" 释放一把lock，当前线程持有该锁数目:"+reentrantLock.getHoldCount());
               reentrantLock.unlock();
        System.out.println(Thread.currentThread().getName()+" 释放一把lock，当前线程持有该锁数目:"+reentrantLock.getHoldCount());
        }else{
			//获取到当前等待锁的线程数，我在这里打断点，要求条件满足等待锁的线程数大于0的时候再debug查看           
            reentrantLock.getQueueLength();
            reentrantLock.lock();
            System.out.println(Thread.currentThread().getName()+"终于抢到一把锁");
            reentrantLock.unlock();
            System.out.println(Thread.currentThread().getName()+"但是又释放了当前唯一的锁");
        }
    }

    public static void main(String[] args) {
        for(int i=0;i<5;i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    aqsTest();
                }
            }, "T"+i).start();
        }

    }
}

```

打一个条件断点在reentrantLock.getQueueLength()处，当等待队列的线程数大于0的时候，进入debug模式，这样可以更好的观测head，tail的具体情况。我们运行下代码观测。

![1567996156538](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567996156538.png)

看到几个框框处，head和tail终于有了值，而且state和exclusiveOwnerThread也有对应持有当前锁的线程，那么head和tail对应的数据结构是什么呢？他们都是Node这个数据结构的实例对象，由Node类构建CLH队列数据结构

```java
//等待队列中的节点，每一个线程获取锁冲突的时候，就会进入到AQS等待队列中，那么在进入队列之前必须要对其进行包装成一个Node，并且将其前驱指针prev指向tail指向的节点，tail将当前指向的节点的后续指针指向新入队的节点，并且利用cas操作将tail设置指向为新入对的节点。   
static final class Node {
        /** 表明当前节点是否是处于等待共享锁模式的 */
        static final Node SHARED = new Node();
        /**表明当前节点是否处于等待排他锁模式*/
        static final Node EXCLUSIVE = null;

        /** 以下几种都是当前节点所处于的状态 */
        //1. 表示当前节点已经不再竞争锁了，可以被清除出队列的状态
        static final int CANCELLED =  1;
        /**2. 表明后继节点可以被唤醒，处于准备争抢锁的状态 */
        static final int SIGNAL    = -1;
        /**3. 表明节点处于条件队列中 */
        static final int CONDITION = -2;
        /**
         * 传播模式
         */
        static final int PROPAGATE = -3;

        /**
         * Status field, taking on only the values:
         *   SIGNAL:     The successor of this node is (or will soon be)
         *               blocked (via park), so the current node must
         *               unpark its successor when it releases or
         *               cancels. To avoid races, acquire methods must
         *               first indicate they need a signal,
         *               then retry the atomic acquire, and then,
         *               on failure, block.
         *   CANCELLED:  This node is cancelled due to timeout or interrupt.
         *               Nodes never leave this state. In particular,
         *               a thread with cancelled node never again blocks.
         *   CONDITION:  This node is currently on a condition queue.
         *               It will not be used as a sync queue node
         *               until transferred, at which time the status
         *               will be set to 0. (Use of this value here has
         *               nothing to do with the other uses of the
         *               field, but simplifies mechanics.)
         *   PROPAGATE:  A releaseShared should be propagated to other
         *               nodes. This is set (for head node only) in
         *               doReleaseShared to ensure propagation
         *               continues, even if other operations have
         *               since intervened.
         *   0:          None of the above
         *
             上面4个值都是waitStatus可以设置的，非负数的值代表当前节点无需参与线程的竞争。
         */
        volatile int waitStatus;

        /**
     、   当前节点的前置节点，那么这个前置节点是个非cancel的节点，肯定会存在的，因为head节点是不会cancel的。在入队的时候为prev赋值，出队的时候就会变成null
         */
        volatile Node prev;

        /**
          当前节点的后继节点，如果为null则表示等等待队列中的最后一个节点。注意当节点为Cancel的时候是不会设置为null的，它还是以节点的方式存在队列中（一般在新加入节点的时候会扫描这种节点，并清理出队列）
         */
        volatile Node next;

        /**
         * 当前节点包装的线程，当入队的时候设置值，出队则变成null
         */
        volatile Thread thread;

        /**
          链接到正在等待状态的下一个节点，或共享的特殊值.  因为只有在独占模式下才能访问条件队列, 我们只需要一个简单的链式队列来在节点等待条件时保存节点.然后将它们转移到等待队列中重新获取锁.因为只有独占模式才会出现在条件队列，设置值的时候会设置一个特殊值表示这种情况。
         */
        Node nextWaiter;

        /**
         *返回当前节点是否处于共享模式下等待
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 返回当前节点的前置节点
         * @return the predecessor of this node
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        Node() {    //用于建立初始化head，tail等属性，共享模式下常用

        }

        Node(Thread thread, Node mode) {     // 添加下一个候选节点，指定等待模式
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // 指定等待状态创建一个节点
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }
```

对于Node实际上就是等待队列的一个节点，它拥有状态（waitStatus）去标记是否可以争抢锁，每个节点有前置和后置指针，直线前驱节点和后继节点，并且拥有是否式独占模式或者共享模式下等待的属性。

AQS同步队列中使用的数据结构实际上是基于CLH队列的：

![1567821952913](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567821952913.png)

上面图中**绿色的灯，可以理解成信号量，代表当前节点的状态**

LockSupport(Jni)去阻塞没有获取到锁的线程。

head和tail这两个属性是AQS等待队列的头节点和尾部节点。

```java
   //设置头部节点，只有调用acquire方法的时候才会调用此方法
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }
```

实际上，当线程获取锁的时候都会调用aquire方法，当获取不到锁就准备封装该线程为Node节点，并且检测是否head初始化了，如果没有，就执行setHead方法，将head和tail两个值指向setHead传入的Node节点（该节点不代表任何线程，只是用于标志头部）。再将线程的Node节点加入到等待队列即可。

![1567999186567](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567999186567.png)



知道了Node这一数据结构，我们再看上面debug的结果：

![1567996156538](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567996156538.png)

这里我直接用一张图表示上面的结果。

![1568009647023](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1568009647023.png)

AQS等待队列中有两个等待锁释放的线程T1和T2，当前锁被T0持有，state=1代表T0还持有1个锁，那么当lock锁完全被释放的时候，紧接着就会唤醒AQS等待队列的线程进行竞争锁。

**AQS是如何对抢不到锁的线程加入到等待队列的呢？**

这里就要涉及到加锁的方法啦，我们看到ReentraLock的lock方法内部原理，这里距离FairSync中的lock方法（公平锁）

```java
        //获取锁
        final void lock() {
            //比较和设置state，如果当前AQS中的state属性为0，就尝试的用cas改变为1，做了修改后将exclusiveOwnerThread属性设置为当前线程，表示这把锁被当前线程占有了。
            if (compareAndSetState(0, 1))
                
                setExclusiveOwnerThread(Thread.currentThread());
            //如果state！=0，表示可能有其他线程抢到了锁，也可能是当前线程已经获取了锁，但是还想要再获取的情况
            else
                //再次获取锁
                acquire(1);
        }

```

上面的lock方法调用了acquire并传入1，准备再次获取锁，先要进行检验，我们看下AQS中的acquire方法

```java
  public final void acquire(int arg) {
      //首先尝试的去加锁，如果成功了就立刻返回
        if (tryAcquire(arg))
            return;
      //如果加锁没有成功，就将当前节点加入到AQS等待队列的尾部
        if(acquireQueued(addWaiter(Node.EXCLUSIVE), arg)))
            //如果线程执行完毕后，要将线程中断。
            selfInterrupt();
    }
```

上面又分为两个步骤校验加锁还是加入等待队列

1. **tryAcquire校验是否可以为当前线程加锁**

```java
//非公平锁的tryAcquire方法
final boolean nonfairTryAcquire(int acquires) {     
       final Thread current = Thread.currentThread();
          //获取AQS的state值  
          int c = getState();
    	  //检测state值是否为0，如果为0，代表锁已经释放，或者没有线程持有锁，可以进行加锁
            if (c == 0) {
                //通过CAS设置state
                if (compareAndSetState(0, acquires)) {
                    //cas设置成功后立刻设置exclusiveOwnerThread为当前线程
                    setExclusiveOwnerThread(current);
                    return true;
                }
            }
     		//state！=0，说明这把锁可能被其他线程持有，校验当前线程等于持有锁的线程
            else if (current == getExclusiveOwnerThread()) {
                //如果当前线程是持有锁的线程，就要将当前线程已有锁的数目（state数）+acquires传入的数目
                int nextc = c + acquires;
                //如果小于0，就抛异常，因为持有锁数目要>=0
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                //设置state值
                setState(nextc);
                return true;
            }
    //如果state！=0且当前线程不是持有锁的线程，代表尝试加锁失败，非阻塞的返回false
            return false;
        }
```

2. **acquireQueued校验是否添加进AQS队列中排队**

```java
//以不可中断方式获取锁(独占模式)
   final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
     
            boolean interrupted = false;
           
            for (;;) {
                //获取当前节点的前置节点
                final Node p = node.predecessor();
                //检查是否是头节点，如果是头节点，并且尝试将锁获取到了，那就将头节点指向当前的node节点，并且将node代表的线程以及前驱prev置为null。
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    //node的前驱节点的后继引用设为空，帮助gc
                    p.next = null; 
                    failed = false;
                    return interrupted;
                }
                //如果不是头节点，或者说是头节点，但是锁没释放，尝试获取锁失败，就会调用shouldParkAfterFailedAcquire方法，依次的检查当前node节点的前驱节点，如果前驱节点p的waitStatus是处于signal的话，就代表要将当前节点挂起阻塞，如果不是，就判断是否是cancel状态，如果是就将p节点排出队列，并将node的前置指针指向p的前置节点，继续这样的waitStatus判断判断，直到判断出waitStatus<0，终止循环。并将当前遍历到的前置节点的后继指针next指向当前node。  
                //如果shouldParkAfterFailedAcquire判断确实要将当前线程阻塞，那就继续 调用parkAndCheckInterrupt方法，其内部主要是使用LockSupport类的park方法去将线程进行阻塞。使用到了UNSAFE类越过jvm直接操作底层硬件。
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    //成功的将当前线程阻塞挂起，并等待锁释放
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }



//为当前线程创建进队节点，分享模式 or 排他模式要在这指明  
private Node addWaiter(Node mode) {
        //外界传入是否是排他或者分享模式等候，为当前线程创建节点
        Node node = new Node(Thread.currentThread(), mode);
        
        Node pred = tail;
        //tail指针不为空，就将当前node节点的前驱指针等于tail指向的节点，并用cas修改tail指针将其指向为止为node节点处，最后将node指向的前驱节点的后继指针（next）指向node节点。返回node。这里就完成了入队操作
    	if (pred != null) {
            node.prev = pred;
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
    //tail指针为空，使用enq方法将node节点加入队列（这个方法是死循环，必成功）
        enq(node);
        return node;
    }

//初始化头尾部指针如果有必要的话
    private Node enq(final Node node) {
        for (;;) {
            Node t = tail;
            if (t == null) { 
                //如果尾部指针指向Null,将head指针初始化，指向一个节点对象(这个节点Thread为null)，然后将tail指向head所指向的节点
                if (compareAndSetHead(new Node()))
                    tail = head;
            } else {
                //将当前要加入的节点入队操作
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }


```

在acquireQueued方法中，会去尝试的获取锁，是个无线循环的方法，直到获取到锁之后，返回这期间（线程）是否被挂起过的标志（interrupt）。

下面图例更清楚的说明了线程是如何加入到AQS等待队列中去的。

![1567821884269](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567821884269.png)





### 条件队列

![1567827650100](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567827650100.png)

条件队列，当notFull 满的时候，notFull队列会发一个信号给notEmpty队列，要求它尽快的进行**消费**，然后notEmpty队列收到这个信号后，就会去通知同步队列，并取出一个节点到同步队列加到其尾部，在这个过程中，会取扫描所有的同步队列的节点，判断WaitStatus的值是否等于Cancel，如果有该节点，将会进行清除出同步队列，剩余的节点准备竞争锁。这有点类似于生产消费者模式。

为什么会出现同步队列和条件队列呢？ 原因是当线程没有得到锁的时候，就会进入到条件队列中等待，条件队列并不会进行动态扩容，当条件队列节点数占满后，这时候必须要去通知同步队列（相当于消费者），去将条件队列中的节点加入到同步队列中消费（也就是准备争抢锁执行逻辑），如果没条件队列，线程的竞争将会加大。



