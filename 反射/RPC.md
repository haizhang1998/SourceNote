# RPC 

概念：Rpc就是远程过程调用，指的是两台不同的机器上运行着不同的代码，而一台机器上正在运行的模块中的代码想要调用另一台机器上运行的模块代码，并从而得到代码的执行结果，这个过程就是RPC，它的核心技术就是Socket、reflection, 多线程，jdk动态代理技术。

思路：

1. 客户端通过socket请求服务端，并且通过字符串形式或者Class形式将需要请求的接口发送到服务端。（通过动态代理，将需要访问的方法的方法名，类型，接口名发送到服务器端）

   ```java
   //首先定义一个Service接口
   public interface CardService {
   
       public String saveCard(String possesser,Long cardId);
   }
   
   ```

   ```java
   //其次定义service实现
   public class CardServiceImpl implements CardService {
       @Override
       public String saveCard(String possesser, Long cardId) {
           System.out.println("card:"+possesser+";"+cardId);
           return possesser+"已经保存银行卡为"+cardId+"的信息";
       }
   }
   
   ```

   ```java
   //定义客户端的实现
   /**
    * 这里模拟客户端，假设客户端和RpcServer在不同的服务器上运行
      客户端主要是请求方，而服务器主要是接受方，客户端想要调用服务端运行的模块的方法，必须要用Socket传递    方法的信息
      而且还有客户端调用不同的方法，这里需要服务端根据不同的请求返回不同的结果，考虑用到客户端的jdk动态代    理技术，代理要请求的接口
    */
   public class RpcClient {
   
       /**
        * 泛型方法，注意<T>必须声明在T前面
        * serviceClass 是实现类，不是接口
        * @param <T>
        * @return
        */
       public<T> T  getDnamicProxyResult(Class serviceClass){
   
           return  (T)Proxy.newProxyInstance(serviceClass.getClassLoader(),serviceClass.getInterfaces(), new InvocationHandler() {
               @Override
               public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                   //请求连接
                   Socket socket=null;
                   ObjectInputStream objectInputStream=null;
                   ObjectOutputStream objectOutputStream=null;
                   try {
                       socket = new Socket("127.0.0.1",10240);
                       //绑定ip：端口
   
                       objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                       //注意，这里的输出流对顺序由严格要求，什么顺序写的在服务端就什么顺序读取
                       objectOutputStream.writeUTF(serviceClass.getName());
                       objectOutputStream.writeUTF(method.getName());
                       //方法的参数类型，用于反射得到方法
                       objectOutputStream.writeObject(method.getParameterTypes());
                       //方法的参数
                       objectOutputStream.writeObject(args);
                       //得到对象输入流
                       objectInputStream = new ObjectInputStream(socket.getInputStream());
                       //返回调用得到结果
                       return   objectInputStream.readObject();
                   }catch (Exception e){
                       e.printStackTrace();
                   }finally {
                       if(objectInputStream!=null)
                           objectInputStream.close();
                       if(objectOutputStream!=null)
                           objectOutputStream.close();
                   }
                return  null;
               }
   
           });
       }
   
   }
   
   ```

   

2. 服务端可以提供的接口注册到服务中心（通过map保存，map的key就是接口的名字，而value是接口的实现类）

   ```java
   //服务端的实现
   **
    * rpc服务端
    */
   public class RpcServer {
       private static int port;
       //拿到电脑可以用的处理器数目来生成线程池的线程数量
       public static ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
       //是否运行服务的一个标记，默认是开启
       public static boolean isRunning = true;
       //注册服务，服务类对应的服务具体实现,接口类===实现类
       public static Map<Class,Class> map = new HashMap<>();
       public RpcServer() {
           port = 9999;
       }
   
       public RpcServer(int port) {
           this.port = port;
       }
   
       //实现服务注册对应的接口以及实现类（都是class类型）
       public  void  register (Class servicInterface,Class serviceImpl){
           map.put(servicInterface,serviceImpl);
       }
   
       public void start() {
           ServerSocket serverSocket = null;
   
   
           try {
               serverSocket = new ServerSocket(port);
   
               System.out.println("端口已打开，现在服务端口是:"+port);
   
               //无限次处理接受请求
               while (isRunning) {
   
                   Socket socket = serverSocket.accept();
                   System.out.println("接受到请求"+socket.getPort());
                   executorService.execute(new ServiceTask(socket));
               }
           } catch (IOException e) {
               e.printStackTrace();
           }
       }
   
       public static void stop() {
           isRunning = false;
           //关闭线程池服务
           executorService.shutdown();
       }
   
   
       //线程的任务类
       public static class ServiceTask implements Runnable {
           Socket socket;
   
           public ServiceTask(Socket socket) {
               this.socket = socket;
           }
   
           public ServiceTask() {
           }
   
   
           //线程的处理逻辑
           @Override
           public void run() {
               ObjectInputStream objectInputStream = null;
               ObjectOutputStream objectOutputStream = null;
   
               try {
                   objectInputStream = new ObjectInputStream(socket.getInputStream());
                   objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                   //注意取值的顺序必须和赋值的时候顺序一致！
                   String serviceClassName = objectInputStream.readUTF();
                   
                   String serviceMethodName = objectInputStream.readUTF();
                   //类型参数，多个
                   Class[] methodParameterTypes = (Class[]) objectInputStream.readObject();
                   //方法值，多个
                   Object[] methodParameters = (Object[]) objectInputStream.readObject();
                   System.out.println("请求的服务类名:"+serviceClassName);
                   System.out.println("请求的服务方法名:"+serviceMethodName);
                   //通过反射获取
                   Class cls = Class.forName(serviceClassName);
                   //创建实例并调用方法
                   Method mehthod = cls.getMethod(serviceMethodName, methodParameterTypes);
                   //开始调用方法
                   Object o = mehthod.invoke(cls.newInstance(), methodParameters);
                   //将调用后的结果返回
                   objectOutputStream.writeObject(o);
               } catch (Exception e) {
                   e.printStackTrace();
               } finally {
                   try {
                       if(objectInputStream!=null)
                           objectInputStream.close();
                       if (objectOutputStream!=null)
                           objectOutputStream.close();
                           if(socket!=null)
                           socket.close();
                   } catch (IOException e) {
                       e.printStackTrace();
                   }
               }
           }
       }
   }
    
   ```

   

3. 服务端接受到请求后，通过请求的接口名，在服务中心的map中寻找对应的接口实现类，通过反射机制调用接口的方法，此时必须传入方法名，方法参数类型，还有参数的值。

4. 执行完毕后再将该方法的返回值返回给客户端。

   ```java
   //服务端测试
   
   public class RpcTest {
   
       public static void main(String[] args) {
   
           new Thread(new Runnable() {
               @Override
               public void run() {
                   //指定服务端口
                   RpcServer rpcServer = new RpcServer(10240);
                  
                   rpcServer.register(CardService.class, CardServiceImpl.class);
                   rpcServer.register(UserService.class, UserServiceImpl.class);
                   //注册完成启动
                   rpcServer.start();
               }
           }).start();
       }
   }
   ```

   ```java
   //客户端测试
   
   public class RpcClientTest {
   
       public static void main(String[] args) throws ClassNotFoundException {
           RpcClient rpcClient = new RpcClient();
           CardService cardService=rpcClient.getDnamicProxyResult(Class.forName("com.haizhang.rpcDemo.service.CardServiceImpl"));
           System.out.println(cardService.saveCard("haizhang",123123123L));
       }
   }
   ```

   

**注意Sokcet在服务端启用多线程的线程池技术进行优化，提高请求处理的效率。**

