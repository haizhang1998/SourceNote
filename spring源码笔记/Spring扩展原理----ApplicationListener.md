# Spring扩展原理----ApplicationListener

ApplicationListener监听器的作用是监听容器中发布的事件，事件驱动模型开发。

**如果我们的类想要被ApplicationListener监听到，就必须要实现ApplicationEvent方法,我们同样可以自定义发布事件**，下面是ApplicationListener的源码

```java
/**
    ApplicationListener的实现接口，这里传入泛型参数必须是继承了ApplicationEvent的类
*/
public interface ApplicationListener<E extends ApplicationEvent> extends EventListener {
	/**
	 * Handle an application event.
	 * @param event the event to respond to
	 */
	void onApplicationEvent(E event);

}

```

现在我们实现一个自己的ApplicationListener监听事件并定义内部处理逻辑

```java
//定义ApplicationListener的组件
@Component
public class MyApplicationListener implements ApplicationListener {
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        //事件监听器的处理逻辑
        System.out.println("监听到一个事件【"+event+"】");
    }
}

//定义配置类扫描这个监听器

@ComponentScan
@Configuration
public class EtxConfiguration3 {
}

```

我们定义一个测试类，并且在内部**发布自己的事件**

```java
    @Test
    public void testApplicationListener(){
        AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext(EtxConfiguration3.class);
        //定义一个自己发布的事件
        annotationConfigApplicationContext.publishEvent(new ApplicationEvent(new String("自己发布的事件")) {
        });
        annotationConfigApplicationContext.close();

    }
```

运行结果：

```java
监听到一个事件【org.springframework.context.event.ContextRefreshedEvent[source=org.springframework.context.annotation.AnnotationConfigApplicationContext@7006c658: startup date [Fri Aug 02 11:07:56 CST 2019]; root of context hierarchy]】
监听到一个事件【springAopTest.AppTest$1[source=自己发布的事件]】
监听到一个事件【org.springframework.context.event.ContextClosedEvent[source=org.springframework.context.annotation.AnnotationConfigApplicationContext@7006c658: startup date [Fri Aug 02 11:07:56 CST 2019]; root of context hierarchy]】

```

可以看到，监听器监听到了3个事件

1. ContextRefreshedEvent  这个是容器初始化完毕之后触发的事件
2. 自己发布的事件
3. ContextClosedEvent 这是容器关闭的时候触发的事件

我们来研究下整个监听器的事件监听流程：

1. 【事件发布流程】：

   1. 我们打断点在下图处并debug观测调用栈

      ![1564716126381](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564716126381.png)

   2. 调用refresh(）方法，并调用其中的**finishRefresh();** 我们观测其中做了什么

   ```java
   publishEvent(new ContextRefreshedEvent(this));
   
   //publishEvent
   @Override
   public void publishEvent(ApplicationEvent event) {
   	publishEvent(event, null);
   }
   ```

   上面几行行代码表示finishRefresh()是ioc容器创建完成之后，发布了一个事件，这里事件监听器就会对其进行逻辑处理。我们看看这个publishEvent究竟如何进行事件的发布的

   ```java
   protected void publishEvent(Object event, ResolvableType eventType) {
           //对传入的事件判空
   		Assert.notNull(event, "Event must not be null");
   		if (logger.isTraceEnabled()) {
   			logger.trace("Publishing event in " + getDisplayName() + ": " + event);
   		}
            
   		// Decorate event as an ApplicationEvent if necessary
       //这里的操作是将这个event事件强制转成ApplicationEvent用于ApplicationListener对其进行监听
   		ApplicationEvent applicationEvent;
   		if (event instanceof ApplicationEvent) {
   			applicationEvent = (ApplicationEvent) event;
   		}
   		else {
   			applicationEvent = new PayloadApplicationEvent<Object>(this, event);
   			if (eventType == null) {
   				eventType = ((PayloadApplicationEvent)applicationEvent).getResolvableType();
   			}
   		}
   
   		// Multicast right now if possible - or lazily once the multicaster is initialized
           //接添加这个事件到earlyApplicationEvents如果它存在的话
   		if (this.earlyApplicationEvents != null) {
   			this.earlyApplicationEvents.add(applicationEvent);
   		}
   		else {
               //不存在就从容器获取事件多播器（派生器），然后将事件派发出去
   			getApplicationEventMulticaster().multicastEvent(applicationEvent, eventType);
   		}
   
   		// Publish event via parent context as well...
   		if (this.parent != null) {
   			if (this.parent instanceof AbstractApplicationContext) {
   				((AbstractApplicationContext) this.parent).publishEvent(event, eventType);
   			}
   			else {
   				this.parent.publishEvent(event);
   			}
   		}
   	}
   ```

   3. 上面的代码主要是获取事件多播器(getApplicationEventMulticaster())，然后将事件派发出去,我们看下其中的操作

   ```java
   //派发事件的操作	
   @Override
   	public void multicastEvent(final ApplicationEvent event, ResolvableType eventType) {
   		ResolvableType type = (eventType != null ? eventType : resolveDefaultEventType(event));
           //得到所有的事件监听器，将这个事件依次派发到这些监听器进行逻辑处理
   		for (final ApplicationListener<?> listener : getApplicationListeners(event, type)) {
               //如果又异步执行器
   			Executor executor = getTaskExecutor();
               //我们就用多线程的方法执行
   			if (executor != null) {
   				executor.execute(new Runnable() {
   					@Override
   					public void run() {
   						invokeListener(listener, event);
   					}
   				});
   			}
               //没有的话就按顺序直接执行
   			else {
   				invokeListener(listener, event);
   			}
   		}
   	}
   ```

   4. 上面的方法也就是将当前事件，遍历所有ioc容器中的ApplicationListener，并传入到其中，如果又Executor这个任务执行器，就可以用多线程的方法进行执行监听处理方法，没有的话就依次调用执行（相当于串行执行）listener.onApplicationEvent(event); 

2.【事件多播器】

1. 上面我们看了要利用**事件多播器**将事件传入多个ApplicationListener中处理。那么事件多播器又是如何得到的呢？
2. 首先ioc容器对象要进行创建并调用refresh（）方法
3. 其次呢在refresh()方法内部调用initApplicationEventMulticaster();去创建事件多播器

```java
protected void initApplicationEventMulticaster() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
    //先去容器中获取有没有名字为APPLICATION_EVENT_MULTICASTER_BEAN_NAME的组件
		if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
            //直接返回容器中存在的事件派发器
			this.applicationEventMulticaster =
					beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using ApplicationEventMulticaster [" + this.applicationEventMulticaster + "]");
			}
		}
		else {
            //如果容器中事先没有创建事件派生器组件，那就创建一个
			this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
            //bean工厂注册这个事件派生器组件
			beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, this.applicationEventMulticaster);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate ApplicationEventMulticaster with name '" +
						APPLICATION_EVENT_MULTICASTER_BEAN_NAME +
						"': using default [" + this.applicationEventMulticaster + "]");
			}
		}
	}
```

上面代码就是创建派生器的获取以及创建流程。

3. 【容器中有哪些监听器】

   事件派发器又是如何知道容器中有哪些监听器呢，他们是如何创建的呢？基本流程如下：

   1. 首先ioc容器对象要进行创建并调用refresh（）方法

   2. refresh()方法中又调用了registerListeners();去注册所有的监听器

      ```java
      protected void registerListeners() {
      		// Register statically specified listeners first.
      
      		for (ApplicationListener<?> listener : getApplicationListeners()) {
      			getApplicationEventMulticaster().addApplicationListener(listener);
      		}
      
      		// Do not initialize FactoryBeans here: We need to leave all regular beans
      		// uninitialized to let post-processors apply to them!
      		String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
      		for (String listenerBeanName : listenerBeanNames) {
      			getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
      		}
      
      		// Publish early application events now that we finally have a multicaster...
      		Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
      		this.earlyApplicationEvents = null;
      		if (earlyEventsToProcess != null) {
      			for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
      				getApplicationEventMulticaster().multicastEvent(earlyEvent);
      			}
      		}
      	}
      ```

      String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false); 这一句从容器中拿到所有的监听器getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);将所有的事件监听器组测到事件派发器当中。