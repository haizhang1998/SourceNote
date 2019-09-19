### ThreadLocal引发的问题及解决方法

看到标题，我们在没有很了解ThreadLocal的作用的前提下，是不是都觉得ThreadLocal不会发生安全性问题？其实你的想法是错的!

**ThreadLocal有可能会出现内存泄漏问题**

在探究ThreadLocal引发的内存泄漏问题，首先内存泄漏的概念又如何呢？ 

它通常指的是**jvm在垃圾回收的时候，原本该被清理的对象，依旧存在于堆中占用内存！这种对象一旦堆积多了，就会导致程序的效率下降，甚至内存占满，内存溢出的现象！**

那么为什么ThreadLocal可能会引发这种现象呢？

我们看下它的源码：

```java
//部分源码
public class ThreadLocal<T> {
    //用于计算线程的本地值存储在entry数组种的位置
     private final int threadLocalHashCode = nextHashCode();

    //设置本地值
    public void set(T value) {
        //拿到当前的线程
        Thread t = Thread.currentThread();
        //拿到ThreadLocalMap(其中Entry[]是它的主要存储数据结构)
        ThreadLocalMap map = getMap(t);
        if (map != null)
            //ThreadLocalMap内部会拿到ThreadLocal的threadLocalHashCode，利用hash计算存储的位置，然后
            map.set(this, value);
        else
            createMap(t, value);
    }

    
    
    
    //返回当前线程的threadLocal.ThreadLocalMap中存放的变量副本值。
    public T get() {
        //得到当前调用此方法的线程
        Thread t = Thread.currentThread();
        //获取该线程的 ThreadLocalMap
        ThreadLocalMap map = getMap(t);
     
        if (map != null) {
            //将当前的threadLocal对象传入，用当前线程存放的ThreadLocalMap获取对应的entry
            ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null) {
                //返回对应的entry值
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }
           //如果线程没有创建对应的 ThreadLocalMap，就调用createMap方法为该线程设置ThreadLocalMap并返回null
        return setInitialValue();
    }
    
     //移除当前线程存放在ThreadLocalMap中的变量副本。注意这个方法可以防止ThreadLocal引发的内存泄漏的问题！
     public void remove() {
         ThreadLocalMap m = getMap(Thread.currentThread());
         if (m != null)
             m.remove(this);
     }
    
     
      ThreadLocalMap getMap(Thread t) {
          //去当前的线程拿到ThreadLocalMap
        return t.threadLocals;
    }

     void createMap(Thread t, T firstValue) {
        t.threadLocals = new ThreadLocalMap(this, firstValue);
    }

    
     /**
     * ThreadLocalMap is a customized hash map suitable only for
     * maintaining thread local values. No operations are exported
     * outside of the ThreadLocal class. The class is package private to
     * allow declaration of fields in class Thread.  To help deal with
     * very large and long-lived usages, the hash table entries use
     * WeakReferences for keys. However, since reference queues are not
     * used, stale entries are guaranteed to be removed only when
     * the table starts running out of space.
     */
    //看到这里官方文档的解释，ThreadLocalMap是一个自定义的hash map，只用于维护线程的本地值。主要是定义了WeakReferences（弱引用）类型作为key，然后定义了一系列增删查改线程存放的变量副本的方法（此处略去）
    static class ThreadLocalMap {
          /**
         * The table, resized as necessary.
         * table.length MUST always be a power of two.
         */
        private Entry[] table;

        
       //注意这个Entry，key一般就是当前的ThreadLocal，而value就是每个线程传递到threadLocal的变量
        static class Entry extends WeakReference<ThreadLocal<?>> {
            /** The value associated with this ThreadLocal. */
            Object value;

            Entry(ThreadLocal<?> k, Object v) {
                super(k);
                value = v;
            }
        }
    }
```

唯一需要我们注意的就是**Entry extends WeakReference<ThreadLocal<?>>** 这一句，它代表了我们的key被定义成了弱引用类型，这样设置的初衷在于可以在内存不够用的时候及时的释放掉我们ThreadLocal对象的内存空间，但是就是因为这个操作才会导致内存泄漏。我们看下主要释放的过程。

在开始之前我们讲下4中引用类型：

* **强引用类型**

  什么是强引用呢？描述的是栈中引用指针直接连接到堆中的对象，在堆中生成的对象还有栈中以用指向之前，不管内存够不够用，都不能够释放堆中的该对象

  ```java
    Object o = new Object();
    Object o1 = o
  ```

  ![1565771788192](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565771788192.png)

  看到上面的例子和对应到内存中的图。o,o1两个引用，相当于new Object（）对象的两个标签。他们都指向这个堆中的对象，那么此时我们测试下结果，当我们把o的引用设置为null，并手动调用gc进行回收，那么o1是否还能持有这个对象呢？也就是说这个对象有没有被回收呢？

  ```java
      Object o = new Object();
         Object o2 = o;
         System.out.println("gc前 o持有的对象地址:"+o);
         System.out.println("gc前 o2持有的对象地址:"+o2);
         o = null;
         System.gc();
         System.out.println("gc后 o持有的对象地址:"+o);
         System.out.println("gc后 o2持有的对象地址:"+o2);
  ```

  测试结果:
  ![1565772194878](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565772194878.png)

  结果显然，只要存在引用指向Object对象，这个对象就不会被gc干掉。这就是强引用类型。

* **弱引用类型**

  弱引用(WeakReference) 和强引用类型很不同，它的存在感觉随时可以被jvm回收处理掉（当内存不足时会首先干掉其中弱引用指向的堆中对象）。那么它又是如何在内存体现的呢？我们结合下面的程序说明

  ```java
    public static  void weakReferenceTest(){
          Object target = new Object();
          WeakReference<Object> weakReference= new WeakReference<Object>(target);
          System.out.println("gc前  weakReference持有的对象地址:"+weakReference.get());
          target =null;
          System.gc();
          System.out.println("gc后  weakReference持有的对象地址:"+weakReference.get());
      }
  ```

  首先target是个强引用类型，我们通过WeakReference把target这个强引用包装成弱引用类型返回给weakReference（此时target还是强引用），此时gc前我们拿到弱引用指向的堆对象地址（此时和强引用一样指向），现在强引用已经引用完毕了这个对象，将其指向空，**此时只有弱引用指向这个堆中的对象了**，那么现在进行gc处理，如果是强引用肯定这个对象是不会被干掉的，但是现在只有一个弱引用指向这个对象，gc首先干掉的就是弱引用指向的这个对象！测试的结果如下：

  ![1565773541906](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565773541906.png)

  大体的流程图：

  ![1565775270700](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565775270700.png)

* **软引用类型**

  软引用类型（SoftReference）其实和弱引用差不多，但是唯一的区别就是，当内存不够的时候，gc会去查找有没有存在没被引用的对象，如果有就先释放它的内存空间，当弱引用之类的对象释放完成后，检测是否内存还是不够用，如果是，就释放软引用类型所指向的对象（前提是只有软引用指向的对象才能被释放，存在强引用指向这个对象是不会被释放内存的！）

* **虚引用类型**

  虚引用是使用PhantomReference创建的引用，虚引用也称为幽灵引用或者幻影引用，是所有引用类型中最弱的一个。一个对象是否有虚引用的存在，完全不会对其生命周期构成影响，也无法通过虚引用获得一个对象实例

> 虚引用，正如其名，对一个对象而言，这个引用形同虚设，有和没有一样

当试图通过虚引用的get()方法取得强引用时，总是会返回null，并且，虚引用必须和引用队列一起使用。既然这么虚，那么它出现的意义何在？？

别慌别慌，自然有它的用处。它的作用在于跟踪垃圾回收过程，在对象被收集器回收时收到一个系统通知。 当垃圾回收器准备回收一个对象时，如果发现它还有虚引用，就会在垃圾回收后，将这个虚引用加入引用队列，在其关联的虚引用出队前，不会彻底销毁该对象。 所以可以通过检查引用队列中是否有相应的虚引用来判断对象是否已经被回收了。

如果一个对象没有强引用和软引用，对于垃圾回收器而言便是可以被清除的，在清除之前，会调用其finalize方法，如果一个对象已经被调用过finalize方法但是还没有被释放，它就变成了一个虚可达对象。

与软引用和弱引用不同，显式使用虚引用可以阻止对象被清除，只有在程序中显式或者隐式移除这个虚引用时，这个已经执行过finalize方法的对象才会被清除。想要显式的移除虚引用的话，只需要将其从引用队列中取出然后扔掉（置为null）即可。

实例：

```java
public class PhantomReferenceTest {
    private static final List<Object> TEST_DATA = new LinkedList<>();
    private static final ReferenceQueue<TestClass> QUEUE = new ReferenceQueue<>();

    public static void main(String[] args) {
        TestClass obj = new TestClass("Test");
        //声明弱引用类型
        PhantomReference<TestClass> phantomReference = new PhantomReference<>(obj, QUEUE);

        // 该线程不断读取这个虚引用，并不断往列表里插入数据，以促使系统早点进行GC
        new Thread(() -> {
            while (true) {
                TEST_DATA.add(new byte[1024 * 100]);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
                //尝试拿到虚引用
                System.out.println(phantomReference.get());
            }
        }).start();

        // 这个线程不断读取引用队列，当弱引用指向的对象被回收时，该引用就会被加入到引用队列中
        new Thread(() -> {
            while (true) {
                Reference<? extends TestClass> poll = QUEUE.poll();
                if (poll != null) {
                    System.out.println("--- 虚引用对象被jvm回收了 ---- " + poll);
                    System.out.println("--- 回收对象 ---- " + poll.get());
                }
            }
        }).start();

        obj = null;

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    static class TestClass {
        private String name;

        public TestClass(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "TestClass - " + name;
        }
    }
}
//使用的虚拟机设置如下：
//-verbose:gc -Xms4m -Xmx4m -Xmn2m
```



> 使用虚引用的目的就是为了得知对象被GC的时机，所以可以利用虚引用来进行销毁前的一些操作，比如说资源    释放等。这个虚引用对于对象而言完全是无感知的，有没有完全一样，但是对于虚引用的使用者而言，就像是待观察的对象的把脉线，可以通过它来观察对象是否已经被回收，从而进行相应的处理。



### 回到主题，ThreadLocal的内存泄漏原因！

请看代码：

```java

public class ThreadLocalLeack {
    public static final int POOL_SIZE = 100;
    public  ThreadLocal<LocalVariable> threadLocal = new ThreadLocal<LocalVariable>();
    public static ThreadPoolExecutor poolExecutor= new ThreadPoolExecutor(POOL_SIZE,POOL_SIZE*2,5000, TimeUnit.SECONDS,new LinkedBlockingQueue<>());

    private static class LocalVariable{
        private byte[] b= new byte[1024*1024*5];
    }

    public static void main(String[] args) {
        for(int i =0 ;i<100 ; i++){
            poolExecutor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        ThreadLocalLeack threadLocalLeack = new ThreadLocalLeack();
                        LocalVariable localVariable = new LocalVariable();
                        threadLocalLeack.threadLocal.set(localVariable);
                        //这一步用于释放内存！你可以将这段注释掉，然后对比java visulVM内存的结果！
                        threadLocalLeack.threadLocal.remove();
                    }
                }
            );

        }
        System.out.println("ok");
        poolExecutor.shutdown();
    }
}

```

上面代码很简单，主要就是生成100个5MB大小的数组在内存中。并将这些对象每个线程保存一个副本在ThreadLocal中。如果我们不将这些对象使用完毕后进行回收就会导致内存泄漏！

下面时图解：

![1565777008943](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565777008943.png)

其中，Threadlocal-1代表ThreadLocal的一个引用，而thread-1就是当前线程，每个线程内部都存有ThreadLocalMap对象，而其内部又是用Entry[]数组进行存储的，Entry时存放着thread-1再ThreadLocal中存放的变量副本。其中key时一个弱引用指向当前的ThreadLocal对象，而value及为当前线程对应的值。

**当当前线程使命完成（比如run方法处理完）或者threadLocal对当前线程处理完成后，就将threadLocal引用置为空，而此时垃圾回收的话会发现堆中的ThreadLocal对象中只有一个弱引用类型指向它（key），那么此时垃圾回收机制就会回收ThreadLocal对象所占用的内存，如下图**

![1565777194046](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565777194046.png)

那么现在key已经被置为null了，于此同时，value却还存放着值，但是无法去访问它！而原本当threadLocal-1被释放内存的时候，应当同时释放thread-1存放的变量副本占用的内存，但是并没有得到释放，反而残留再了堆空间中，着就是典型的内存泄漏现象了！

**如何处理种种现象呢？**

我们可以手动在线程方法结束前或者不再使用threadLocal设置的线程变量副本之前，清理这个变量所占的空间，这时候就要使用threadLocal.remove方法去释放这个副本的内存空间了！



> ThreadLocal中弱引用类型会导致内存泄漏问题
>
> synchronized关键字也会有线程安全问题，比如死锁，如果不注意的话可能对象值会改变，导致锁不住的情况发生。



**拓展内容**

Cas加锁机制（乐观锁）

为什么synchronized里面的i++有些时候比cas中换值速度要慢？

因为synchronized会涉及到上下文切换，而cas没有，synchronized是会把线程锁住的，这里存在着两次上下文切换



阿里巴巴面试基础：

![1565788308839](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565788308839.png)

**threadLocal为什么要使用Entry[] ，数组去存放当前线程的变量呢？**

![1565792300137](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565792300137.png)

上面图例说明的很清楚，如果thread-1 同时调用两个ThreadLocal（threadLocal-1 threadLocal-2），那么Entry[]数组就会分别为这两个threadLocal保存不同的副本值（看你threadLocal set哪个值进去），在set值的时候，threadLocal就会去检查当前线程是否已经创建了ThreadLocalMap对象，如果有的话，就拿到当前的threadLocal的hashCode计算该threadLocal存放的副本变量值所在Entry[]数组中的位置，如果key和当前threadLocal一致，那就替代原先的值。

**拓展**

Spring使用了ThreadLocal处理事务，事务处理必须是使用同一个连接对象（Connection）而spring就是使用ThreadLocal去保存Connection这个对象的。