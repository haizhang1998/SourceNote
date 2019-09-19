### 多线程学习（十）---- 显示锁ReadWriteLock

上一张讲到了可重入锁ReentraLock，而我们经常使用的内置锁synchronized，它也是属于可重入锁。那么他们都有另外的一个称号就是 **排他锁**。

**排他锁的概念：**

意思是，当一个线程获取到排他锁之后，其他的线程都不能对其进行访问，阻塞等待锁的释放，这个锁就是称为排他锁。

试想一个读多写少的场景，使用排他锁还合适吗？

当然是不合适的，对于读数据，我们希望多个线程都可以一起读，这样提高了程序的效率，而如果用排他锁的话，就会让多个读线程进行争抢这把锁，还会阻塞没有抢到锁的线程。既然都是读操作，不会对数据进行修改，我们其实没有必要使用排他锁。换一种方式就是显示锁Lock为我们提供的一个子类**ReadWriteLock.**

**ReadWriteLock介绍**

**它其实是两把锁来的** ，一个是读锁，一个是写锁。我们称这个类为读写锁类。

*** 读锁**

多个线程执行读操作的时候，不会进行等待锁的释放，没有阻塞现象发生，甚至可以并行进行读取操作。读锁的获取，就算线程A获取到了这把读锁，其实线程B,C,D。。。 都还是可以继续获取读锁。也就是说读锁的获取对哪个线程持有它不做限制。别的线程持有读锁的同时，其他线程也是可以继续获取并持有这把读锁。但是当有线程获取到了读写锁对象的写锁，就必须要等待写锁的释放才能获取读锁！

**ReentrantReadWriteLock实现读锁**

给出大概的方法图：

![1567145358635](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567145358635.png)

看到ReentrantReadWriteLock实际上是将ReadLock做为其内置的一个类。并提供到了方法获取读锁和写锁

![1567145437765](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567145437765.png)

现在探究下读锁的源码:

```java
    public static class ReadLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -5992448646407690164L;
        private final Sync sync;
        
        protected ReadLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
 		* 给当前线程加上读锁
 		  1. 如果写锁没有被其他线程锁获取，那么将会立刻的加上读锁给当前线程
 		  2. 如果写锁正在被其他线程锁占有，则不能立刻返回读锁。而是会将当前线程挂起等待写锁的释放。再次获取读锁
        */
        public void lock() {
            sync.acquireShared(1);
        }

        /**
         * 获取读锁，获取读锁的过程是可以被其他线程中断的
         * @throws InterruptedException if the current thread is interrupted
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        /**
         * 尝试获取读锁，当写锁并没有被其他线程锁持有，则将返回true并加上读锁给当前线程。
         * @return {@code true} if the read lock was acquired
         */
        public boolean tryLock() {
            return sync.tryReadLock();
        }

        /**
         * @param timeout the time to wait for the read lock
         * @param unit the time unit of the timeout argument
         * @return {@code true} if the read lock was acquired
         * @throws InterruptedException if the current thread is interrupted
         * @throws NullPointerException if the time unit is null
         */
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
        }

        /**
         * 企图释放锁
         * <p>If the number of readers is now zero then the lock
         * is made available for write lock attempts.
         */
        public void unlock() {
            sync.releaseShared(1);
        }

        /**
         读锁不支持Condition！
         * Throws {@code UnsupportedOperationException} because
         * {@code ReadLocks} do not support conditions.
         * @throws UnsupportedOperationException always
         */
        public Condition newCondition() {
            throw new UnsupportedOperationException();
        }

        /**
         * Returns a string identifying this lock, as well as its lock state.
         * The state, in brackets, includes the String {@code "Read locks ="}
         * followed by the number of held read locks.
         *
         * @return a string identifying this lock, as well as its lock state
         */
        public String toString() {
            int r = sync.getReadLockCount();
            return super.toString() +
                "[Read locks = " + r + "]";
        }
    }
```

读锁的方法基本上在前面都见过类似的，那么区别就是在lock和tryLock的时候，会去检测是否有写锁在被其他线程使用，如果是的话就会进行阻塞/返回false。

> 读锁又称为共享锁，可以被多个线程锁共同持有，并执行其内部的读数据等逻辑操作，性能上会比排他锁提升不少。

*** ReentrantReadWriteLock实现写锁**

写锁就相当于排他锁的一种啦。其实和synchronized的加锁机制差不多。当线程A持有写锁并不释放的时候，其他线程是不可以访问临界区资源的。就会发生阻塞情况。这就是写锁。保证数据的写入安全。

老规矩，先观测下源码：

```java
 public static class WriteLock implements Lock, java.io.Serializable {
        private static final long serialVersionUID = -4992448646407690164L;
        private final Sync sync;

        /**
         * @param lock the outer lock object
         * @throws NullPointerException if the lock is null
         */
        protected WriteLock(ReentrantReadWriteLock lock) {
            sync = lock.sync;
        }

        /**
         * Acquires the write lock.
           获取读锁
           如果没有线程持有读锁/写锁的情况下，那就会立刻加上锁。并将当前线程写锁持有数目加一
 		   如果当前线程已经持有了读锁，那么就会将读锁持有数增加1
         * 如果其他线程在占有锁的情况下，就会将当前线程挂起等候，直到重新得到写锁，然后将写锁的计数器设置为1.
         */
        public void lock() {
            sync.acquire(1);
        }

        /**
         * @throws InterruptedException if the current thread is interrupted
         */
        public void lockInterruptibly() throws InterruptedException {
            sync.acquireInterruptibly(1);
        }

        /**
           尝试获取写锁，只要别的线程没有持有它就立刻加上并返回true
         * @return {@code true} if the lock was free and was acquired
         * by the current thread, or the write lock was already held
         * by the current thread; and {@code false} otherwise.
         */
        public boolean tryLock( ) {
            return sync.tryWriteLock();
        }

        /**
         * @throws InterruptedException if the current thread is interrupted
         * @throws NullPointerException if the time unit is null
         */
        public boolean tryLock(long timeout, TimeUnit unit)
                throws InterruptedException {
            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
        }

        /**
         * @throws IllegalMonitorStateException if the current thread does not
         * hold this lock
         */
        public void unlock() {
            sync.release(1);
        }

        /**
         * @return the Condition object
         */
        public Condition newCondition() {
            return sync.newCondition();
        }

        /**
           检测当前的读锁是否被当前线程持有
         * @return {@code true} if the current thread holds this lock and
         *         {@code false} otherwise
         * @since 1.6
         */
        public boolean isHeldByCurrentThread() {
            return sync.isHeldExclusively();
        }

        /**
         * @return the number of holds on this lock by the current thread,
         *         or zero if this lock is not held by the current thread
         * @since 1.6
         */
        public int getHoldCount() {
            return sync.getWriteHoldCount();
        }
    }
```

还是老样子，方法和互斥锁的方法其实没什么两样，只不过当请求锁的时候会去检查是否有线程持有读锁或写锁，如果有则进行阻塞或者说返回false。

那么ReentraReadWriteLock其实是实现了ReadWriteLock的实现子类：

```java
public interface ReadWriteLock {
    /**
     * @return the lock used for reading
     */
    Lock readLock();

    /**
     * @return the lock used for writing
     */
    Lock writeLock();
}

```

那么ReentraReadWriteLock的方法主要有：

![1567150558008](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567150558008.png)

这几个都是我们在学习ReentraLock类的时候已经见过n遍的方法了，这里就不再去讲解。



#### 如何使用ReentraReadWriteLock？

将了上面的概念及源码，我们动手验证下：

**案例：**

现在有个业务，定义了一个商品类，其中有标价属性。有n多个用户线程准备开始读取这个商品的标价。而除此之外，又有m（m<n）个线程准备对这个商品的标价进行修改，每个操作都会耗费一定的时间。统计一下，在这个整个读取和修改的过程中，花费了多少时间，期间必须要保证读写的安全性。

**分析**

从案例得到几个要点：

* 定义一个商品类，并赋予初始标价
* 创建读取商品标价的线程任务，创建修改商品标价的线程任务。
* 为了保证安全性，这里由于m<n ，理解成读线程居多，写线程较少，采用ReadWriteLock的子类进行获取读写锁然后再进行对应的加锁解锁。
* 业务开始时记录时间，业务结束时打印时间。

**实现**

```java
public class GoodServiceImpl implements GoodService {

    private ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock(false);
    private ReentrantReadWriteLock.ReadLock readLock ;
    private ReentrantReadWriteLock.WriteLock writeLock ;

    public GoodServiceImpl(){
        readLock = reentrantReadWriteLock.readLock();
        writeLock = reentrantReadWriteLock.writeLock();
    }


    @Override
    public void readGoodsInfo(Goods goods, long startTime) {
        try {
            readLock.lock();
            Thread.sleep(150);
            System.out.println(Thread.currentThread().getName()+"调用readGoodsInfo读取到了商品信息"+goods.getMoney()+"，耗时:"+(System.currentTimeMillis()-startTime));

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void writeGoodsInfo(Goods goods, long startTime) {
        //加上写锁
        try {
            writeLock.lock();
            System.out.println(Thread.currentThread().getName()+"当前等待读锁/写锁的总线程数:"+reentrantReadWriteLock.getQueueLength());
            //修改操作200ms
            Thread.sleep(5000);
            Random random = new Random();
            //设置价格
            goods.setMoney(random.nextInt(1000));
            System.out.println(Thread.currentThread().getName()+"调用writeGoodsInfo修改了商品信息"+goods.getMoney()+"，耗时:"+(System.currentTimeMillis()-startTime));
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            writeLock.unlock();
        }
    }

    //排他锁去读取
    @Override
    public synchronized void readGoodsInfoBySynchronized(Goods goods,long startTime ) {
        Thread.sleep(150);
        System.out.println(Thread.currentThread().getName()+"调用readGoodsInfoBySynchronized读取到了商品信息"+goods.getMoney()+"，耗时:"+(System.currentTimeMillis()-startTime));
    }
}

```

上面定义了三个方法，分别是**读锁读取商品信息，写锁写商品信息，排他锁读商品信息** ，这里我们先研究读锁读商品信息，和写锁写商品信息这两个功能。

```java
public class ReentraReadWriteLockTest {
    //赋予默认值
    private static final Goods goods = new Goods(291);


    //专门读取商品价格的线程
    private static class ReadThread implements Runnable{
        GoodService goodService;

        public ReadThread(GoodService goodService){
            this.goodService = goodService;
        }

        @Override
        public void run() {
            //采用读锁
            goodService.readGoodsInfo(goods,System.currentTimeMillis());
        }
    }

    //专门修改商品的信息线程
    private static class WriteThread implements Runnable{
        GoodService goodService;

        public WriteThread(GoodService goodService){
            this.goodService = goodService;
        }

        @Override
        public void run() {
            //采用写锁
            goodService.writeGoodsInfo(goods,System.currentTimeMillis());
        }
    }


    public static void main(String[] args) {
        final GoodService goodService = new GoodServiceImpl();
        WriteThread writeThread[] = new WriteThread[2];
        for(int i=0;i<writeThread.length;i++){
            writeThread[i]=new WriteThread(goodService);
            new Thread(writeThread[i],"writeThread"+i).start();
        }

        ReadThread readThread[] = new ReadThread[10];
        for(int i=0;i<readThread.length;i++){
            readThread[i] = new ReadThread(goodService);
            new Thread(readThread[i],"readThread"+i).start();
        }
    }
}

```

上面的测试用例中，创建了2个专门用来写商品信息的线程，和10个专门用来读取商品信息的线程。我们先启动写商品线程再启动读商品的线程。那么得到的结果如下：

![1567390676315](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567390676315.png)

**现象说明：**你可以看到第一个写线程在获取到写锁后 ，在等待读写锁的线程数为0而当第二个写线程在获取写锁后等待读写锁的线程数飙升到了10个。这是为什么呢？其实在第一个写线程初始化获得锁之后，读线程可能是没有开始构造的，自然没有进入到读商品方法内部等待获取读锁，那么当第二个写线程初始化之后，在获得读锁之前， 读线程可能就开始构造完毕，并且进入到获取读锁状态，但是发现在获取读锁是检测到写锁在被第二个写线程持有，故此没有办法获取读锁执行，那么当写锁释放的时候。才会去获得读锁（读锁是共享锁的，因此不用抢占）并执行逻辑代码。在写操作执行完毕后就开始了读线程的一次执行，并且读线程每个线程操作其实需要的时间是200ms。实际上如果串行执行的话得花上150*10 = 1500 ms 才可以执行完读取流程，但是这个结果很显然不一样，读线程读取完商品的耗时都是近似于10139ms (实际上就是等待写锁释放耗费的时间加上自己业务执行的时间)，而如果是串行读取的话起码也要花费 10029 + 1500 = 11529 ms ，节省了大概1400ms 的时间！ 这就是读锁的好处，可以近似于并行执行的程度。

**对比synchronized加在读线程的效率**

当然了，如果你想测试在synchronized的情况下，对应的耗时是多少，将read方法变为readGoodsInfoBySynchronized ，并将写线程的方法去掉使用写锁，而使用synchronized加在方法头上即可。

测试的结果会呈现递增的耗时对于readThread来说！

![1567403669455](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567403669455.png)

> 这也就可以看到读写锁带来的提升啦！这也是为什么jdk要引入显示锁的缘故，除了它灵活使用，还有在某些特定的场景灵活运用会提升不少性能和速度。



**拓展提升：**

上面线程认真观测你就会发现，例子中写线程的数目很少，如果只有一个写线程操作数据，而有100个读线程去读取数据，该怎么样进一步提升性能？

答案是使用**volatile关键字**。我们只需要在Goods类中的money上加上volatile关键字进行修饰，就能够在某个线程对其进行修改的时候，其余的线程可以获取到最新的值，volatile的内部其实也是采用cas进行修改值的，所以操作速度当然快于synchronized关键字。

