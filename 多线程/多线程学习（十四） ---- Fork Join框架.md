### 多线程学习（十四） ---- Fork Join框架

**第一模块： ForkJoin的概念**

从JDK1.7开始，Java提供Fork/Join框架用于并行执行任务，它的思想就是讲一个大任务分割成若干小任务，最终汇总每个小任务的结果得到这个大任务的结果。 也就是分而治之的思想。

这种思想和MapReduce很像（input --> split --> map --> reduce --> output）

主要有两步：

- 第一、任务切分；
- 第二、结果合并

它的模型大致是这样的：线程池中的每个线程都有自己的工作队列（PS：这一点和ThreadPoolExecutor不同，ThreadPoolExecutor是所有线程公用一个工作队列，所有线程都从这个工作队列中取任务），当自己队列中的任务都完成以后，会从其它线程的工作队列中偷一个任务执行，这样可以充分利用资源。

![1567498969577](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567498969577.png)



**第二模块：工作窃取**（提高cpu利用率）

![img](https://images2018.cnblogs.com/blog/874963/201805/874963-20180523154643214-1612544334.png)

上面说到了Fork Join框架中的线程池中**每个线程都有一个自己独特的工作队列** ，假设线程1中的工作队列装载着4个任务，而线程B中的工作队列装载着4个， 那么此时线程1中运行完全部的任务后，线程B才运行到第三个任务。那就会出现线程1窃取线程B的任务4去运行，然后再将结果返回给线程2的工作队列中。

**那么为什么需要使用工作窃取算法呢？**

假如我们需要做一个比较大的任务，我们可以把这个任务分割为若干互不依赖的子任务，为了减少线程间的竞争，于是把这些子任务分别放到不同的队列里，并为每个队列创建一个单独的线程来执行队列里的任务，线程和队列一一对应，比如A线程负责处理A队列里的任务。但是有的线程会先把自己队列里的任务干完，而其他线程对应的队列里还有任务等待处理。干完活的线程与其等着，不如去帮其他线程干活，于是它就去其他线程的队列里窃取一个任务来执行。而在这时它们会访问同一个队列，所以为了减少窃取任务线程和被窃取任务线程之间的竞争，**通常会使用双端队列，被窃取任务线程永远从双端队列的头部拿任务执行，而窃取任务的线程永远从双端队列的尾部拿任务执行。**

工作窃取算法的优点是充分利用线程进行并行计算，并减少了线程间的竞争，其缺点是在某些情况下还是存在竞争，比如双端队列里只有一个任务时。并且消耗了更多的系统资源，比如创建多个线程和多个双端队列。



**第三模块：ForkJoinPool讲解**

ForkJoinPool与其它的ExecutorService区别主要在于它使用“工作窃取”：线程池中的所有线程都企图找到并执行提交给线程池的任务。当大量的任务产生子任务的时候，或者同时当有许多小任务被提交到线程池中的时候，这种处理是非常高效的。尤其是当在构造方法中设置asyncMode为true的时候这种处理更加高效。

![img](https://images2018.cnblogs.com/blog/874963/201805/874963-20180523155916226-588922770.png)

WorkQueue是一个ForkJoinPool中的内部类，它是线程池中线程的工作队列的一个封装，支持任务窃取。

> 什么叫线程的任务窃取呢？就是说你和你的一个伙伴一起吃水果，你的那份吃完了，他那份没吃完，那你就偷偷的拿了他的一些水果吃了。存在执行2个任务的子线程，这里要讲成存在A,B两个个WorkQueue在执行任务，A的任务执行完了，B的任务没执行完，那么A的WorkQueue就从B的WorkQueue的ForkJoinTask数组中拿走了一部分尾部的任务来执行，可以合理的提高运行和计算效率。

我们看下WorkQueue的主要方法

```java
    static final class WorkQueue {
        
        final ForkJoinPool pool;  
        //拥有这个任务队列的线程。
        final ForkJoinWorkerThread owner; 
        //保存任务的队列
        ForkJoinTask<?>[] array;  
        //当前的执行的任务
        volatile ForkJoinTask<?> currentJoin;  
        volatile ForkJoinTask<?> currentSteal; // mainly used by helpStealer
        
 		WorkQueue(ForkJoinPool pool, ForkJoinWorkerThread owner) {
            this.pool = pool;
            this.owner = owner;
            base = top = INITIAL_QUEUE_CAPACITY >>> 1;
        }
    }
```

上面简单的呈现了任务队列的具体结构。实际上任务提交部分实在ForkJoinPool类中

* Submit

![img](https://images2018.cnblogs.com/blog/874963/201805/874963-20180523170036097-1854162491.png)

* execute

![img](https://images2018.cnblogs.com/blog/874963/201805/874963-20180523170046577-972016318.png)



* externalPush（将当前任务提交）

![img](https://images2018.cnblogs.com/blog/874963/201805/874963-20180523170247725-1317698839.png)

可以看到：

1. 同样是提交任务，submit会返回ForkJoinTask，而execute不会
2. 任务提交给线程池以后，会将这个任务加入到当前提交者的任务队列中。

前面我们说过，每个线程都有一个WorkQueue，而WorkQueue中有执行任务的线程（ForkJoinWorkerThread owner），还有这个线程需要处理的任务（ForkJoinTask<?>[] array）。那么这个新提交的任务就是加到array中。

**第四模块：ForkJoinTask讲解**

```java
public abstract class ForkJoinTask<V> implements Future<V>, Serializable 
```

ForkJoinTask代表运行在ForkJoinPool中的任务。

主要方法：

- fork()    在当前线程运行的线程池中安排一个异步执行。简单的理解就是再创建一个子任务。

  ![img](https://images2018.cnblogs.com/blog/874963/201805/874963-20180523172200955-636692873.png)

  > 可以看到，如果是ForkJoinWorkerThread运行过程中fork()，则直接加入到它的工作队列中，否则，重新提交任务。

  

- join()    当任务完成的时候返回计算结果。

- invoke()    同步方法，主线程后面的代码要等待invoke方法运行完成才会执行后续代码。

  ![img](https://images2018.cnblogs.com/blog/874963/201805/874963-20180523173616950-645619291.png)

  ![img](https://images2018.cnblogs.com/blog/874963/201805/874963-20180523174040579-1660483412.png)

  可以看到它们都会等待计算完成

- submit()   异步方法，有返回值，可以调用get()方法，拿到返回值。
- execute()  异步方法，没有返回值。

子类：

- RecursiveAction    一个递归无结果的ForkJoinTask（没有返回值）
- RecursiveTask<V>    一个递归有结果的ForkJoinTask（有返回值）

主要的类继承关系图

![img](https://images2018.cnblogs.com/blog/874963/201805/874963-20180523163529873-1907395587.png)

![1567503268955](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567503268955.png)





**使用图例**

就拿计算1+2+....+10000这个例子来讲解

![img](https://images2018.cnblogs.com/blog/874963/201805/874963-20180523175928739-1854695576.png)

![img](https://images2018.cnblogs.com/blog/874963/201805/874963-20180523180033920-1766834164.png)

 

![img](https://images2018.cnblogs.com/blog/874963/201805/874963-20180523180043948-1139632177.png)

![img](https://images2018.cnblogs.com/blog/874963/201805/874963-20180523182024837-2085258468.png)

 



**使用ForkJoin框架，遍历指定目录下所有的txt文件**

```java
public class ForkJoinTest {


    //查找text文件在给定的dir目录下
    private static class FindTxtFileInDir extends RecursiveAction {

       private File filePath ;


        public FindTxtFileInDir(File filePath) {
            this.filePath = filePath;
        }

        //计算如何去寻找txt文件
        @Override
        protected void compute() {
            //用于存放子任务
            List<FindTxtFileInDir> submitTask =  new ArrayList<>();
            //首先获取所有的子文件
            File[] files = filePath.listFiles();
            if(files!=null){
                //其次遍历files
                for(File file : files){
                    //如果不是目录，那就是文件
                    if(!file.isDirectory()){
                        //检查文件的后缀
                        if(file.getAbsolutePath().endsWith(".txt")){
                            System.out.println(file);
                        }
                    }else{
                        //否则是目录，同样的处理这个子任务
                        submitTask.add(new FindTxtFileInDir(file));
                    }
                }
             //如果子任务队列非空，那就有待继续搜索的子任务，提交到池子中继续compute流程
                if(!submitTask.isEmpty()){
                    //在这里遍历所有的子任务，然后提交
                    for(FindTxtFileInDir findTxtFileInDir : invokeAll(submitTask)){
                        //通过join方法归并每一个子任务处理结果
                        findTxtFileInDir.join();
                    }
                }
            }
        }
    }


    public static void main(String[] args) {
        //新建forkJoinPool来处理提交的任务
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        //再通过创建我们自定义的ForkJoinTask
        FindTxtFileInDir findTxtFileInDir = new FindTxtFileInDir(new File("D:/")) ;
       //execute 异步提交这个大任务到线程池中处理
        forkJoinPool.execute(findTxtFileInDir);
        //主线程自己执行任务
        System.out.println("main thread do sth here");
        //获取处理结果，一直阻塞等待
        findTxtFileInDir.join();
        System.out.println("Task end");

    }
}

```

上面新建一个ForkJoinPool去处理提交的ForkJoinTask（RecursiveAction和RecursiveTask<v>都是它的子类），那么提交的方法有execute(),submit ,invoke 三者。而execute异步的提交，主线程还是可以继续后面操作。在获取任务的结果时，使用join()方法获取得到最终的结果。子任务调用join时收集每个子任务处理的结果。

**使用ForkJoin框架计算1+....+10000的和**

```java
package ForkJoin;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

//使用forkJoin框架计算1+....+10000的总和
public class ForkJoinCalcSum {

    private static class SubTask extends RecursiveTask<Integer>{
        //定义一个阈值
        private final int LIMIT = 50;
        private int start ;
        private int end ;
        public SubTask(int start, int end){

            this.end = end;
            this.start = start;
        }

        @Override
        protected Integer compute() {
           //当start - end <= Limit 就开始计算业务逻辑
            if(end-start+1<=LIMIT){
                int sum =0;
                for(int i=start;i<=end;i++){
                    try {
                          //处于对比，每次让线程睡眠10ms
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    sum+=i;
                }
                return sum;
            }

            //如果不满足，那就切分,中间分一半
            int mid = (start+end)/2;
            SubTask subTask = new SubTask(start,mid);
            SubTask subTask1 = new SubTask(mid+1, end);
            invokeAll(subTask,subTask1);
            return subTask.join()+subTask1.join();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        SubTask subTask = new SubTask(1,10000);
        long start =System.currentTimeMillis();
        forkJoinPool.invoke(subTask);
        System.out.println(subTask.join());

        System.out.println("耗时"+(System.currentTimeMillis()-start));
        
        //这里使用单线程（main）来处理，进行对比
        start =System.currentTimeMillis();
        int counter=0;
        for(int i=0;i<10000;i++){
            //处于对比，每次让主线程睡眠10ms
            Thread.sleep(10);
            counter+= i;
        }
        System.out.println("耗时"+(System.currentTimeMillis()-start));
    }
}

```

结果:

![1567565288262](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1567565288262.png)

差了差不多9倍多的时间消耗。可以见到，越是耗时的任务，如果可以将其拆分，并通过ForkJoinPool提交让多线程处理，效率将会高出很多倍。

