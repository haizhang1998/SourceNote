##Spring扩展原理----@EventListener与SmartInitializingSinglton

对事件的监听不单单可以实现接口ApplicationListener去完成监听。更加简单的做法是**创建@EventListener注解，标注在目标监听方法上，并且指定监听的类型，便可以同ApplicationListener一样进行事件的监听了**

动手实际下：

```java
//创建一个类，声明一个事件处理方法，并标注@EventListener注解
@Component
public class MyEventListener  {

    @EventListener(classes = ApplicationEvent.class)
    public void listenApplicationEvent(ApplicationEvent applicationEvent){
        System.out.println("使用EventListenr注解捕获到事件:【"+applicationEvent+"】");
    }
}


//配置类
@ComponentScan
@Configuration
public class EtxConfiguration4 {
}

//测试方法
  @Test
    public void testEventListener(){
        AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext(EtxConfiguration4.class);
        //定义一个自己发布的事件
        annotationConfigApplicationContext.publishEvent(new ApplicationEvent(new String("自己发布的事件")) {
        });
        annotationConfigApplicationContext.close();
}

```

运行结果

![1564729005873](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564729005873.png)

看到上面的运行结果和之前使用类实现ApplicationListener接口得到的结果是一样的。

你可能很好奇这个注解是如何达到这种效果呢？我们继续看其内部原理：

```java
/ *
 * @author Stephane Nicoll
 * @since 4.2
 * 主要起作用的是EventListenerMethodProcessor
 * @see EventListenerMethodProcessor
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventListener {
	@AliasFor("classes")
	Class<?>[] value() default {};


    //指定监听的ApplicationEvent类，比如可以只监听容器初始化事件ContextRefreshedEvent.class
	@AliasFor("value")
	Class<?>[] classes() default {};

    //这里主要是可以利用spel表达式定义不会监听事件的条件
	String condition() default "";
}
```

我们看向EventListenerMethodProcessor，**正是因为这个类才能使得我们的EventListner得到解析**

```java
//EventListenerMethodProcessor的继承关系
public class EventListenerMethodProcessor implements SmartInitializingSingleton, ApplicationContextAware {
```

这里我们重点看这个类的继承关系，发现有一个SmartInitializingSingleton接口

```java
/**
 * Callback interface triggered at the end of the singleton pre-instantiation phase
 * during {@link BeanFactory} bootstrap. This interface can be implemented by
 * singleton beans in order to perform some initialization after the regular
 * singleton instantiation algorithm, avoiding side effects with accidental early
 * initialization (e.g. from {@link ListableBeanFactory#getBeansOfType} calls).
 * In that sense, it is an alternative to {@link InitializingBean} which gets
 * triggered right at the end of a bean's local construction phase.
 *
 * <p>This callback variant is somewhat similar to
 * {@link org.springframework.context.event.ContextRefreshedEvent} but doesn't
 * require an implementation of {@link org.springframework.context.ApplicationListener},
 * with no need to filter context references across a context hierarchy etc.
 * It also implies a more minimal dependency on just the {@code beans} package
 * and is being honored by standalone {@link ListableBeanFactory} implementations,
 * not just in an {@link org.springframework.context.ApplicationContext} environment.
 *
 * <p><b>NOTE:</b> If you intend to start/manage asynchronous tasks, preferably
 * implement {@link org.springframework.context.Lifecycle} instead which offers
 * a richer model for runtime management and allows for phased startup/shutdown.
 *
 * @author Juergen Hoeller
 * @since 4.1
 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory#preInstantiateSingletons()
 */
public interface SmartInitializingSingleton {
	//当所有的单实例bean创建完成后才会执行这个方法
	void afterSingletonsInstantiated();
}
```

我们看看这个SmartInitializingSingleton何时被触发，

1. 创建ioc容器对象refresh();

2. finishBeanFactoryInitialization(beanFactory);初始化所有剩下的单实例bean

3. beanFactory.preInstantiateSingletons();

   1. 先创建所有的单实例bean，利用一个for循环拿到所有的单实例bean

   2. 获取所有创建好的单实例bean，判断是否是smartInitialization这个接口类型的。

      如果是就调用smartSingleton.afterSingletonsInstantiated();

      