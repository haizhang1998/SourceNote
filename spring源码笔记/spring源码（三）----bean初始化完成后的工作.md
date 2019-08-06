## spring源码（三）----bean初始化完成后的工作

上篇说了bean的整个初始化流程，大致分为几步，【实例化bean对象】，【为bean属性赋值】，【调用BeanPostProcessor来为bean做改造】等。 

当bean创建完成后，spring容器还会检查Bean是不是**SmartIni'tializingSingleton类型的** ，如果是就执行**afterSingletonInitialization方法** 

完成上面的判断整个初始化bean的流程已经全部走完了，bean此时就在容器之中。

**最后一步：finishRefresh**

```java
protected void finishRefresh() {
		// Initialize lifecycle processor for this context.
		initLifecycleProcessor();

		// Propagate refresh to lifecycle processor first.
		getLifecycleProcessor().onRefresh();

		// Publish the final event.
		publishEvent(new ContextRefreshedEvent(this));

		// Participate in LiveBeansView MBean, if active.
		LiveBeansView.registerApplicationContext(this);
	}
```

**步骤**：

1. initLifecycleProcessor();初始化和生命周期有关的后置处理器，如果容器中存在lifecycleProcessor就将其获取出来，保存在this.lifecycleProcessor中。我们可以自定义一个LifecyclePostProcessor实现类，并实现onRefresh和onClose方法去监听容器刷新完毕和关闭容器的时候就可以分别调用。

​      如果没有，就新建一个默认的：**DefaultLifecycleProcessor defaultProcessor = new   DefaultLifecycleProcessor();**并保存在this.lifecycleProcessor中，然后加入到容器中。

2. 执行生命周期相关的后置处理器的onRefresh()方法，执行容器刷新完成的回调方法。
3. ​	publishEvent(new ContextRefreshedEvent(this));发布容器刷新完成的事件。ApplicationListener可以监听这些事件
4. ​	LiveBeansView.registerApplicationContext(this);





===================================spring源码总结=======================================

1. 在spring容器启动的时候会保存所有注册进来的bean定义信息
   1. xml注册bean
   2. 注解注册bean:@Service、@Component .....

2. spring容器会合适的时机创建这些bean
   1. 用到这个bean的时候，利用getBean创建bean：创建好之后保存在容器中
   2. 同一创建剩下所有的bean的时候：finishBeanFactoryInitalization();

3. 后置处理器BeanPostProcessor：

   1. 每一个bean创建完成，都会使用各种后置处理器进行处理；来增强bean的功能

4. 事件驱动模型：

   ApplicationListener:事件监听

   事件的派发:ApplicationEventMulticaster

