### 多线程学习（十五） ---- CountDownLatch和CyclicBarrier

*** 第一模块： CountDownLatch**

这个类有点类似于计时器的使用，当计时器结束时，开始做某些业务。

> CountDownLatch 的作用有点类似于加强版本的join方法，要等到某些步骤全部执行完毕后，才会继续执行剩下的业务逻辑。

我们先给出一张CountDownLatch的作用图

![1567565606423](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567565606423.png)

上面的图例中，有两个工作县城TW1和TW2准备想要执行一些业务，但是在执行业务前，他们必须要等待CountDownLatch的放行。也就是说，此时在他们执行业务前，如果还有其他线程的初始化工作没有完成的话，通过CountDownLatch就会检测到初始化步骤还剩下多少个（CNT就是记录这些初始化步骤的个数的），而图中的Ta，Tb , Tc , Td 时四个用于初始化工作的线程，其中Td任务繁重，需要负责两个初始化工作。而每当他们完成一次初始化工作，就要调用CountDownLatch对象的countDown()方法去将CNT的值减1，告诉CountDownLatch对象，已经干掉一个初始化工作了！ 那么当CNT等于0的时候，CountDownLatch就会检测到，并且唤醒等待中的TW1和TW2工作线程，告诉他们，你们可以干自己的任务了，就将这两个线程同时放行去处理任务。

> 经常使用CountDownLatch去记录一些业务的开始的时候，所需要完成的前置步骤的个数，注意，CountDownLatch中的CNT，不代表执行初始化工作的线程数！只是简单的记录初始化步骤的次数！



**CounDownLatch源码解读**

![1567566613688](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567566613688.png)

CountDownLatch内部也使用Sync内部类来完成一些加锁解锁的动作，时基于AQS的实现。那么上图展示了CountDownLatch的大致方法。我们看几个重要的代码实现

```java
/**
   CountDownLatch的初始化必须传入一个count，你可以理解为初始化工作步骤数，await()方法的调用回阻塞当前的线程，直到countDown()方法被其他线程调用直至为0的时候，就会立刻同时释放所有处于wait状态的等待该CountDownLatch对象的线程。这个过程只有一次，并且count不能被重新设置，如果你想要重新设置count，可以考虑使用CyclicBarrier类。
 * @since 1.5
 * @author Doug Lea
 */
public class CountDownLatch {
   //这个类使用AQS维护了count，也就是CountDownLatch的计数器
    private static final class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 4982264981922014374L;

        Sync(int count) {
            setState(count);
        }
		//拿到当前的state，也就是计数器
        int getCount() {
            return getState();
        }
		//尝试获取锁
        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }
        //尝试释放唤醒等待线程，并将count -1 
        protected boolean tryReleaseShared(int releases) {
            // Decrement count; signal when transition to zero
            for (;;) {
                int c = getState();
                if (c == 0)
                    return false;
                int nextc = c-1;
                if (compareAndSetState(c, nextc))
                    return nextc == 0;
            }
        }
    }
    
    private final Sync sync;
    //构造方法，使用Sync的SetState方法利用AQS维护这个count状态
    public CountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException("count < 0");
        this.sync = new Sync(count);
    }
    
    //对当前的count-1
    public void countDown() {
        sync.releaseShared(1);
    }
    
    //获取当前的count
     public long getCount() {
        return sync.getCount();
    }
    
    //await调用回将当前线程阻塞，并等待count为0，如果超时了就会自动唤醒
    public boolean await(long timeout, TimeUnit unit)
        throws InterruptedException {
        return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }
     //await调用回将当前线程阻塞，并等待count为0
      public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

}
```

其实也很简单，在别的线程调用countDown的时候，每次调用都会去检测count的数量，如果是0那么就释放唤醒等待的线程，如果不是那就让线程继续等待，如果等待的线程设置了时间，当等待时间超时，会自动的醒来。我们看看几个用到CountDownLatch类的实际例子

**案例：**

* **可以用于做压力测试**

压测主要是同一时间并发多个请求访问某个业务，看下系统的抗压能力如何，来测试性能。那么要达到同一时间释放多个线程处理某个业务，就要使用CountDownLatch去实现。这里先说明下案例：

**实现要求：**

现在需要几个压力测试线程，以及1个检测压力测试是否完成的线程（用于统计压测时间），那么压测线程在检测压力测试线程没有法出继续执行命令的时候，所有压测线程都需要等待命令的发布，而检测压力测试线程需要等待最后一个压测线程测试结束，将测试耗时打印。

```java
public class CountDownLatchTest {
    //控制压测线程执行压测的锁
    static CountDownLatch countDownLatch =new CountDownLatch(1);

    //压测线程
    private static class PressThread implements Runnable{
        CountDownLatch finishDownLatch ;
        
        public PressThread(CountDownLatch finishDownLatch){
            this.finishDownLatch = finishDownLatch;
        }

        @Override
        public void run() {
            //压力测试
            System.out.println(Thread.currentThread().getName()+" begin to doPressTest....");
            try {
                //一直等待锁的释放,如果准备工作线程没做完的话
                countDownLatch.await();
                System.out.println(Thread.currentThread().getName()+" do pressTest");
                //将等待做完压力测试的CountDownLatch对象的步骤减1
                finishDownLatch.countDown();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    //定义另外一个用来探测压力测试是否做完的线程
    private static class CheckFinishPressTestThread implements Runnable{
        CountDownLatch finishDownLatch ;

        public CheckFinishPressTestThread(CountDownLatch finishDownLatch){
            this.finishDownLatch = finishDownLatch;
        }

        @Override
        public void run() {
            System.out.println("now check finish do press ..");
            try {
                //放行压测线程，开始计时
                countDownLatch.countDown();
                //等待压力测试线程测试完毕
                long start = System.currentTimeMillis();
                finishDownLatch.await();
                System.out.println("now  all pressThreads are done! spend time: "+( System.currentTimeMillis()- start));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        int pressThreadNums = 10;
        CountDownLatch finishPressCountDownLatch = new CountDownLatch(pressThreadNums);
        //探测是否完成压力测试线程
        Thread finishPressTestThread = new Thread(new CheckFinishPressTestThread(finishPressCountDownLatch));
        //探测压力测试线程
        Thread pressThread[] = new Thread[pressThreadNums];
        for (int i = 0; i < pressThread.length; i++) {
            pressThread[i] = new Thread(new PressThread(finishPressCountDownLatch));
            pressThread[i].start();
        }
        //启动压力测试线程和探测压力测试线程，压力测试线程必须都要在探测压力测试线程启动后才可以启动！
        try {
            //等待所有的压力测试线程启动，并等候countDownLatch的放行，先让主线程休息20ms
            Thread.sleep(20);
            finishPressTestThread.start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

```

结果：

![1567577829152](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567577829152.png)

很清楚，压测线程创建了10个，而检测压测是否完成的线程只有1个，并且上面程序在检测线程启动之前，对主线程休眠20ms，让所有的压测线程都进入等待状态，再同一时刻将锁释放（countDown()） 。并记录起始终止时间。当所有的压测线程结束，才会打印出最后一句话。



*** 第二模块 CyclicBarrier**

CyclicBarrier其实和CountDownLatch很相似，它主要是基于一种线程屏障的实现方式来阻塞和唤醒线程的。

**如何理解屏障？**

如下图：

![1567578415403](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567578415403.png)

假设有Ta,Tb,Tc三个线程，红色的竖线我们可以理解为一个屏障，当着三个线程其中之一走到红色竖线处，就相当于到达了屏障，检测是否其余两个线程全部到达这个屏障处，如果全部到达，才可以继续业务处理，如果没有全部到达屏障处，那就要阻塞等待所有线程到达。 

**在代码层面上理解屏障含义**

![1567580126014](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567580126014.png)

我们线看下CyclicBarrier的源码：

```java
/**

   这个类可以让多个线程去等待彼此去到达屏障带你，CyclicBarriers构造的时候就指定了固定大小的线程到达屏障的时候，就释放屏障。为什么称其为可循环的呢？主要是它在哪些等待线程被释放的时候，还可以再进行await，并且可以再被barrierAction（其实是个Runnable）统计处理。CyclicBarrier支持传入Runnable实现类，可以对屏障处的线程做些额外的统计工作（这个工作在屏障没放行之前完成）
   当然你可以选择哪个线程可以应用BarrierAction进行执行统计
 * if (barrier.await() == 0) {
 *   // log the completion of this iteration
 * }}
 *
  如果当前到达屏障的线程其中之一，因为等待超时，提前终止，或者被中断的情况下。其他线程也会因为它退出等待。这是CyclicBarrier采用了all-or-none 的原则。
 * @since 1.5
 * @see CountDownLatch
 *
 * @author Doug Lea
 */
public class CyclicBarrier {
     /** The lock for guarding barrier entry */
    private final ReentrantLock lock = new ReentrantLock();
    /** Condition to wait on until tripped */
    private final Condition trip = lock.newCondition();
    /** The number of parties */
    private final int parties;
    /* The command to run when tripped */
    private final Runnable barrierCommand;
    /** The current generation */
    private Generation generation = new Generation();
     /**
      这个是现在还在等待线程到达屏障的数目
     */
    private int count;
    
    
      /**
     * 屏障将会被出发当等待的线程数量到达parties指定的数量的时候
     *
     * @param 调用该CyclicBarrier对象的await方法进入进入等待状态的线程数目
     * @param 如果屏障被出发就会调用barrierAction，如果没有这个Action可以传入null
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.count = parties;
        this.barrierCommand = barrierAction;
    }
    
 
    
    /**
      重置屏障状态，所有等待屏障的线程会收到BrokenBarrierException异常
     */
    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            breakBarrier();   // break the current generation
            nextGeneration(); // start a new generation
        } finally {
            lock.unlock();
        }
    }
    
     /**
     * 更新当前的屏障，并且唤醒所有等待的线程
     */
    private void nextGeneration() {
        // signal completion of last generation
        trip.signalAll();
        // set up next generation
        count = parties;
        generation = new Generation();
    }
    
    
       /**
     * 如果当前线程不是最后一个到达的，发生下面几种状况之一可以终止阻塞：
        1. 直到最后一个线程到达。然后在放行前，会调用传入的barrierAction(Runnable)中的run方法。
           执行完毕后并且没问题后，放行
        2. 其他线程对当前线程调用interrupt方法（如果其中任何等待屏障的线程被中断，其他所有线程都会抛出BrokenBarrierException异常
        3. 等待线程如果设置了超时，超过时间后停止等待。
        4. 其他线程调用了reset方法重置该屏障。（如果调用reset方法后，如果有线程等待以前的barrier释放，他们将会收到BrokenBarrierException
  
     * @return 返回当前是否是最后一个线程到达屏障，如果是0则是最后一个线程到达屏障，否则就不是
     * @throws InterruptedException if the current thread was interrupted
     *         while waiting
     * @throws BrokenBarrierException if <em>another</em> thread was
     *         interrupted or timed out while the current thread was
     *         waiting, or the barrier was reset, or the barrier was
     *         broken when {@code await} was called, or the barrier
     *         action (if present) failed due to an exception
     */
       public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen
        }
    }
    /**
      每个线程到达屏障的时候都会执行这个方法，owait内部会检测是否是计时模式，false代表不计时，然后会将count--，并且会检测当前的屏障（generation）的状态是否处于breaken，线程是否被终止。如果count--完毕，会检测是否为0，如果为0就执行barrierAction的run方法，并signall唤醒所有线程。并且重置屏障（count值会进行初始化成原来的值)
     */
    private int dowait(boolean timed, long nanos)
        throws InterruptedException, BrokenBarrierException,
               TimeoutException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final Generation g = generation;

            if (g.broken)
                throw new BrokenBarrierException();

            if (Thread.interrupted()) {
                breakBarrier();
                throw new InterruptedException();
            }

            int index = --count;
            if (index == 0) {  // tripped
                boolean ranAction = false;
                try {
                    final Runnable command = barrierCommand;
                    if (command != null)
                        command.run();
                    ranAction = true;
                    nextGeneration();
                    return 0;
                } finally {
                    if (!ranAction)
                        breakBarrier();
                }
            }

            // loop until tripped, broken, interrupted, or timed out
            for (;;) {
                try {
                    //如果非计时
                    if (!timed)
                        //阻塞等待放行
                        trip.await();
                    else if (nanos > 0L)
                        nanos = trip.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    //在等待的过程中，如果收到异常将终止等待并抛出异常，并且充值屏障状态
                    if (g == generation && ! g.broken) {
                        breakBarrier();
                        throw ie;
                    } else {
                        // 就算不是当前线程照成的异常，也要将它中断
                        Thread.currentThread().interrupt();
                    }
                }
				
                //线程被唤醒，会检测屏障是否break，如果是就抛异常
                if (g.broken)
                    throw new BrokenBarrierException();
				//如果当前的屏障版本不一致，将会返回之前的index（也就是剩余屏障等待线程数）
                if (g != generation)
                    return index;	
				//如果是timeout唤醒的，要将屏障重置！breakBarrier时，会将所有线程唤醒，然后再一次循环检测原因
                if (timed && nanos <= 0L) {
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
```

上面简单的讲解了一些主要的使用方法，最重要的还是await()的这个方法，以及doawait()讲解了每个线程在内部是以何种方式去等待屏障状态的，以及种种可能的结果，又是如何重置屏障状态的。



那么如何使用这个类去做一些事情呢？下面就给出一个案例

**案例：**

现在有个需求： 你的上司在接到产品那边的需求时，想要你实现这样的功能：
有N 个客户，同时购买了很多商品，假设你只需要统计这N个客户中，谁购买的商品总价最高，并将它的名字和购买的商品价格打印出来，商城想要对他进行回馈奖励。这里，每N个客户为1组，那么我就要统计这一组中商品总价最高的那位客户。如何实现呢？

**分析**

客户同时付款，我想要获得总数为N的客户中最高的价格的那位客户，就要定义一个屏障，也就是拦截N个客户，并且设置一个barrierAction，进行一些统计工作。

**实现代码：**

* 定义一个客户类

```java
//顾客类
public class Customer {
    //用户唯一标志
    private String uid;
    //购买物品的总价
    private int price;

    public Customer() {
    }

    public Customer(String uid, int price) {
        this.uid = uid;
        this.price = price;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    @Override
    public String toString() {
        return "Customer{" +
                "uid='" + uid + '\'' +
                ", price=" + price +
                '}';
    }
}
```

* 定义比较客户价格的类（相当于排序器，按照price从大到小排序）

```java
public class CustomerComparator implements Comparator<Customer> {

    @Override
    public int compare(Customer o1, Customer o2) {
        return (o1.getPrice()>o2.getPrice()? -1:(o1.getPrice()<o2.getPrice()?1:0));
    }
}

```

* 定义CyclicBarrier处理类

```java
//使用CyclicBarrier处理流程
public class CyclicBarrierProcess {
    private static CyclicBarrier cyclicBarrier ;
    //统计客户购买物品总价的队列
    private static List<Customer> statisticList ;
    public CyclicBarrierProcess(CyclicBarrier cyclicBarrier,List<Customer> statisticList) {
        this.cyclicBarrier = cyclicBarrier;
        this.statisticList = statisticList;
    }

    //客户购买商品的线程，模拟购物
    private static class MockCustomShopping implements Runnable{
        @Override
        public void run() {
            //设置客户id，以及随机设置购买的物品价格

            Random random;
            Customer customer;
            random = new Random();
            customer = new Customer(UUID.randomUUID().toString(), random.nextInt(10000));
            System.out.println(customer.getUid()+"begin to wait ...");
            //收集等待其余客户的数据
            statisticList.add(customer);
            try {
                cyclicBarrier.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (BrokenBarrierException e) {
                e.printStackTrace();
            }
            System.out.println(customer.getUid()+"begin do other things ...");
        }
    }


    //设置一个统计客户数据的线程
    private static class StatisticCustomerInfo implements Runnable{
        @Override
        public void run() {
            //对数组的元素从高到底排序
            if(!statisticList.isEmpty()){
                statisticList.sort(new CustomerComparator());
                System.out.println("now the result is:");
                for(int i=0;i<statisticList.size();i++){
                    System.out.println(statisticList.get(i));
                }
                System.out.println("最高排名:"+statisticList.get(0));
            }
        }
    }


    public static void main(String[] args) {
        int customerNumber = 20;
        new CyclicBarrierProcess(new CyclicBarrier(customerNumber,new StatisticCustomerInfo()),new ArrayList<Customer>());
        //创建20个客户线程
        for(int i=0;i<20;i++){
            new Thread(new MockCustomShopping()).start();
        }

    }
}

```

上面就实现了一个CyclicBarrier的一个用例，创建20个客户模拟购物并将数据利用一个List存放，当所有线程到达屏障的时候，准备调用barrierAction（StatisticCustomerInfo）中的run方法。并且将排名和最高排名者信息打印，完毕后，cyclicBarrier就对所有被阻塞的线程唤醒，继续执行。



**如何体现Cyclic复用这个功能呢？**

在上面的客户线程中，为了测试这个功能，你可以在await后，打印处do other things后再一次进行await，然后打印处do other thinsg 2nd ,然后观测执行结果

```java
//使用CyclicBarrier处理流程
public class CyclicBarrierProcess {
   
    //客户购买商品的线程，模拟购物
    private static class MockCustomShopping implements Runnable{
        @Override
        public void run() {
            //设置客户id，以及随机设置购买的物品价格

            Random random;
            Customer customer;
            random = new Random();
            customer = new Customer(UUID.randomUUID().toString(), random.nextInt(10000));
            System.out.println(customer.getUid()+"begin to wait ...");
            //收集等待其余客户的数据
            statisticList.add(customer);
            try {
            cyclicBarrier.await();
            System.out.println(customer.getUid()+"begin do other things ...");
            //再次await，
            cyclicBarrier.await();
            System.out.println(customer.getUid()+"begin do other things 2nd ...");
                
             } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (BrokenBarrierException e) {
                e.printStackTrace();
            }
        }
    }
}

```

结果：

![结果](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567586467503.png)



上面的结果显然，在第2次await，当所有线程第2次到达屏障的时候，又执行了一次Runnable统计逻辑。这个功能可以用于分阶段性对某组数据统计分类。这就是cyclic的概念，哪怕你再await  n 次 ，它也会调用Runnable逻辑 n 次 然后再放行。

为什么会出现这个现象，其实就是doawait方法内部，在上一次放行的时候，又重新newGeneration（），而这个方法的内部会重置屏障状态，并且重置屏障等待线程计数器（count) 。这样的化就相当于你再次await的时候，会再次到达这个新的屏障，所以就会触发Runnable执行任务。

