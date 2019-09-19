##Spring注解驱动开发----Web开发（二）

我们回顾一下，以前我们在使用springmvc框架写程序的时候，是不是都要进行配置xml

```java
<web-app>
    <context-param>
        <param-name>contextConfigLocation</param-name>
        <param-value>/WEB-INF/root-context.xml</param-value>
    </context-param>
    <servlet>
        <servlet-name>dispatcher</servlet-name>
        <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
        <init-param>
            <param-name>contextConfigLocation</param-name>
            <param-value></param-value>
        </init-param>
        <load-on-startup>1</load-on-startup>
    </servlet>
    <servlet-mapping>
        <servlet-name>dispatcher</servlet-name>
        <url-pattern>/*</url-pattern>
    </servlet-mapping>
    <listener>
        <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
    </listener>
</web-app>
```

有了dispatchServlet我们才可以进行捕捉请求，然后再将这些请求通过各种映射器映射到我们特定的处理类。但是这些映射路径究竟时怎么去加载到，换句话说，spring容器究竟如何知道哪个路径映射到哪个类呢？所以接下来我们要介绍SpringServletContainerInitializer，这里相当于实现了我们上一篇说的ServletContainerInitializer，注册进了许多组件。

**SpringServletContainerInitializer机制**

我们在使用springmvc的时候需要导入一些包

```java
 <dependency>
      <groupId>javax.servlet</groupId>
      <artifactId>javax.servlet-api</artifactId>
      <version>3.1.0</version>
    </dependency>
    <dependency>
      <groupId>org.springframework</groupId>
      <artifactId>spring-webmvc</artifactId>
      <version>4.3.8.RELEASE</version>
    </dependency>
    <dependency>
      <groupId>javax.servlet</groupId>
      <artifactId>servlet-api</artifactId>
      <version>3.0-alpha-1</version>
      <scope>provided</scope>
    </dependency>
```

成功导入了之后，你可以看到spring-web包下面有一个META-INF/services/目录，存放着我们上一篇所说的ServletContainerInitializer文件，**容器在启动的时候会优先加载这个文件指定的实现类，调用其方法为实现类注册进容器运作的组件!**

![1565245471687](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565245471687.png)

这个文件内部指向的就是SpringServletContainerInitializer，它是实现了ServletContainerInitializer接口的类。我们看看它究竟为容器注册了什么

```java
//首先会将WebApplicationInitializer接口的实现类以及其子接口等，全部加载进来，并存放在webAppInitializerClasses参数中
@HandlesTypes({WebApplicationInitializer.class})
public class SpringServletContainerInitializer implements ServletContainerInitializer {
    
    public SpringServletContainerInitializer() {
    }

    //spring容器启动会调用这个方法注册组件
    public void onStartup(Set<Class<?>> webAppInitializerClasses, ServletContext servletContext) throws ServletException {
        
        List<WebApplicationInitializer> initializers = new LinkedList();
        Iterator var4;
        //如果这些注册的组件非空
        if (webAppInitializerClasses != null) {
            //拿到他们的迭代器
            var4 = webAppInitializerClasses.iterator();
            //遍历这些组件
            while(var4.hasNext()) {
                Class<?> waiClass = (Class)var4.next();
                //检测当前组件是否为接口，是否非抽象，是否是WebApplicationInitalizer类型的
                if (!waiClass.isInterface() && !Modifier.isAbstract(waiClass.getModifiers()) && WebApplicationInitializer.class.isAssignableFrom(waiClass)) {
                    try {
                        //如果通过校验就九江这个组件加入到组件数组中，并创建该组件的实例对象！
                       initializers.add((WebApplicationInitializer)waiClass.newInstance());
                    } catch (Throwable var7) {
                        throw new ServletException("Failed to instantiate WebApplicationInitializer class", var7);
                    }
                }
            }
        }

        //如果是空的化，就打印log
        if (initializers.isEmpty()) {
            servletContext.log("No Spring WebApplicationInitializer types detected on classpath");
        } else {
            servletContext.log(initializers.size() + " Spring WebApplicationInitializers detected on classpath");
            //组件排序
            AnnotationAwareOrderComparator.sort(initializers);
            var4 = initializers.iterator();
            
            while(var4.hasNext()) {
                WebApplicationInitializer initializer = (WebApplicationInitializer)var4.next();
                //这里回调我们创建的WebApplicationInitializer 实现类的onStartup方法注册自己的组件到servletContext中
                initializer.onStartup(servletContext);
            }

        }
    }
}
```

上面代码注释的步骤应该很清楚了，莫非就是遍历所有WebApplicationInitializer.class的子接口实现类等，然后分别调用他们的onStartup组测自己的组件到servletContext中。

我们想想WebApplicationInitializer.class究竟是什么呢？

![1565246080775](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1565246080775.png)

它分别有三层继承实现关系，这里列举下，最外层是实现类

```java
AbstractAnnotationConfigDispatcherServletInitializer ---->继承
     AbstractDispatcherServletInitializer  --->继承
         AbstractContextLoaderInitializer  --->实现
             WebApplicationInitializer     
```

大体的逻辑清晰可见了。

1. AbstractContextLoaderInitializer来创建根容器createRootApplicationContext();

   ```java
   //这个类主要在容器启动时候调用此方法添加组件
   protected void registerContextLoaderListener(ServletContext servletContext) {
           //创建一个父级容器，这createRootApplicationContext();的实现交给用户
   		WebApplicationContext rootAppContext = createRootApplicationContext();
   		if (rootAppContext != null) {
   			ContextLoaderListener listener = new ContextLoaderListener(rootAppContext);
   			listener.setContextInitializers(getRootApplicationContextInitializers());
   			servletContext.addListener(listener);
   		}
   		else {
   			logger.debug("No ContextLoaderListener registered, as " +
   					"createRootApplicationContext() did not return an application context");
   		}
   	}
   ```

   

2. AbstractDispatcherServletInitializer 相当于DispatcherServlet的初始化器createServletApplicationContext();

   ```java
   //注册DispatcherServlet
   protected void registerDispatcherServlet(ServletContext servletContext) {
           //拿到DispatcherServlet名字
   		String servletName = getServletName();
   		Assert.hasLength(servletName, "getServletName() must not return empty or null");
           //创建web容器
   		WebApplicationContext servletAppContext = createServletApplicationContext();
   		Assert.notNull(servletAppContext,
   				"createServletApplicationContext() did not return an application " +
   				"context for servlet [" + servletName + "]");
           //创建前端控制器createDispatcherServlet
   		FrameworkServlet dispatcherServlet = createDispatcherServlet(servletAppContext);
     //这里设置web容器的初始化组件，交给用户去完成这边的逻辑 
      dispatcherServlet.setContextInitializers(getServletApplicationContextInitializers());
     //添加创建好的dispatcherServlet
   		ServletRegistration.Dynamic registration = servletContext.addServlet(servletName, dispatcherServlet);
   		Assert.notNull(registration,
   				"Failed to register servlet with name '" + servletName + "'." +
   				"Check if there is another servlet registered under the same name.");
   
   		registration.setLoadOnStartup(1);
       //添加映射路径，映射路径也是交给用户完善
   		registration.addMapping(getServletMappings());
       //异步支持
   		registration.setAsyncSupported(isAsyncSupported());
       //设置过滤器，用户实现
   		Filter[] filters = getServletFilters();
   		if (!ObjectUtils.isEmpty(filters)) {
   			for (Filter filter : filters) {
   				registerServletFilter(servletContext, filter);
   			}
   		}
       //做其他注册的操作
   		customizeRegistration(registration);
   	}
   ```

   * 创建一个web的ioc容器
   * 创建了一个DispatcherServlet
   * 将创建的DispatcherServlet利用servletContext的api添加到ServletContext种
   * 添加映射路径

3. AbstractAnnotationConfigDispatcherServletInitializer 注解方式配置的DispatcherServlet的初始化器

   ```java
   public abstract class AbstractAnnotationConfigDispatcherServletInitializer
   		extends AbstractDispatcherServletInitializer {
   
   	/**
   	 * {@inheritDoc}
   	 * <p>This implementation creates an {@link AnnotationConfigWebApplicationContext},
   	 * providing it the annotated classes returned by {@link #getRootConfigClasses()}.
   	 * Returns {@code null} if {@link #getRootConfigClasses()} returns {@code null}.
   	 */
   	@Override
   	protected WebApplicationContext createRootApplicationContext() {
           //创建根容器逻辑AnnotationConfigWebApplicationContext
           // getRootConfigClasses();自行实现
   		Class<?>[] configClasses = getRootConfigClasses();
   		if (!ObjectUtils.isEmpty(configClasses)) {
   			AnnotationConfigWebApplicationContext rootAppContext = new AnnotationConfigWebApplicationContext();
   			rootAppContext.register(configClasses);
   			return rootAppContext;
   		}
   		else {
   			return null;
   		}
   	}
   
   	/**
   	 * {@inheritDoc}
   	 * <p>This implementation creates an {@link AnnotationConfigWebApplicationContext},
   	 * providing it the annotated classes returned by {@link #getServletConfigClasses()}.
   	 */
   	@Override
   	protected WebApplicationContext createServletApplicationContext() {
   		AnnotationConfigWebApplicationContext servletAppContext = new AnnotationConfigWebApplicationContext();
           //自行实现getServletConfigClasses();
   		Class<?>[] configClasses = getServletConfigClasses();
   		if (!ObjectUtils.isEmpty(configClasses)) {
   			servletAppContext.register(configClasses);
   		}
   		return servletAppContext;
   	}
   
   	/**
   	 * Specify {@link org.springframework.context.annotation.Configuration @Configuration}
   	 * and/or {@link org.springframework.stereotype.Component @Component} classes to be
   	 * provided to the {@linkplain #createRootApplicationContext() root application context}.
   	 * @return the configuration classes for the root application context, or {@code null}
   	 * if creation and registration of a root context is not desired
   	 */
   	protected abstract Class<?>[] getRootConfigClasses();
   
   	/**
   	 * Specify {@link org.springframework.context.annotation.Configuration @Configuration}
   	 * and/or {@link org.springframework.stereotype.Component @Component} classes to be
   	 * provided to the {@linkplain #createServletApplicationContext() dispatcher servlet
   	 * application context}.
   	 * @return the configuration classes for the dispatcher servlet application context or
   	 * {@code null} if all configuration is specified through root config classes.
   	 */
   	protected abstract Class<?>[] getServletConfigClasses();
   
   }
   ```

   * 创建根容器

   * 创建webIOC容器，获取配置类：getServletConfigClasses，以及getRootConfigClasses()

     这两个类要在AbstractAnnotationConfigDispatcherServletInitializer实现类种获取。

总结：

​	以注解方式来启动SpringMVC 继承AbstractAnnotationConfigDispatcherServletInitializer 实现抽象方法去指定DispatcherServlet的配置信息；

**根容器(RootWebApplicationContext)和Web容器（ServletWebApplicationContext）有什么区别呢？**

![mvc context hierarchy](https://docs.spring.io/spring/docs/4.3.25.RELEASE/spring-framework-reference/htmlsingle/images/mvc-context-hierarchy.png)

这是Spring管往给出的图，根容器去扫描Services，Repositories等业务逻辑组件。而Web容器呢主要是扫描Controllers，HanlerMapping，ViewResolver等控制层组件。



####使用java方式整合springMVC

我们可以利用java注解的方式来装配上面的父子容器

```java
public class GolfingWebAppInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {
    //获取根容器配置列：（spring的配置文件）父级容器
   @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class<?>[]{RootConfig.class};
    }

    //获取web容器的配置类（springMvc配置文件）子容器
    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[]{AppConfig.class};
    }

    //获取DispatcherServlet的映射信息
     @Override
    protected String[] getServletMappings() {
        //让disppatcherServlet捕获全部的路径
        return new String[]{"/"};
    }
 
}
```

**配置根容器**

```java
/**
 * 根容器，加载逻辑组件，注意不要扫描controller
 */
@ComponentScan(value = {"com.haizhang"},excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION,classes = {Controller.class})
})
public class RootConfig {
}


/**
 * 配置springmvc容器，只扫描controller
 */
@ComponentScan(value = {"com.haizhang"},includeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION,classes = Controller.class)
},useDefaultFilters = false)
public class AppConfig {
}

```

Service和Controller的实现

```java
public interface HelloService {
    public String sayHello(String name);
}


@Service
public class HelloServiceImpl  implements HelloService{
    @Override
    public String sayHello(String name) {
        return "springmvc say hello to "+ name;
    }
}


@Controller
public class HelloController {

    @Resource
    HelloService helloService;

    @RequestMapping("/hello")
    @ResponseBody
    public String sayHello(String name){
            String res=helloService.sayHello(name);
            return res;
    }
}

```

现在你可以运行程序了，结果就不截图啦，自己测试即可。



**spring MVC的定制**

我们之前会在spring-mvc.xml文件种配置了很多标签

```java
//当servlet无法处理的时候交给tomcat容器处理，比如一些静态的资源等
<mvc:default-servlet-handler/>
//开启mvc注解驱动
<mvc:annotation-driven>
    <mvc:path-matching
        suffix-pattern="true"
        trailing-slash="false"
        registered-suffixes-only="true"
        path-helper="pathHelper"
        path-matcher="pathMatcher"/>
</mvc:annotation-driven>
。。。
```

现在没有了这些注解要如何定制一些规则呢，注解通通可以搞定

比方说我们现在需要配置default-servlet-handler这个功能，我们需要到上面定义的AppConfig种进行配置，这个类主要是加载到我们的DispatcherServlet中的。我们需要继承WebMvcConfigurerAdapter这个类，去选择需要添加的功能

```java
/**
 * 配置springmvc容器，只扫描controller
 */

/**
 * 配置springmvc容器，只扫描controller
 */
@ComponentScan(value = {"com.haizhang"},includeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION,classes = Controller.class)
},useDefaultFilters = false)
//这一步必须！ 不然这些组件讲不能够被调用！
@EnableWebMvc
public class AppConfig extends WebMvcConfigurerAdapter {
    @Override
    public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
        //这一句就等同于<mvc:default-servlet-handler/>
        configurer.enable();
    }

    //配置viewResolver
    @Override
    public void configureViewResolvers(ViewResolverRegistry registry) {
        //return jsp("/WEB-INF/", ".jsp");默认的配置是在/WEB-INF/路径下去找后缀为jsp的文件
        registry.jsp("/views/",".jsp");
        System.out.println();
    }

    //配置拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //拦截所有路径
        registry.addInterceptor(new MyFirstInteceptor()).addPathPatterns("/**");
    }
}

```

上面基本上装配了拦截器，静态资源解析器，视图解析器这些功能，完全替代了spring-mvc.xml







