# Spring注解驱动开发

### 第一模块@ComponentScan

@ComponentScan() 通常标注在@Configuration配置类之中，而@Configuration实质上也是@Component的，也是个组件。现在重点研究@ComponentScan的使用

```java
@Configuration
/**
 * 扫描springAnnotationAndSourceTest下的每个包的每个类，我们在这里可以定义过滤规则
 * 1. excludeFilters 需要排除哪些内容在这里定义Filters进行匹配过滤
 *    type = FilterType.ANNOTATION 表示过滤准则采用Annotation，而classes是所有value指定的扫描路径下@Service标注的类都会被过滤
 *    type = FilterType.ASSIGNABLE_TYPE 表示过滤准则采用类名指定的过滤，而classes是所有value指定的扫描路径下满足指定类的类都会被过滤
 *    type = FilterType.CUSTOM 表示过滤准则采用自定义准则的过滤，而classes是定义过滤准则的类，所有满足match方法判断为true的类文件都会被过滤
 */
@ComponentScan(value={"springAnnotationAndSourceTest"},excludeFilters = {
        @ComponentScan.Filter(type = FilterType.CUSTOM,classes = {CustomerFilter.class})
//          @ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes={UserService.class})
//        @ComponentScan.Filter(type = FilterType.ANNOTATION,classes = {Service.class})
})
@EnableAspectJAutoProxy
public class Config {
}

```

我们主要看像自定义的过滤器怎么设计，只要实现TypeFilter接口的match方法即可通过match的逻辑过滤类，像ClassMetadata可以拿到一个类文件的所有信息，通过他们就可以达到过滤的目标（return true）

```java

/**
 * 凡是实现了TypeFilter的类，就可以自定义@ComponentScan的扫描准则
 */
public class CustomerFilter implements TypeFilter {
    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) throws IOException {
        //得到资源
        Resource resource = metadataReader.getResource();
        AnnotationMetadata annotationMetadata = metadataReader.getAnnotationMetadata();
        ClassMetadata classMetadata = metadataReader.getClassMetadata();
        //每次@ComponentScan做扫描的时候会获取metadataReader，然后再获取ClassMetadata，类元素信息。检测其class的名字
        //如果包含Dao关键字，return true 也就是满足过滤的要求，过滤掉
        if(classMetadata.getClassName().contains("Dao")){

            return true;
        }
        return false;
    }
}

```

结果没有后最为Dao的类了

![1563779600040](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563779600040.png)



### 第二模块 @Scope

@Scope有以下的几个作用域,我们看向@Scope注解的源码

```java
//标注在类和方法上
@Target({ElementType.TYPE, ElementType.METHOD})
//运行时有效
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Scope {

	/**
	 * Alias for {@link #scopeName}.
	 * @see #scopeName
	 */
	@AliasFor("scopeName")
	String value() default "";

	/**
	 * Specifies the name of the scope to use for the annotated component/bean.
	 * <p>Defaults to an empty string ({@code ""}) which implies
	 * {@link ConfigurableBeanFactory#SCOPE_SINGLETON SCOPE_SINGLETON}.
	 * @since 4.2
	        
	 * @see ConfigurableBeanFactory#SCOPE_PROTOTYPE  原型类型，bean不会第一时间扫描后就在spring容器构建，而是当getBean用到这个bean的时候才会构建
	 * @see ConfigurableBeanFactory#SCOPE_SINGLETON  单列类型，bean的默认类型，不管调用多少次，这个bean只存在一个在spring容器中
	 * @see org.springframework.web.context.WebApplicationContext#SCOPE_REQUEST  必须实在web环境下才有效，表示request同一个请求才会创建
	 * @see org.springframework.web.context.WebApplicationContext#SCOPE_SESSION  必须在web环境下才有效，表示一个session创建一次不同的bean。
	 * @see #value
	 */
	@AliasFor("value")
	String scopeName() default "";

	/**
	 * Specifies whether a component should be configured as a scoped proxy
	 * and if so, whether the proxy should be interface-based or subclass-based.
	 * <p>Defaults to {@link ScopedProxyMode#DEFAULT}, which typically indicates
	 * that no scoped proxy should be created unless a different default
	 * has been configured at the component-scan instruction level.
	 * <p>Analogous to {@code <aop:scoped-proxy/>} support in Spring XML.
	 * @see ScopedProxyMode
	 */
	ScopedProxyMode proxyMode() default ScopedProxyMode.DEFAULT;

}
```

有了上面的了解，可以进行尝试了，我们这里只看一个**prototype**和**singleton** 的区别,我们定义一个Man且有一个构造方法

1. prototype模式下

   ```java
   @Component("man")
   //原型模式，不会再spring启动时创建，而是用一次创建一个
   @Scope(value = "prototype")
   public class Man {
   
       public Man(){
           System.err.println("create Man");
       }
   }
   ```

   测试：

   ![1563780689510](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563780689510.png)

   上面结果看到很明显每次getBean，地址都是不一样的。说明每次调用man时候会全新的new一个对象！

2. singleton模式下

   ![1563780843809](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563780843809.png)

​       很清晰，只有一次调用Man的构造方法，得到的地址全部一样，说明只有一个bean再spring容器中！



### 第三模块 @Lazy 懒加载

spring容器中创建bean总是一开始创建就进行加载创建bean，如果不想容器一创建就加载bean，而是想调用后第一次才创建bean。则应该在类上面标注@Lazy

```java
@Component("man")
//懒加载
@Lazy
public class Man {

    public Man(){
        System.err.println("create Man");
    }
}
```

测试及结果

![1563781607654](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563781607654.png)

观测到crateMan 这个构造方法并不是在spring创建时候才创建，而是创建完成spring容器后**第一次调用**man的时候才会创建对象。



### 第四模块 @Conditional 按照条件创建bean

如果我们想当某个条件满足的时候才创建bean，应该使用**@Conditional**注解，现在我们利用@Conditional来做一个案例：

电脑的操作系统windows的创建者时bill （比尔盖茨） 而linux操作系统创建者为linus（林纳斯）,而操作系统的问世的前提必须有Computer的存在，所以根据这几种关系，我们利用@Conditional进行判断是否存在Computer，再判断操作系统类型，再取生成对应条件下的bean。

**实现代码**

1. 生成4个类，ComputerConditon(电脑判断类）, LinuxOperCondition(linux操作系统判断类)，WindowsOperConditon(windows操作系统判断类)，Person（人类,用于记录创建者信息）

2. 创建完bean后我们主要实现处理Person的3个条件判断类，观测@Conditional源码

   ```java
   //可以作用再类和方法上
   @Target({ElementType.TYPE, ElementType.METHOD})
   @Retention(RetentionPolicy.RUNTIME)
   @Documented
   public @interface Conditional {
    
   	/**
   	 * 意思时，如果想要按自定义的类进行判断必须要实现Condition接口并实现其中的matches办法
   	 * All {@link Condition}s that must {@linkplain Condition#matches match}
   	 * in order for the component to be registered.
   	 */
   	Class<? extends Condition>[] value();
   
   }
   
   ```

   继续我们看到Condition的源码

   ```java
   public interface Condition {
   
   	/**
   	 * 如果matches方法返回true那么就达到了Condtion也就时满足条件，则bean就会被加载进spring容器中。
   	 * Determine if the condition matches.
   	 * @param context the condition context
   	 * @param metadata metadata of the {@link org.springframework.core.type.AnnotationMetadata class}
   	 * or {@link org.springframework.core.type.MethodMetadata method} being checked.
   	 * @return {@code true} if the condition matches and the component can be registered
   	 * or {@code false} to veto registration.
   	 */
   	boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata);
   
   }
   ```

3. 有了上面的了解，开始实现 ComputerConditon(电脑判断类）, LinuxOperCondition(linux操作系统判断类)，WindowsOperConditon(windows操作系统判断类) 

   ```java
   /**
    * 电脑bean
    */
   public class ComputerOper implements Condition {
       /**
        * 为了演示方便，我们对电脑的判断始终通过
        * @param context
        * @param metadata
        * @return
        */
       @Override
       public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
           return true;
       }
   }
   
   ```

   ```java
   /**
    * linux操作系统判断条件,必须继承Conditional
    */
   public class LinuxOper implements Condition {
       /**
        * @param context 上下文
        * @param metadata 注释具体信息
        * @return
        */
       @Override
       public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
           System.err.println("判断操作系统类型是否为"+this.getClass().getSimpleName());
           //spring容器的beanFactory
           ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
           //类加载器
           ClassLoader contextClassLoader = context.getClassLoader();
           //拿到spring容器的bean注册列表
           BeanDefinitionRegistry registry = context.getRegistry();
           //环境变量,可以拿到当前操作系统类型！
           Environment contextEnvironment = context.getEnvironment();
           //拿到当前的操作系统类型
           String os = contextEnvironment.getProperty("os.name");
           //首先判断是否存在ComputerOper的声明,其次判断当前操作系统是否为linux
           if( registry.containsBeanDefinition("computer")&& os.toLowerCase().contains("linux")){
               return true;
           }
           //否则不符合条件，抛弃该bean
           return false;
       }
   }
   
   ```

   ```java
   **
    * windows操作系统判断条件
    */
   public class WindowsOper implements Condition {
       /**
        * @param context 上下文
        * @param metadata 注释具体信息
        * @return
        */
       @Override
       public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
           System.err.println("判断操作系统类型是否为"+this.getClass().getSimpleName());
           //拿到spring容器的bean注册列表
           BeanDefinitionRegistry registry = context.getRegistry();
           //环境变量,可以拿到当前操作系统类型！
           Environment contextEnvironment = context.getEnvironment();
           //拿到当前的操作系统类型
           String os = contextEnvironment.getProperty("os.name");
           //首先判断是否存在ComputerOper的声明,其次判断当前操作系统是否为linux
           if( registry.containsBeanDefinition("computer")&& os.toLowerCase().contains("windows")){
               return true;
           }
           //否则不符合条件，抛弃该bean
           return false;
       }
   }
   
   ```

4. 上面的3个判断类我们需要写一个Config配置类进行调用

   ```java
   @Configuration
   public class Config {
       /**
        * 如果判断通过，创建电脑类
        * @return
        */
       @Conditional({ComputerOper.class})
       @Bean("computer")
       public ComputerOper computerOper(){
           return  new ComputerOper();
       }
   
       /**
        * 如果校验通过，则创建windows操作系统的创建者bill的信息
        * @return
        */
       @Conditional({WindowsOper.class})
       @Bean("bill")
       public Person person1(){
           return  new Person("bill");
       }
   
       /**
        *如果校验通过，则创建linux操作系统的创建者linus的信息
        * @return
        */
       @Conditional({LinuxOper.class})
       @Bean("linus")
       public Person person2(){
           return new Person("linus");
       }
   }
   ```

5. 所有的工作已经做完，现在进行测试

   ```java
       @Test
       public void testApp2() {
           ApplicationContext applicationContext = new AnnotationConfigApplicationContext(Config.class);
           //判断linus是否被加载进容器，如果有就拿到该对象
           Person inventor_linux = (applicationContext.containsBean("linus")?(Person) applicationContext.getBean("linus"):null);
            //判断bill是否被加载进容器，如果有就拿到该对象
           Person inventor_windows = (applicationContext.containsBean("bill")?(Person) applicationContext.getBean("bill"):null);
    
           System.out.println("当前的操作系统:"+applicationContext.getEnvironment().getProperty("os.name"));
           if(inventor_linux!=null && !StringUtil.isEmpty(inventor_linux.getName())){
               System.out.println(inventor_linux.getName());
           }
           if(inventor_windows!=null && !StringUtil.isEmpty(inventor_windows.getName())){
               System.out.println(inventor_windows.getName());
           }
       }
   ```

   结果截图：（当前操作系统Windows10）

   ![1563785477905](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563785477905.png)

6. 当然，如果你想要校验当操作系统为linux时的正确性，我们不必要切换操作系统，只需要设置以下VMoption虚拟机的环境变量追加 -Dos.name=linux 即可以看到对应的输出结果

   ![1563785664973](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563785664973.png)

![1563785687253](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563785687253.png)

7. 还有一点，如果把ComputerOper的match返回值设为false，是不会创建操作系统的作者的！因为操作系统的判断前提是Computer必需问世！

### 第五模块 @Import方式注入bean到spring容器

给容器注入组件总共有四种方式：

1. 包扫描+标注注解/ (@Component/@Repository/@Service/@Controller)

2. 在配置类中声明@Bean

3. **@Import**（快速给容器导入一个组件)

    3.1 @Import(要导入的组件.class)  会组件自动注册进spring容器（组件无需标注@Component） 而在spring容器中会把@Import进来的组件命名方式为组件的全类名

    3.2 @ImportSelector 返回要导入组件的全类名数组，相当于批量导入

    3.3 @ImportBeanDefinitionRegistrar可以直接将bean注册进BeanDefinationRegisty ,可以在注册前进行过滤操作。

  4.FactoryBean（下一模块介绍）

附上@Import注解的定义

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Import {
	/**
	 * {@link Configuration}, {@link ImportSelector}, {@link ImportBeanDefinitionRegistrar}
	 * or regular component classes to import.
	 */
	Class<?>[] value();

}

```

现在来分别动手实践以下

* **测试Import导入**

  创建4个类，分别是Green,Red,Yellow,RainBow. 

  ```java
  //package springAnnotationAndSourceTest.bean.importBeanToSpringIoc;
  public class Green {
  }
  
  //package springAnnotationAndSourceTest.bean.importBeanToSpringIoc;
  public class Red {
  }
  
  //package springAnnotationAndSourceTest.bean.importBeanToSpringIoc;
  public class Yellow {
  }
  
  //package springAnnotationAndSourceTest.bean.importBeanToSpringIoc;
  public class RainBow {
  }
  
  ```

  然后定义一个配置类@Import进Red yellow Green

  ```java
  package springAnnotationAndSourceTest.bean.importBeanToSpringIoc;
  
  import org.springframework.context.annotation.Configuration;
  import org.springframework.context.annotation.Import;
  @Configuration
  @Import(value = {Green.class,Red.class,Yellow.class})
  public class Config3 {    
  }
  ```

  运行测试：

  ![1563788814154](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563788814154.png)

​       结果很显然看到Bean都被导进来了，并且名字为它的全类名！

* 现在我们来看一下@Import中的ImportSelector选项

  源码 

  ```java
  /**
   * 整体的意思是可以自定义准则(criteria) 然后得到你想要导入的类，返回值是多个或者一个（是一个导入类的类名数组）
   * Interface to be implemented by types that determine which @{@link Configuration}
   * class(es) should be imported based on a given selection criteria, usually one or more
   * @author Chris Beams
   * @since 3.1
   * @see DeferredImportSelector
   * @see Import
   * @see ImportBeanDefinitionRegistrar
   * @see Configuration
   */
  public interface ImportSelector {
  
  	/**
  	 * Select and return the names of which class(es) should be imported based on
  	 * the {@link AnnotationMetadata} of the importing @{@link Configuration} class.
  	 */
  	String[] selectImports(AnnotationMetadata importingClassMetadata);
  }
  
  ```

  现在写一个MyImportSelector类实现ImportSelector接口

  ```java
  /**
   * 自定义类的导入规则
   */
  public class MyImportSelector implements ImportSelector {
      /**
       * @param importingClassMetadata 当前标注@Import的类的所有信息都可以在这里拿到
       * @return 所有要导入的类的全类名数组,注意不要返回null，就算没有导入的也要返回String[]{}
       */
      @Override
      public String[] selectImports(AnnotationMetadata importingClassMetadata) {
          //这里可以自定义一些准则 
          return new String[]       {"springAnnotationAndSourceTest.bean.importBeanToSpringIoc.Green"};
      }
  }
  
  ```

  将Config类的Import注解导入这个selector

  ```java
  @Configuration
  @Import(value = {MyImportSelector.class})
  public class Config3 {
  }
  ```

  测试结果可以看到只有一个Green的bean

  ![1563789798113](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563789798113.png)

 

* 接下来我们研究以下最后一个ImportBeanDefinitionRegistrar

  ```java
  public interface ImportBeanDefinitionRegistrar {
  
  	/**
  	 这个方法进行注册bean，直接放进BeanDefinitionRegistry中就可以在spring容器创建对应的bean
  	 * Register bean definitions as necessary based on the given annotation metadata of
  	 * the importing {@code @Configuration} class.
  	 * <p>Note that {@link BeanDefinitionRegistryPostProcessor} types may <em>not</em> be
  	 * registered here, due to lifecycle constraints related to {@code @Configuration}
  	 * class processing.
  	 * @param importingClassMetadata annotation metadata of the importing class
  	 * @param registry current bean definition registry
  	 */
  	public void registerBeanDefinitions(
  			AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry);
  
  }
  
  ```

  我们定义一个类取实现ImportBeanDefinitionRegistrar 

  ```java
  package springAnnotationAndSourceTest.bean.importBeanToSpringIoc;
  
  import org.springframework.beans.factory.support.BeanDefinitionRegistry;
  import org.springframework.beans.factory.support.RootBeanDefinition;
  import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
  import org.springframework.core.type.AnnotationMetadata;
  
  public class MyImportBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {
      /**
       *
       * @param importingClassMetadata 当前类的注解信息
       * @param registry 把所有需要添加进容器中的bean 调用BeanDefinitionRegistry.registerBeanDefinition手动注册进来
       */
      @Override
      public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
             //检查下Green Red  Yellow在不在容器
          if(registry.containsBeanDefinition("springAnnotationAndSourceTest.bean.importBeanToSpringIoc.Green")&&
          registry.containsBeanDefinition("springAnnotationAndSourceTest.bean.importBeanToSpringIoc.Red")&&
          registry.containsBeanDefinition("springAnnotationAndSourceTest.bean.importBeanToSpringIoc.Yellow")){
             //注册RainBow进入到Spring容器中
           RootBeanDefinition rootBeanDefinition = new RootBeanDefinition(RainBow.class);
           registry.registerBeanDefinition("rainBow",rootBeanDefinition);
  
          }
  
      }
  }
  
  ```

  在Config3类下做相应的导入

  ```java
  @Configuration
  @Import(value = {Red.class,Green.class,Yellow.class,MyImportBeanDefinitionRegistrar.class})
  public class Config3 {
  }
  ```

  测试结果

  ![1563796038211](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563796038211.png)



### 第六模块  FactoryBean<T> 将bean注入容器

使用FactoryBean将目标Bean进行”包装“（比如可以设置bean的创建方式）然后在配置类中注入FactoryBean所包装的类。 

首先定义一个类实现FactoryBean的方法

```java
package springAnnotationAndSourceTest.bean.importBeanToSpringIoc;

import org.springframework.beans.factory.FactoryBean;

public class ColorFactoryBean implements FactoryBean<Red> {
    /**
     * 创建目标对象，这里可以设置一些对象的初始化属性，将目标对象注入到容器中
     * @return
     * @throws Exception
     */
    @Override
    public Red getObject() throws Exception {
        return new Red();
    }

    /**
     * 得到目标对象的类型
     * @return
     */
    @Override
    public Class<?> getObjectType() {
        return Red.class;
    }

    /**
     * 判断是否为单例，如果不是则每次调用一次就会构造创建一次
     * @return
     */
    @Override
    public boolean isSingleton() {
        return false;
    }
}

```

其次修改Config3 ，添加@Bean注解

```java
    @Bean
    public ColorFactoryBean colorFactoryBean(){
        return  new ColorFactoryBean();
    }

```

结果截图：

![1563796999878](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563796999878.png)

上图中显示出colorFactoryBean已经创建好了，怎么确定Red类已经被加载进spring容器了呢？我们调整以下测试方法

```java
   @Test
    public void testApp3() {
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(Config3.class);
        String []names = applicationContext.getBeanDefinitionNames();
        Object bean1 = applicationContext.getBean("colorFactoryBean");
        System.err.println(bean1.getClass());
    }
```

![1563797289772](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563797289772.png)

明显的我们得到的对象类型确实是Red ，可是为什么不是ColorFactoryBean呢？ 其实在spring内部有定义，我们看一下关键部分

```java
public interface BeanFactory {

	/**
	 * Used to dereference a {@link FactoryBean} instance and distinguish it from
	 * beans <i>created</i> by the FactoryBean. For example, if the bean named
	 * {@code myJndiObject} is a FactoryBean, getting {@code &myJndiObject}
	 * will return the factory, not the instance returned by the factory.
	 */
	String FACTORY_BEAN_PREFIX = "&";

```

意思是当我们想要取到实现了FactoryBean的FactoryBean类型，就必须在getBean("&beanName") beanName前面加上"&"这个标志。

```java
        Object bean1 = applicationContext.getBean("&colorFactoryBean");
```

结果

![1563797774083](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563797774083.png)

