### 多线程学习（十六）---- Semaphore 实现流控

Semaphore这个类主要是用来限流的。

什么意思呢？你可以看下面的这张图例

![1567579211884](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567579211884.png)

假设Semaphore定义了4个**令牌**在它的内部 。 令牌的含义就是，每个线程只有拿到令牌才可以进行业务的处理。比如现在有9个线程，同时想要去争抢Semaphore中空闲的令牌，那么只有4个线程能够抢到令牌，剩下的5个线程就会阻塞并等待令牌的释放，当令牌有空闲的时候，就会唤醒所有在等待令牌的线程，又加入到新一轮竞争中。

acquire（）：获取Semaphore中的令牌

release（）：释放线程持有的令牌。

Semaphore在初始化的时候就指定了令牌的数目，令牌的数目的多少就代表着有多少个线程在同一时间内能够获取并处理资源。换句话说就是同一时间内允许的最大流量数目（令牌数）

**Semaphore的类图**

![Semaphore](https://gitee.com/alan-tang-tt/yuan/raw/master/%E6%AD%BB%E7%A3%95%20java%E5%90%8C%E6%AD%A5%E7%B3%BB%E5%88%97/resource/Semaphore.png)

Semaphore中包含了一个实现了AQS的同步器Sync，以及它的两个子类FairSync和NonFairSync，这说明Semaphore也是区分公平模式和非公平模式的。

**Semaphore源码解读**

* **内部类Sync**

```java
// java.util.concurrent.Semaphore.Sync
abstract static class Sync extends AbstractQueuedSynchronizer {
    private static final long serialVersionUID = 1192457210091910933L;
    // 构造方法，传入许可次数，放入state中
    Sync(int permits) {
        setState(permits);
    }
    // 获取许可次数
    final int getPermits() {
        return getState();
    }
    // 非公平模式尝试获取许可
    final int nonfairTryAcquireShared(int acquires) {
        for (;;) {
            // 看看还有几个许可
            int available = getState();
            // 减去这次需要获取的许可还剩下几个许可
            int remaining = available - acquires;
            // 如果剩余许可小于0了则直接返回
            // 如果剩余许可不小于0，则尝试原子更新state的值，成功了返回剩余许可
            if (remaining < 0 ||
                compareAndSetState(available, remaining))
                return remaining;
        }
    }
    // 释放许可
    protected final boolean tryReleaseShared(int releases) {
        for (;;) {
            // 看看还有几个许可
            int current = getState();
            // 加上这次释放的许可
            int next = current + releases;
            // 检测溢出
            if (next < current) // overflow
                throw new Error("Maximum permit count exceeded");
            // 如果原子更新state的值成功，就说明释放许可成功，则返回true
            if (compareAndSetState(current, next))
                return true;
        }
    }
    // 减少许可
    final void reducePermits(int reductions) {
        for (;;) {
            // 看看还有几个许可
            int current = getState();
            // 减去将要减少的许可
            int next = current - reductions;
            // 检测举出
            if (next > current) // underflow
                throw new Error("Permit count underflow");
            // 原子更新state的值，成功了返回true
            if (compareAndSetState(current, next))
                return;
        }
    }
    // 销毁许可
    final int drainPermits() {
        for (;;) {
            // 看看还有几个许可
            int current = getState();
            // 如果为0，直接返回
            // 如果不为0，把state原子更新为0
            if (current == 0 || compareAndSetState(current, 0))
                return current;
        }
    }
}
```

通过Sync的几个实现方法，我们获取到以下几点信息：

（1）许可是在构造方法时传入的；

（2）许可存放在状态变量state中；

（3）尝试获取一个许可的时候，则state的值减1；

（4）当state的值为0的时候，则无法再获取许可；

（5）释放一个许可的时候，则state的值加1；

（6）许可的个数可以动态改变；



* **内部类NonfairSync**

```java
// java.util.concurrent.Semaphore.NonfairSync
static final class NonfairSync extends Sync {
    private static final long serialVersionUID = -2694183684443567898L;
    // 构造方法，调用父类的构造方法
    NonfairSync(int permits) {
        super(permits);
    }
    // 尝试获取许可，调用父类的nonfairTryAcquireShared()方法
    protected int tryAcquireShared(int acquires) {
        return nonfairTryAcquireShared(acquires);
    }
}
```

非公平模式下，直接调用父类的nonfairTryAcquireShared()尝试获取许可。

* **内部类FairSync**

```java
// java.util.concurrent.Semaphore.FairSync
static final class FairSync extends Sync {
    private static final long serialVersionUID = 2014338818796000944L;
    // 构造方法，调用父类的构造方法
    FairSync(int permits) {
        super(permits);
    }
    // 尝试获取许可
    protected int tryAcquireShared(int acquires) {
        for (;;) {
            // 公平模式需要检测是否前面有排队的
            // 如果有排队的直接返回失败
            if (hasQueuedPredecessors())
                return -1;
            // 没有排队的再尝试更新state的值
            int available = getState();
            int remaining = available - acquires;
            if (remaining < 0 ||
                compareAndSetState(available, remaining))
                return remaining;
        }
    }
}
```

公平模式下，先检测前面是否有排队的，如果有排队的则获取许可失败，进入队列排队，否则尝试原子更新state的值。



```java
/**

   一个计数的Semaphore，概念上而言，它维护了一组令牌，当线程进行acquire的时候，如果没有令牌剩余，就会阻塞线程，直到有为止。每一次调用release方法都会增加一个令牌（permit）数量，并且请求令牌者中将有一个线程得到这个令牌，semaphore内部只是简单的定义了一个int类型的计数器维护数目。

    Semaphore通常用于约束一组线程，去访问某些资源。例如下面实例代码：
 * class Pool {
 *   private static final int MAX_AVAILABLE = 100;
 *   private final Semaphore available = new Semaphore(MAX_AVAILABLE, true);
 *
 *   public Object getItem() throws InterruptedException {
 *     available.acquire();
 *     return getNextAvailableItem();
 *   }
 *
 *   public void putItem(Object x) {
 *     if (markAsUnused(x))
 *       available.release();
 *   }
 *   
 *   protected Object[] items = ... whatever kinds of items being managed
 *   protected boolean[] used = new boolean[MAX_AVAILABLE];
 *
 *   protected synchronized Object getNextAvailableItem() {
 *     for (int i = 0; i < MAX_AVAILABLE; ++i) {
 *       if (!used[i]) {
 *          used[i] = true;
 *          return items[i];
 *       }
 *     }
 *     return null; // not reached
 *   }
 *
 *   protected synchronized boolean markAsUnused(Object item) {
 *     for (int i = 0; i < MAX_AVAILABLE; ++i) {
 *       if (item == items[i]) {
 *          if (used[i]) {
 *            used[i] = false;
 *            return true;
 *          } else
 *            return false;
 *       }
 *     }
 *     return false;
 *   }
 * }}</pre>
 *
   上面代码展示了在获取item的时候线去acquire一张令牌，检查是否还有item可用，如果有可用的item那就将对应下标的use置为true。释放item的时候将use[i]置为false并且释放该线程中的令牌。
 *
   在构造Semaphore的时候，可以额外传入fairness是否为true，如果是false，采用的是非公平模式获取令牌，这样的化就不保证顺序获取，线程之间可以进行争抢。
   如果fairness设置为true，则会采用先进先出的策略挑选等待线程取取获取令牌
   如果在fairness设置为true的时候，又调用了tryAcquire方法取获取令牌，拿将不会保证顺序获取。也就是打断了原本先进先得的顺序
   默认情况下，Semaphore设置公平模式，去避免有线程访问不了资源的情况（线程饥饿）
   如果想要使用Semaphore取进行某种同步控制，那么将fairness设置为非公平模式，将会比公平模式更有价值。
 *
 * @since 1.5
 * @author Doug Lea
 */
public class Semaphore implements java.io.Serializable {
    //permit：令牌数，  fair：是否采用公平模式 
    public Semaphore(int permits, boolean fair) {
        sync = fair ? new FairSync(permits) : new NonfairSync(permits);
    }
    //默认情况下，不传入fair参数则构造的是非公平获取令牌
    public Semaphore(int permits) {
        sync = new NonfairSync(permits);
    }
    /**
    获取令牌，如果有令牌，则获取。如果没有就阻塞当前线程，直到令牌被其他线程释放，或者该线程被interrupt。
    */
    public void acquire() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }
    //获取指定数目的令牌,内部线程的阻塞是可以被其他线程中断的
	public void acquire(int permits) throws InterruptedException {
        if (permits < 0) throw new IllegalArgumentException();
        sync.acquireSharedInterruptibly(permits);
    }
    
    //缩减Semaphore中存放令牌的数量，这个方法和acquire方法不一样，它不会等待令牌的释放，而是将令牌总数直接减少。
        protected void reducePermits(int reduction) {
        if (reduction < 0) throw new IllegalArgumentException();
        sync.reducePermits(reduction);
    }
    
     /**
     * 释放指定数量的令牌到Semaphore对象中
     * @param permits the number of permits to release
     * @throws IllegalArgumentException if {@code permits} is negative
     */
    public void release(int permits) {
        if (permits < 0) throw new IllegalArgumentException();
        sync.releaseShared(permits);
    }
    
    //释放一个许可，释放一个许可时state的值会加1，并且会唤醒下一个等待获取许可的线程。
     public void release() {
        sync.releaseShared(1);
    }
    //尝试获取一个许可，使用Sync的非公平模式尝试获取许可方法，不论是否获取到许可都返回，只尝试一次，不会进入队列排队。
    public boolean tryAcquire() {
    return sync.nonfairTryAcquireShared(1) >= 0;
   }
    //一次尝试获取多个许可，只尝试一次。
    public boolean tryAcquire(int permits) {
    if (permits < 0) throw new IllegalArgumentException();
    return sync.nonfairTryAcquireShared(permits) >= 0;
}
    //尝试获取多个许可，并会等待timeout时间，这段时间没获取到许可则返回false，否则返回true。
    public boolean tryAcquire(int permits, long timeout, TimeUnit unit)
    throws InterruptedException {
    if (permits < 0) throw new IllegalArgumentException();
    return sync.tryAcquireSharedNanos(permits, unit.toNanos(timeout));
}
 
    //一次释放多个许可，state的值会相应增加permits的数量。
    public void release(int permits) {
    if (permits < 0) throw new IllegalArgumentException();
    sync.releaseShared(permits);
}
    //销毁当前可用的许可次数，对于已经获取的许可没有影响，会把当前剩余的许可全部销毁。
    public int drainPermits() {
    return sync.drainPermits();
}
}
```

## 总结

（1）Semaphore，也叫信号量，通常用于控制同一时刻对共享资源的访问上，也就是限流场景；

（2）Semaphore的内部实现是基于AQS的共享锁来实现的；

（3）Semaphore初始化的时候需要指定许可的次数，许可的次数是存储在state中；

（4）获取一个许可时，则state值减1；

（5）释放一个许可时，则state值加1；

（6）可以动态减少n个许可；



**答疑**

（1）如何动态增加n个许可？

答：调用release(int permits)即可。我们知道释放许可的时候state的值会相应增加，再回头看看释放许可的源码，发现与ReentrantLock的释放锁还是有点区别的，Semaphore释放许可的时候并不会检查当前线程有没有获取过许可，所以可以调用释放许可的方法动态增加一些许可。

（2）如何实现限流？

答：限流，即在流量突然增大的时候，上层要能够限制住突然的大流量对下游服务的冲击，在分布式系统中限流一般做在网关层，当然在个别功能中也可以自己简单地来限流，比如秒杀场景，假如只有10个商品需要秒杀，那么，服务本身可以限制同时只进来100个请求，其它请求全部作废，这样服务的压力也不会太大。



**案例实现**

现在有一个场景，要求你模拟抢红包场景，现在有一个红包类，库存只有5件，但是又100个并发的请求，同一时可想要进行抢购红包。要求你限制流量在40个请求。以减少服务器处理压力。如何实现呢？

**分析**

* 将100个请求限制流量在40左右，我们可以定义一个拥有40张令牌的Semapohre。
* 为了体现同时进行争抢令牌，使用CountDownLatch统一控制。
* 在修改库存的时候，使用Atomic类利用cas操作修改库存。
* 在并发操作时，进行加锁。



红包类

```java
//红包类
public class RedEvelope {
    //红包剩余量
    private int remain;

    public RedEvelope(int remain) {
        this.remain = remain;
    }
    
    public int getRemain() {
        return remain;
    }

    public void setRemain(int remain) {
        this.remain = remain;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o){
            System.out.println(this+"=="+o);
            if(o instanceof RedEvelope){
                RedEvelope instance = (RedEvelope) o;
                if(this.getRemain() == instance.getRemain()){
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(remain);
    }
}

```

抢红包处理业务

```java
//模拟抢红包业务
public class MockToGrabRedEnvelope {
    private static final int PERMITS_NUMBER = 40;
    //控制客户线程并发执行的锁
    static final CountDownLatch customerCtrl = new CountDownLatch(1) ;
    static final Semaphore semaphore = new Semaphore(PERMITS_NUMBER);
    private static AtomicReference<RedEvelope> atomicReference;
    static int  counter =0;

    public MockToGrabRedEnvelope(AtomicReference<RedEvelope> atomicReference) {
        this.atomicReference = atomicReference;
    }

    private static class CustomerThread extends Thread{
        @Override
        public void run() {
            System.out.println(Thread.currentThread().getName() + " want to grab a red envelope ....  ");
            try {
                //等待开锁
                customerCtrl.await();
                //开锁后想要获取令牌,不阻塞获取，如果获取不到将过滤请求
                if(semaphore.tryAcquire()){
                    System.out.println(Thread.currentThread().getName()+" catch a permit and begin process grab red envelope ..");
                    doGrabRedEvelope();
                    //释放令牌
                    semaphore.release();
                }
                else{
                    System.out.println(Thread.currentThread().getName()+"cannot catch a permit .. ");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    //做一些抢红包业务操作
    public static void doGrabRedEvelope() throws InterruptedException {
        //使用cas操作，修改红包数
        RedEvelope redEvelope = atomicReference.get();
        int remain = redEvelope.getRemain();
        RedEvelope newRedEvelope = new RedEvelope(remain-1);

        //每个线程一进来就会判断只要remain>0就代表还有红包剩余，那就会进行cas尝试修改
        while(remain>0){
            Thread.sleep(2);
            //如果remain大于0，那就尝试使用cas修改
            if(atomicReference.compareAndSet(redEvelope,newRedEvelope)){
                //如果修改成功，就代表抢到红包，打印
                counter++;
                System.out.println(Thread.currentThread().getName()+":"+atomicReference.get().getRemain());
                //结束此方法
                return;
            }
            else{
                //否则的话说明有人修改了，修改失败
                System.out.println(Thread.currentThread().getName()+" revise false，some others have revised！");
                //重新再获取一次，准备第2次尝试。
                redEvelope = atomicReference.get();
                remain = redEvelope.getRemain();
                newRedEvelope = new RedEvelope(remain-1);
            }
        }
        //如果时while循环正常退出，说明没有执行return；也就是没有抢到红包。
            System.out.println(Thread.currentThread().getName()+" grab red envelope fail!" + atomicReference.get().getRemain());
    }


    public static void main(String[] args) throws InterruptedException {
        MockToGrabRedEnvelope m = new MockToGrabRedEnvelope(new AtomicReference<>(new RedEvelope(5)));
        for(int i=0;i<100;i++){
            CustomerThread customerThread = new CustomerThread();
            customerThread.start();
        }
        Thread.sleep(200);
        //统一放行
        customerCtrl.countDown();
        Thread.sleep(5000);
        System.out.println(counter);
    }
}

```

运行上面的业务，我们可以发现结果：
![1567664826658](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567664826658.png)

只截取了一部分，上面大概的意思时，先让线程在获取令牌的时候打印一句话，然后等待main线程的CountDownLatch指令，当收到了这个指令的时候，就会将线程释放，那么释放的这些线程又会进入下一轮争抢Semaphore令牌的竞争，抢到令牌的线程开始处理自己的红包业务，与此同时，没有抢到的线程将会结束。

红包业务采用了原子操作Cas，那么在CAS操作前要获取原先的红包数量，如果红包数量大于0，那就尝试的进行cas操作，如果不大于0，则直接退出。在cas操作修改红包数目的时候，如过返回的时false，代表红包数目提前被别的线程修改，那就重新获取并重新判断剩余值是否还大于0.同样重复这套流程。