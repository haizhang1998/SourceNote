# Socket进阶 --- 传递文件

本文的话题如何通过socket利用两台电脑之间进行传输文件？

很简单，实质就是两台计算机的Socket连接传递文件的功能。再加上多线程的线程池优化。

```java
//服务器端
/**
 * 文件传输的服务端
 */
public class FileServer {
     public static ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    /**
     * 指定启动的端口
     * @param port
     */
    public void start(int port) throws IOException {

        ServerSocket serverSocket = new ServerSocket(9089);
        Socket socket=null;
        while (true) {
            System.out.println("等待客户端连接");
            socket= serverSocket.accept();
            executorService.execute(new ServerTask(socket));
        }

    }

    //停止服务器端口
    public void stop(Object...objects) throws IOException {
         for(Object o: objects){
             if(o.getClass() == Socket.class){
                 System.out.println("关闭socket");
                 Socket socket =(Socket)o;
                 if(socket.isClosed()==false)
                    socket.close();
             }
         }
    }


    public static void main(String[] args) throws IOException {
        FileServer fileServer = new FileServer();
        //开启服务器
        fileServer.start(9999);
    }

}
```

主要看向ServerTask这个多线程类，里面写了一些多线程的处理逻辑

```java

public class ServerTask implements Runnable{
    Socket socket;

    public ServerTask(Socket socket) {
        this.socket = socket;
    }

    /**
     * 执行任务
     */
    public void executeTask(){
        try{
            //开始接受客户传递过来的文件
            InputStream inputStream = socket.getInputStream();
            //这里定义一个路径，读取文件后存放的路径
            File file = new File("E:\\javaProject\\rpc\\src\\com\\haizhang\\socketDemo");
            if(!file.exists()){
                System.out.println("不存在");
                //不存在则创建
                file.mkdir();
            }
            //文件名下载设为Client.java
            OutputStream outputStream = new FileOutputStream(file+"\\Client.java");
            //读取文件的字节
            byte [] bytes =new byte[1024];
            int len = 0;
            long timeBegin = System.currentTimeMillis();
            System.out.println("开始读取文件");
            if(( len = inputStream.read(bytes))!=-1){
                outputStream.write(bytes,0,len);
            }
            long timeEnd = System.currentTimeMillis();
            long took = timeEnd - timeBegin;
            System.out.println("读取文件结束;耗时"+took);
//            //反馈给客户端
            OutputStream outputStream1 =  socket.getOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream1);
            objectOutputStream.writeUTF("完成读取文件！");
            objectOutputStream.flush();
            System.out.println("读取成功！");
        } catch (IOException e) {
            e.printStackTrace();
             OutputStream outputStream2 =  socket.getOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream2);
            objectOutputStream.writeUTF("读取文件失败！"+e.getMessage());
            objectOutputStream.flush();
            System.out.println("读取失败！"+e.getMessage());
        }
    }

    @Override
    public void run() {
        executeTask();
    }
}

```

**重点在客户端**

客户端需要找到服务器所在的电脑的ip 比如当前的服务器ip是

![1563777796209](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563777796209.png)

观测到连接特定的DNS后缀为meizu.com这项的IPv4地址就是服务器所在的可以被解析的ip地址（否则无法正确的连接！）

```java
public class Client{
    public static void main(String[] args) {
        try {
            //这里上传文件到socket
            //自己的ip或者远程的ip
            Socket socket = new Socket("172.29.144.60", 9089);
            //上传文件
            String fPath = "D:\\socketTest\\src\\com\\haizhang\\soketTest\\Demo.java";
            InputStream inputStream = new FileInputStream(fPath);
            OutputStream outputStream = socket.getOutputStream();
            byte[] bytes = new byte[1024];
            int len = 0;
            if ((len = inputStream.read(bytes)) != -1) {
                //开始读
                outputStream.write(bytes, 0, len);
            }
            ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
            String msg = objectInputStream.readUTF();
            System.out.println(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

