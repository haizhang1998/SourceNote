## Spring注解开发----Web开发（三）

### Servlet异步请求处理

什么是异步请求呢？简单的讲就是类似于多线程，我们的请求利用多线程去处理。而不是交给1个主线程一个一个处理请求，在请求没处理完成之前就让别的线程一直等待，这样会消耗大量的资源。

这是没有开启异步功能时候的请求和响应

![1565251526013](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565251526013.png)

用户的request会从主线程池中拿出一个线程专门作为处理，在这个request没有处理完毕之前，都会一直占用这个线程。这样就会阻塞到别的线程。极大的浪费了资源。

这个是开启了异步之后的请求和响应

![1565251749752](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565251749752.png)

用户的request请求会交给异步处理线程池中进行处理，这里如果有多个请求，就会从异步线程池中获取多个线程对其进行处理操作，而不必要去等待主线程去一个个的处理，主线程已处理完就会退出，这样节省了内存的损耗，同时提高了相应效率。（在springmvc中才会有这个异步处理的线程池，在原生servlet中，只能使用主线程池的线程，而如果不开启异步功能，只会使用其中一个线程死磕到底）

**实现Servlet异步处理的方法**

```java
/**
 异步处理控制器
*/
@WebServlet(asyncSupported = true,value = "/asyn")
public class AsynController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //我们要拿到异步处理的上下文
        System.out.println("主线程:"+Thread.currentThread().getName()+"开始执行");
        final AsyncContext asyncContext = req.getAsyncContext();
        StopWatch stopWatch1 = new StopWatch();
        stopWatch1.start();
        //利用异步机制去处理请求
        asyncContext.start(
                new Runnable() {
                    @Override
                    public void run() {
                        StopWatch stopWatch = new StopWatch();
                        stopWatch.start();
                        System.out.println(Thread.currentThread().getName()+"处理请求中");
                        try {
                            Thread.sleep(3000);

                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        System.out.println(Thread.currentThread().getName()+"处理结束!"+stopWatch.getTotalTimeSeconds());
                        ServletResponse response = asyncContext.getResponse();
                        asyncContext.complete();
                        stopWatch.stop();
                        try {
                            response.getWriter().write("ok");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
        );
        System.out.println("主线程:"+Thread.currentThread().getName()+"结束执行"+stopWatch1.getTotalTimeSeconds());
        stopWatch1.stop();

    }
}
```

现在只需将你的http请求支持异步功能就可以，可以采用ajax进行传递。



### SpringMvc实现异步处理功能

spring中有自己维护的异步线程池，spring使用Callable接口进行异步处理。代码如下

```java
@Controller
public class SpringAsynController {

    @RequestMapping("/test")
    public Callable<Object> asynMethod(){
        System.out.println(Thread.currentThread().getName()+"主线程处理开始!"+System.currentTimeMillis());
        final Callable callable = new Callable() {
            @Override
            public Object call() throws Exception {
                System.out.println(Thread.currentThread().getName()+"处理请求中。。。。"+System.currentTimeMillis());
                Thread.sleep(2000);
                System.out.println(Thread.currentThread().getName()+"处理请求完毕。。。。"+System.currentTimeMillis());
                return "res";
            }
        };
        System.out.println(Thread.currentThread().getName()+"主线程处理完毕!"+System.currentTimeMillis());
        return callable;
    }
}

```

执行的步骤：

1. http请求第一次发送到异步处理接口的时候，会先经过一次拦截器调用它的preHandle方法（如果有的话）
2. 控制器返回callabe
3. Spring异步处理，将Callable提交到TaskExecutor使用一个隔离的线程进行
4. DispatcherServlet和所有的Filter退出web容器的线程，但是response保持打开状态
5. Callable返回结果，SpringMVC重新派发给容器，回复之前的处理
6. 根据Callable返回的结果。SpringMvc继续进行试图渲染流程等（从接收请求--到试图渲染）比如返回string，那就会尝试跳转到string指定的jsp等。这一步从头开始不会执行我们的方法逻辑了，主要是针对返回值进行一次处理。（你可以想象成，方法体是空，只有Callable返回值的结果，然后交给springmvc进行试图渲染，而不会再次执行主线程处理语句。）

结果截图

![1565315746965](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565315746965.png)





####Spring异步处理--DeferredResult（结合消息中间件）

![1565316084886](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565316084886.png)

假设我们现在有个创建订单编号的请求，交给应用1中的线程1进行处理，可碰巧应用1并没有这个逻辑，那它就需要将这个请求消息保存在消息中间件中（jmx..）那应用2有这个逻辑，且它就会去监听消息中间件中是否有对应的请求等待处理，如果捕获到这个请求，就会将处理结果返回给中间件，应用1中的线程2一旦监听到这个请求的结果的返回值，就立刻将结果返回给客户端。

如果我们要完成这种效果，就需要使用Spring中的DeferredResult。我们现在来模拟这种效果，当然不会用到两个应用和中间件。只是演示。

1. 我们定义一个 DeferredResultQueue来暂存待处理的DefferedResult对象

   ```java
   
   public class DeferredResultQueue {
       ConcurrentLinkedDeque<DeferredResult> concurrentLinkedDeque =new  ConcurrentLinkedDeque<DeferredResult>();
   
       //保存DeferredResult方法
       public void save(DeferredResult deferredResult){
           concurrentLinkedDeque.add(deferredResult);
       }
   
       //获取并删除一个deferredResult
       public DeferredResult get(){
           if(!concurrentLinkedDeque.isEmpty())
               return concurrentLinkedDeque.poll();
           else
               return new DeferredResult(10000L,"处理请求超时");
       }
   }
   
   ```

   

2. 我们定义控制器并实现DeferredResult返回值的方法。

```java
@Controller
public class DefferedResultController {
    private  DeferredResultQueue deferredResultQueue = new DeferredResultQueue();
    
    //客户端首先回调用这个接口尝试生成订单
    @RequestMapping("/createOrder")
    @ResponseBody
    public DeferredResult<Object> deferredResultMehtod(){
        DeferredResult<Object> deferredResult = new DeferredResult<Object>(100000L,"处理请求超时");
        //当前app处理不了这个请求，用一个队列将deferredResult对象暂时存放起来（相当于模拟中间件），等待其他线程对这个对象设置值后再将这个对象返回给客户端
        deferredResultQueue.save(deferredResult);
        return  deferredResult;
    }

    //这个方法生成订单，模拟应用2
    @RequestMapping("/create")
    public void createOrder(){
        //随机拿到一个待处理的deferredResult 
        DeferredResult<Object> deferredResult = deferredResultQueue.get();
        //一旦调用deferredResult 这个方法，对应监听这个对象的线程就会返回这里设置的值
        deferredResult.setResult(Thread.currentThread().getName()+"suc!");
    }
}

```

定义完成后还要将Controller加载到容器中喔

ok到这里，我们就可以运行web容器了。

首先我们输入http://localhost:8080/createOrder 请求创建订单（注意上面设置了10秒中超时，再10秒中之内要处理好相应）

接着我们在另一个网页输入http://localhost:8080/create 来获取 deferredResult处理请求。当create页面处理成功后，对应的creatOrder的页面就能返回我们creat中设置的result。