### 多线程学习（十一）---- Condition 接口

**Lock接口还可以实现生產者消費者模式**

![1567435300252](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567435300252.png)

看上面的Lock接口的方法中，有一个newCondition的方法。而返回的就是我们要研究的**Condition接口**

我们研究下这个方法

```java

    /**
     * Returns a new {@link Condition} instance that is bound to this
     * {@code Lock} instance.
     *返回一个和当前Lock对象实例绑定的Condition对象（可以实现消息的传递）
     * <p>Before waiting on the condition the lock must be held by the
     * current thread.
        在等待condition的时候，lock锁必须要被当前线程持有，意思是你必须要持有对应的Lock锁对象，才有权利操作对应的Condition对象。
     * A call to {@link Condition#await()} will atomically release the lock
     * before waiting and re-acquire the lock before the wait returns.
     * 在调用await（）方法的时候会自动的释放当前持有的锁然后再进入等待状态，然后再等待状态结束前会再次的重新获取锁
     * @return A new {@link Condition} instance for this {@code Lock} instance
     * @throws UnsupportedOperationException if this {@code Lock}
     *         implementation does not support conditions
     */
    Condition newCondition();
```

通过Lock中的newCondition方法获取的Condition对象是和Lock对象绑定的。

**使用方法：**

```java
   private Lock lock = new ReentrantLock(false);
    private Condition condition = lock.newCondition();
```

那么如何通过Lock，Condition来实现对应的生产消费者逻辑呢？

如下代码：

```java
//生产者消费者模式
public class SignalAndAwaitProcess {
    //javabean: isFlag为true代表有货物，fasle无货物
    //  color是货物的颜色，type为货物的类型
    private Product product;

    public SignalAndAwaitProcess(Product product){
        this.product = product;
    }

    //消费者持有的锁
    private Lock lock = new ReentrantLock(false);
    private Condition condition = lock.newCondition();


    //生产产品
    public void generateProduct() throws InterruptedException {
        while(true){
            try{
            //加锁
            lock.lock();
            //询问是否存在货物
            if(product.isFlag()){
              //如果存在则让当前线程等候,让出cpu
                condition.await();
            }
            //如果不存在
            Random random = new Random();
            int i = random.nextInt(3);
            if(i<=1){
                product.setColor("白色");
                product.setType("馒头");
            }
            else{
                product.setColor("黄色");
                product.setType("烤饼");
            }
            product.setFlag(true);
            System.out.println("生产者生产:"+product.getColor()+product.getType());
            //发个信号唤醒消费者线程
            condition.signal();
        }finally {
                lock.unlock();
            }
        }
    }


    //消费产品
    public void consumeProduct() throws InterruptedException {
        while(true) {
            try{
            //消费者锁要锁住
            lock.lock();
            if(!product.isFlag()){
                //没有产品
                condition.await();
            }
            //执行消费
            System.out.println("消费者消费:"+product.getColor()+product.getType());
            product.setFlag(false);
            condition.signal();
        }finally {
                lock.unlock();
        }
     }
    }

}
```

代码中，消费者和生产者使用的时候，必须是使用同一把锁，和同一个Condition。如果出现锁对象和Condition绑定的锁对象出现在同一个方法中，并且两者是不同的锁的话，就会出现IlleagalMonitor这种异常

所以在lock的时候，用哪个对象加上的锁，就用对应这个锁对象的Condition实例。

