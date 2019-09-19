# spring注解开发----Web开发（一）

这一篇我们先暂时抛弃springMvc框架，重新回到最原始的Servlet，探究其内部的一些原理

首先我们回顾下最原生的servlet开发

简单的案例：定义一个login.jsp，内部附上连接跳转到/login进行逻辑处理，然后将逻辑处理结果返回

很简单，我们按以下步骤定义即可：

1. 在maven的pom下面导入依赖

   ```text
       <!-- jstl -->
       <dependency>
         <groupId>javax.servlet</groupId>
         <artifactId>jstl</artifactId>
         <version>1.2</version>
       </dependency>
       <!-- servlet api -->
       <dependency>
         <groupId>javax.servlet</groupId>
         <artifactId>javax.servlet-api</artifactId>
         <version>3.1.0</version>
       </dependency>
   ```

2. 新建LoginServlet类

   ```java
   //一定要声明访问路径，否则jsp无法连接到这里
   @WebServlet("/login")
   public class LoginServlet extends HttpServlet {
       @Override
       protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
   //        super.doGet(req, resp);
           //接受到/login请求之后就会调用doGet方法内部逻辑，并将hello！写出到输出流返回客户
           resp.getWriter().write("hello!");
       }
   }
   ```

3. 定义login.jsp

   ```js
   <%@ page contentType="text/html;charset=UTF-8" language="java" %>
   <html>
   <head>
       <title>Title</title>
   </head>
   <body>
   <a href="/login">登录</a>
   </body>
   </html>
   ```

4. 配置tomcat，步骤略去

5. 测试截图：

   ![1565231554775](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565231554775.png)

上面就是最原生的使用类继承HttpServlet并重写doGet/doPost方法来处理请求操作。我们其实还可以像类似于spring中，在某个bean创建之前获取容器中的某些信息，接下来就去介绍ServletContainerInitializer，创建Servlet容器的时候会调用它的实现类导入你喜欢的组件。

#### ServletContainerInitializer

servlet容器在启动应用的时候，会扫描当前应用每一个jar包里面**META-INF/services/javax.servlet.ServletContainerInitializer** 指定的实现类，这个文件的内容就是ServletContainerInitializer的**全类名**，启动并运行这个实现类的方法，我们需要如下去定义这个接口的实现类.

**实现步骤:**

1. 定义一个接口以及继承它的抽象类还有继承抽象类的子类

```java
public interface MyTestInterface {
}

public class AbstractTest implements MyTestInterface {
}
public class SonClass extends AbstractTest {
}

```

2. 创建一个类实现ServletContainerInitializer 

```java
package servletContainerInitilizer;
/**
 * 实现servlet容器的初始化器
 */
//容器启动的时候会将HandlesTypes中指定的类型的子类以及实现类全部传入进来，最终会在onStartup中的set参数中存放这些类型
@HandlesTypes(MyTestInterface.class)
public class MyServletContainerInitializer implements ServletContainerInitializer {
    /**
     *在Web容器启动的时候会去调用这个方法
     * @param set   存放所有HandlesType的子类型
     * @param servletContext   一个Web应用一个ServletContext
     * @throws ServletException
     */
    @Override
    public void onStartup(Set<Class<?>> set, ServletContext servletContext) throws ServletException {
        System.out.println(111);
          for(Class<?> s: set)
          {
              System.out.println("HandlesTypes:"+s);
          }
    }
}

```

3. 在src目录下创建一个目录**META-INF/services/ ** 并声明一个文件javax.servlet.ServletContainerInitializer ，然后在这个文件内部写上上面类的全类名

![1565233045959](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565233045959.png)

测试重启tomcat观测控制台：

![1565233184613](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565233184613.png)





### 使用ServletCOntext注册WEB组件（Servlet，Filter，Listener）

同样，我们是可以利用上面的ServletContextInitilizer去注册WEB组件的,为了在容器启动时注册这些组件，我们声明这些组件

1. 实现Filter

```java

public class RequestFilter implements Filter{
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        System.out.println("filter init");
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        System.out.println("DOfILTER。。。"+servletRequest.getRemoteHost()+":::"+servletRequest.getContentType());
        //放行
        filterChain.doFilter(servletRequest,servletResponse);
    }

    @Override
    public void destroy() {
        System.out.println("filter destroy");
    }
}

```

2. 实现Listener

```java
public class MyListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        System.out.println("容器启动.."+servletContextEvent.getServletContext().getContextPath());
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        System.out.println("容器销毁.."+servletContextEvent.getServletContext().getContextPath());
    }
}

```

3. 实现Servlet

```java
//注意这里实现没有配@WebServlet标签。交给ServletContext中再进行绑定
public class LoginServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//        super.doGet(req, resp);
        resp.getWriter().write("hello!");
    }
}

```

4. 实现ServletContainerInitializer

```java
/**
 * 实现servlet容器的初始化器
 */
//容器启动的时候会将HandlesTypes中指定的类型的子类以及实现类全部传入进来，最终会在onStartup中的set参数中存放这些类型
@HandlesTypes(MyTestInterface.class)
public class MyServletContainerInitializer implements ServletContainerInitializer {
    /**
     *在Web容器启动的时候会去调用这个方法
     * @param set   存放所有HandlesType的子类型
     * @param servletContext   一个Web应用一个ServletContext
     * @throws ServletException
     */
    @Override
    public void onStartup(Set<Class<?>> set, ServletContext servletContext) throws ServletException {
        System.out.println(111);
          for(Class<?> s: set)
          {
              System.out.println("HandlesTypes:"+s);
          }

          //往ServletContext注入三大组件
        //servlet名字===》类
        ServletRegistration.Dynamic dynamic = servletContext.addServlet("loginServlet", new LoginServlet());
          //对应的映射路径
          dynamic.addMapping("/login");

        //注册Listener
        servletContext.addListener(MyListener.class);
        //注册Filter
        FilterRegistration.Dynamic requestFilter = servletContext.addFilter("requestFilter", RequestFilter.class);
        //绑定所有request的请求都放行
        requestFilter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST),true,"/*");
    }
}
```

上面我们运行起来后发现组件都是已经起作用了，这里springmvc它也是这样为容器注册组件及配置对应的映射路径的。

运行结果部分截图：

![1565244051276](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565244051276.png)

下一篇介绍springmvc运用这种模式的整合