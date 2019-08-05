# Spring源码----BeanFacotry准备

spring--->refresh() : 容器创建以及刷新 

重点的研究在于refresh（）方法，我们来看它的整体执行流程

```java
@Override
	public void refresh() throws BeansException, IllegalStateException {
		synchronized (this.startupShutdownMonitor) {
			// Prepare this context for refreshing.
            //刷新前的预处理工作
			prepareRefresh();

			// Tell the subclass to refresh the internal bean factory.
			ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

			// Prepare the bean factory for use in this context.
			prepareBeanFactory(beanFactory);

			try {
				// Allows post-processing of the bean factory in context subclasses.
				postProcessBeanFactory(beanFactory);

				// Invoke factory processors registered as beans in the context.
				invokeBeanFactoryPostProcessors(beanFactory);

				// Register bean processors that intercept bean creation.
				registerBeanPostProcessors(beanFactory);

				// Initialize message source for this context.
				initMessageSource();

				// Initialize event multicaster for this context.
				initApplicationEventMulticaster();

				// Initialize other special beans in specific context subclasses.
				onRefresh();

				// Check for listener beans and register them.
				registerListeners();

				// Instantiate all remaining (non-lazy-init) singletons.
				finishBeanFactoryInitialization(beanFactory);

				// Last step: publish corresponding event.
				finishRefresh();
			}

			catch (BeansException ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Exception encountered during context initialization - " +
							"cancelling refresh attempt: " + ex);
				}

				// Destroy already created singletons to avoid dangling resources.
				destroyBeans();

				// Reset 'active' flag.
				cancelRefresh(ex);

				// Propagate exception to caller.
				throw ex;
			}

			finally {
				// Reset common introspection caches in Spring's core, since we
				// might not ever need metadata for singleton beans anymore...
				resetCommonCaches();
			}
		}
	}

```

**第一模块:beanFacotry的准备初始化工作**

1. prepareRefresh();

  ```java
//构造方法
   @Override
   	protected void prepareRefresh() {
           //清缓存
   		this.scanner.clearCache();
           //调用准备刷新方法
   		super.prepareRefresh();
   	}
   ```
   
   继续看向prepareRefresh();

   ```java
	protected void prepareRefresh() {
           //记录初始化事件
   		this.startupDate = System.currentTimeMillis();
           //设置启动状态
   		this.closed.set(false);
   		this.active.set(true);
   		if (logger.isInfoEnabled()) {
   			logger.info("Refreshing " + this);
   		}
           //初始化一些属性设置，通过子类重写这个方法；子类自定义个性化的属性设置方法。
   		initPropertySources();
           //校验属性的合法性，比如非空校验之类的
   		getEnvironment().validateRequiredProperties();
           //保存容器中一些早期的事件
   		this.earlyApplicationEvents = new LinkedHashSet<ApplicationEvent>();
   	}
   ```
   
2. obtainFreshBeanFactory();创建bean工厂

   ```java
   protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
       //刷新bean工厂，内部有this.beanFactory.setSerializationId(getId());为bean工厂设置一个唯一标志id
   		refreshBeanFactory();
       //获取得到bean工厂
   		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
   		if (logger.isDebugEnabled()) {
   			logger.debug("Bean factory for " + getDisplayName() + ": " + beanFactory);
   		}
   		return beanFactory;
   }
   ```

   注意getBeanFactory()这里创建获取beanFactory是从**GenericApplicationContext这个类获取的，AnnotationConfigApplicationContext继承了这个类**

   ```java
   public class GenericApplicationContext extends AbstractApplicationContext implements BeanDefinitionRegistry {
       private final DefaultListableBeanFactory beanFactory;
       
   	/**
   	 * Create a new GenericApplicationContext.
   	 * @see #registerBeanDefinition
   	 * @see #refresh
   	 */
   	public GenericApplicationContext() {
   		this.beanFactory = new DefaultListableBeanFactory();
   	}
   
       /**
   	 * Create a new GenericApplicationContext with the given DefaultListableBeanFactory.
   	 * @param beanFactory the DefaultListableBeanFactory instance to use for this context
   	 * @see #registerBeanDefinition
   	 * @see #refresh
   	 */
   	public GenericApplicationContext(DefaultListableBeanFactory beanFactory) {
   		Assert.notNull(beanFactory, "BeanFactory must not be null");
   		this.beanFactory = beanFactory;
   	}
   
       /**
   	 * Create a new GenericApplicationContext with the given parent.
   	 * @param parent the parent application context
   	 * @see #registerBeanDefinition
   	 * @see #refresh
   	 */
   	public GenericApplicationContext(ApplicationContext parent) {
   		this();
   		setParent(parent);
   	}
   
       
   	/**
   	 * Create a new GenericApplicationContext with the given DefaultListableBeanFactory.
   	 * @param beanFactory the DefaultListableBeanFactory instance to use for this context
   	 * @param parent the parent application context
   	 * @see #registerBeanDefinition
   	 * @see #refresh
   	 */
   	public GenericApplicationContext(DefaultListableBeanFactory beanFactory, ApplicationContext parent) {
   		this(beanFactory);
   		setParent(parent);
   	}
   /**
   	 * Return the single internal BeanFactory held by this context
   	 * (as ConfigurableListableBeanFactory).
   	 */
   	@Override
   	public final ConfigurableListableBeanFactory getBeanFactory() {
   		return this.beanFactory;
   	}
   }
   ```

   上面可以看到**在创建容器的时候，我们已经调用了GenericApplicationContex的无参构造方法创建出来了beanFactory的实例对象【DefaultListableBeanFactory类型】** 

   而getBeanFactory就是返回这个创建出来的beanFactory对象

   

3. prepareBeanFactory(beanFactory);bean工厂的一些属性设置工作

   ```java
   
   	/**
   	 * Configure the factory's standard context characteristics,
   	 * such as the context's ClassLoader and post-processors.
   	 * @param beanFactory the BeanFactory to configure
   	 */
   //为beanFactory创建标准的上下文属性，比如类加载器等
   	protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
   		// Tell the internal bean factory to use the context's class loader etc.
           //设置bean的类加载器
   		beanFactory.setBeanClassLoader(getClassLoader());
           //设置bean的标准解析器
   		beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
           
   		beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));
   
   		// Configure the bean factory with context callbacks.
           //为beanFactory添加后置处理器ApplicationContextAwareProcessor
   		beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
   		beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
   		beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
   		beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
   		beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
   		beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
   		beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
   
   		// BeanFactory interface not registered as resolvable type in a plain factory.
   		// MessageSource registered (and found for autowiring) as a bean.
   		beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
   		beanFactory.registerResolvableDependency(ResourceLoader.class, this);
   		beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
   		beanFactory.registerResolvableDependency(ApplicationContext.class, this);
   
   		// Register early post-processor for detecting inner beans as ApplicationListeners.
          
   		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));
   
   		// Detect a LoadTimeWeaver and prepare for weaving, if found.
   		if (beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
   			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
   			// Set a temporary ClassLoader for type matching.
   			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
   		}
   
   		// Register default environment beans.
   		if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
   			beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
   		}
   		if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
   			beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
   		}
   		if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
   			beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
   		}
   	}
   ```

   

   1. 设置BeanFactory的类加载器，表达式解析器等

   2. 添加部分BeanPostProcessor(ApplicationContextAwareProcessor）

   3. 设置忽略的自动装配接口EnvironmentAware，这些目的是这些接口的实现类型不能通过接口实现自动注入

   4. 注册可以解析的自动装配，我们可以直接在任何组件中自动注入BeanFacotry，ResourceLoader，ApplicationEventPublisher，ApplicationContext

   5. 添加BeanPostProcessor 【applicationListenerDetector】

   6. 添加编译时的AspectJ支持

   7. 给beanFactory中注册一些能用的组件，比如environment【ConfigurableEnviorment】

      注册了一些SystemPropertires【Map<String,Object>】

      以后要用这些属性可以@Autowired导入这些属性

4. postProcessBeanFactory(beanFactory)

   beanFactory创建并准备好后调用beanFactory后置处理工作





**第二模块:invokeBeanFactoryPostProcessors(beanFactory)**

执行**BeanFactoryPostProcessor和BeanDefinationRegistryPostProcessor**的后置处理方法，其中BeanDefinationRegistryPostProcessor是BeanFactoryPostProcessor的子接口

1. 容器先获取所有实现了BeanDefinitionRegistryPostProcessor的类
2. 将这些BeanDefinitionRegistryPostProcessor实现类按照优先级进行排序并执行（实现了PriorityOrdered接口的优先级最先执行）
3. 首先是执行实现了BeanDefinitionRegistryPostProcessor接口和PriorityOrdered的类的后置处理方法
4. 其次是执行实现了BeanDefinitionRegistryPostProcessor接口和Ordered的类的后置处理方法
5. 最后是执行实现了BeanDefinitionRegistryPostProcessor接口但是没有实现PriorityOrdered和Ordered的类的后置处理方法

随后spring又去搜索BeanFactoryPostProcessor的实现bean。

步骤也是一样的

1. 首先是执行实现了BeanFactoryPostProcessor接口和PriorityOrdered的类的后置处理方法
2. 其次是执行实现了BeanFactoryPostProcessor接口和Ordered的类的后置处理方法
3. 最后是执行实现了BeanFactoryPostProcessor接口但是没有实现PriorityOrdered和Ordered的类的后置处理方法



**第三模块：registerBeanPostProcessors(beanFactory);**

这一步主要是为spring容器注册其他的beanPostProcessor

我们看看BeanPostProcessor的子接口，有很多种实现

```java
DestructionAwareBeanPostProcessor           //bean的销毁方法调用前后执行
InstantiationAwareBeanPostProcessor 		//bean的初始化方法调用前后执行
SmartInstantiationAwareBeanPostProcessor    //在bean实例化之前进行执行
MergedBeanDefinitionPostProcessor   
```

接下来就是这个方法的调用流程

1. spring会获取容器中所有的BeanPostProcessor

2. beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));为容器添加 BeanPostProcessorChecker这个BeanPostProcessor，用于校验beanPostProcessor的调用

3. spring在这个方法内创建了四个数组，记录不同执行优先级的BeanPostProcessor

   ```java
   List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<BeanPostProcessor>();
   		List<BeanPostProcessor> internalPostProcessors = new ArrayList<BeanPostProcessor>();
   		List<String> orderedPostProcessorNames = new ArrayList<String>();
   		List<String> nonOrderedPostProcessorNames = new ArrayList<String>();
   ```

4. spring遍历所有获取的beanPostProcessor的名字，去匹配优先级，然后将不同优先级的BeanPostProcessor排序到上面定义的不同数组上。并且判断这个beanPostProcessor是否是实现了MergedBeanDefinitionPostProcessor接口，如果是就加入到internalPostProcessors队列中。

5. 各种BeanPostProcessor分类分好了以后，spring调用

   ```java
   	sortPostProcessors(beanFactory, priorityOrderedPostProcessors);
      registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);
   
   //注册
   private static void registerBeanPostProcessors(
   			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {
   
   		for (BeanPostProcessor postProcessor : postProcessors) {
   			beanFactory.addBeanPostProcessor(postProcessor);
   		}
   	}
   ```

   这两个方法分别是将priorityOrderedPostProcessors（最先执行的BeanPostProcessor队列）进行排序，然后在beanFactory中组测这个priorityOrderedPostProcessors队列的所有bean

6. 实现Orderd和既不实现Orderd接口也不实现priorityOrdered的接口的BeanPostProcessor的注册流程和第5步骤一致

7. ```java
   sortPostProcessors(beanFactory, internalPostProcessors);registerBeanPostProcessors(beanFactory, internalPostProcessors);
   ```

   最后排序并注册实现了MergedBeanDefinitionPostProcessor接口的BeanPostProcessor

8. ```java
   beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
   ```

   最后spring像容器中注册了一个BeanPostProcessor【ApplicationListenerDetector】，来检查bean创建完成之后是不是ApplicationListener

   ```java
   //ApplicationListenerDetector的后置处理方法
   @Override
   	public Object postProcessAfterInitialization(Object bean, String beanName) {
   		if (this.applicationContext != null && bean instanceof ApplicationListener) {
   			// potentially not detected as a listener by getBeanNamesForType retrieval
   			Boolean flag = this.singletonNames.get(beanName);
   			if (Boolean.TRUE.equals(flag)) {
   				// singleton bean (top-level or inner): register on the fly
   				this.applicationContext.addApplicationListener((ApplicationListener<?>) bean);
   			}
   			else if (Boolean.FALSE.equals(flag)) {
   				if (logger.isWarnEnabled() && !this.applicationContext.containsBean(beanName)) {
   					// inner bean with other scope - can't reliably process events
   					logger.warn("Inner bean '" + beanName + "' implements ApplicationListener interface " +
   							"but is not reachable for event multicasting by its containing ApplicationContext " +
   							"because it does not have singleton scope. Only top-level listener beans are allowed " +
   							"to be of non-singleton scope.");
   				}
   				this.singletonNames.remove(beanName);
   			}
   		}
   		return bean;
   	}
   ```

   如果使是的话就执行this.applicationContext.addApplicationListener((ApplicationListener<?>) bean);将这个bean当作ApplicationListener来注入到spring容器中，可以对事件进行监听。

   **上面这些步骤只是注册BeanPostProcessor到容器中喔，并没有执行！**



**第四模块:initMessageSource()**

初始化MessageSource组件（做国际化，消息绑定，消息解析）

```java
protected void initMessageSource() {
        //拿到一个bean工厂
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
        //bean工厂是否存在messagesource的bean
		if (beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME)) {
            //如果有就获取
			this.messageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
			// Make MessageSource aware of parent MessageSource.
         
			if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource) {
				HierarchicalMessageSource hms = (HierarchicalMessageSource) this.messageSource;
				if (hms.getParentMessageSource() == null) {
					// Only set parent context as parent MessageSource if no parent MessageSource
					// registered already.
					hms.setParentMessageSource(getInternalParentMessageSource());
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Using MessageSource [" + this.messageSource + "]");
			}
		}
		else {
			// Use empty MessageSource to be able to accept getMessage calls.
			DelegatingMessageSource dms = new DelegatingMessageSource();
			dms.setParentMessageSource(getInternalParentMessageSource());
			this.messageSource = dms;
			beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate MessageSource with name '" + MESSAGE_SOURCE_BEAN_NAME +
						"': using default [" + this.messageSource + "]");
			}
		}
	}
```



1. 获取BeanFactory
2. 看容器中是否存在id为messageSource的，类型是MessageSource的组件，如果有赋值给messageSource，如果没有就spring自己创建一个DelegatingMessageSource；MessageSource:取出国际化配置文件中的某个key的值，能按照区域信息获取
3. 把创建好的MessageSource注册在容器中	beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);，以后获取国际化配置文件的值的时候，可以自动注入MessageSource;



**第五模块：初始化事件派发器，监听器等initApplicationEventMulticaster()** 

```java
	protected void initApplicationEventMulticaster() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
			this.applicationEventMulticaster =
					beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
			if (logger.isDebugEnabled()) {
				logger.debug("Using ApplicationEventMulticaster [" + this.applicationEventMulticaster + "]");
			}
		}
		else {
			this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
			beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, this.applicationEventMulticaster);
			if (logger.isDebugEnabled()) {
				logger.debug("Unable to locate ApplicationEventMulticaster with name '" +
						APPLICATION_EVENT_MULTICASTER_BEAN_NAME +
						"': using default [" + this.applicationEventMulticaster + "]");
			}
		}
	}

```

1. 获取bean工厂
2. 从bean工厂获取APPLICATION_EVENT_MULTICASTER_BEAN_NAME的applicationEventMultiCaster
3. 如果没有存在bean工厂中，则创建一个this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
4. 将ApplicationEventMulticaster注册到beanFactory中，以后其他组件用的时候就可以自动注入



**第六模块：onRefresh();**

这个实现是留给子类去实现，默认是空方法在spring中。你可以在容器刷新的时候再自定义做其他的事情



**第七模块:registerListeners();**

```java
protected void registerListeners() {
		// Register statically specified listeners first.
	//从spring容器中看看是不是有固定指定的监听器，如果有就加入	
    for (ApplicationListener<?> listener : getApplicationListeners()) {
			getApplicationEventMulticaster().addApplicationListener(listener);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let post-processors apply to them!
    //1，从bean工厂拿去所有类型为ApplicationListener的事件监听器
		String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
    //2.将其加入到派发起中
		for (String listenerBeanName : listenerBeanNames) {
			getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
		}

		// Publish early application events now that we finally have a multicaster...
    //3.再所有监听事件装配完成之后触发的处理流程
		Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
		this.earlyApplicationEvents = null;
		if (earlyEventsToProcess != null) {
			for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
				getApplicationEventMulticaster().multicastEvent(earlyEvent);
			}
		}
	}
```

1. 从容器拿到所有ApplicationListener
2. 将每个监听器添加到事件派发器当中
3. 派发之前步骤产生的事件



