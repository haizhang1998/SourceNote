##Spring扩展----BeanDefinationRegistryPostProcessor

 BeanDefinitionRegistryPostProcessor是一个继承了父类接口 BeanFactoryPostProcessor的子接口，它的源码如下

```java
/**
 * Extension to the standard {@link BeanFactoryPostProcessor} SPI, allowing for
 * the registration of further bean definitions <i>before</i> regular
 * BeanFactoryPostProcessor detection kicks in. In particular,
 * BeanDefinitionRegistryPostProcessor may register further bean definitions
 * which in turn define BeanFactoryPostProcessor instances.
 *
 * @author Juergen Hoeller
 * @since 3.0.1
 * @see org.springframework.context.annotation.ConfigurationClassPostProcessor
 */
//大致意思是 BeanDefinitionRegistryPostProcessor再beanFactoryPostProcessor执行之前执行，可以定制更多的bean定义，比如为BeanFactoryPostProcessor初始化之前做更充足的准备。
public interface BeanDefinitionRegistryPostProcessor extends BeanFactoryPostProcessor {

	/**
	 * Modify the application context's internal bean definition registry after its
	 * standard initialization. All regular bean definitions will have been loaded,
	 * but no beans will have been instantiated yet. This allows for adding further
	 * bean definitions before the next post-processing phase kicks in.
	 * @param registry the bean definition registry used by the application context
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException;

}

```

 BeanDefinitionRegistry的含义是bean的注册中心，保存了所有bean的定义信息（bean的信息如单例?多例？）。以后的beanFactory就是依靠 BeanDefinitionRegistry保存的每一个bean定义信息创建bean实例的。

我们写下代码来演示BeanDefinitionRegistryPostProcessor的用法

```java
//首先定义一个普通的bean，等会要加载到registry中
public class Green {

    private String color;
    public Green(){
        System.out.println("Green constructor ............");
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
```

定义BeanDefinitionRegistryPostProcessor的实现类

```java

public class MyBeanDefinationRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor {
    /**
     * registry的调用方法
     * @param registry
     * @throws BeansException
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        System.out.println("postProcessBeanDefinitionRegistry....现在bean的注册中心中存在的bean定义数目："+registry.getBeanDefinitionCount());
        System.out.println(Arrays.asList(registry.getBeanDefinitionNames()));
        //这里注册一个自定义的bean
        Green green = new Green();
        RootBeanDefinition rootBeanDefinition = new RootBeanDefinition(green.getClass());
        registry.registerBeanDefinition("green",rootBeanDefinition);

    }

    /**
     * 父接口BeanPostProcessor的调用方法
     * @param beanFactory
     * @throws BeansException
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        System.out.println("postProcessBeanFactory....现在beanFactory中存在的bean定义数目："+beanFactory.getBeanDefinitionCount());
        System.out.println(Arrays.asList(beanFactory.getBeanDefinitionNames()));
    }
}

```

定义配置类和ImportSelector导入beanDefinationRegistryPostProcessor

```java
@Configuration
@Import({MyImportSelector.class})
public class EtxConfiguration2 {
}


//MyImportSelector返回需要注册进spring容器的bean
public class MyImportSelector implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[]{"springAnnotationAndSourceTest.etx.beanDefinationRegistryPostProcessor.MyBeanDefinationRegistryPostProcessor"};
    }
}
```

测试类直接将配置类加载进AnnotationApplicationContext中即可。

测试结果如下:

![1564711277108](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564711277108.png)

可以看到**BeanDefinitionRegistryPostProcessor接口中定义的方法先于父类BeanFactoryPostProcessor执行** 并且在BeanDefinitionRegistryPostProcessor中注册了Green类进去，bean的数目增加了一个。

**为什么beanDefinitionRegistry会先于BeanFacotryPostProcessor的方法执行呢？**

我们可以debug进入内部看看

**步骤:**

1. 创建IOC对象
2. 调用refresh（）方法
3. 调用invokeBeanFactoryPostProcessors(beanFactory)方法触发容器中定义的BeanFacotryProcessors
4. PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors()); 调用所有bean工厂中实现了BeanFactoryPostProcessors（包括其子接口BeanDefinationRegistryPostProcessors）的bean后置处理方法
5. 依次拿到beanFactory中BeanFactoryPostProcessors（包括其子接口BeanDefinationRegistryPostProcessors）的bean对象，并且调用方法postProcessBeanDefinitionRegistry(registry);
6. 执行BeanFactoryPostProcessors（包括其子接口BeanDefinationRegistryPostProcessors）的后置处理方法。

上面说的步骤主要在于下面invokeBeanFactoryPostProcessors方法中，和上一篇BeanFacotryPostProcessor调用的方法位置都一致。

```java
public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		Set<String> processedBeans = new HashSet<String>();
        //判断bean工厂是否为BeanDefinitionRegistry的对象
		if (beanFactory instanceof BeanDefinitionRegistry) {
            //将bean工厂转为BeanDefinitionRegistry，其实beanFacotry内部的东西都是BeanDefinitionRegistry中拿到的
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			List<BeanFactoryPostProcessor> regularPostProcessors = new LinkedList<BeanFactoryPostProcessor>();
			List<BeanDefinitionRegistryPostProcessor> registryPostProcessors =
					new LinkedList<BeanDefinitionRegistryPostProcessor>();
            //遍历所有的实现了 beanFactoryPostProcessors接口及子接口的类
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
                //判断这个对象是否是BeanDefinitionRegistryPostProcessor的实例！
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
                    //如果是BeanDefinitionRegistryPostProcessor实例就转化为它
					BeanDefinitionRegistryPostProcessor registryPostProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
                    //执行这个BeanDefinitionRegistryPostProcessor的接口的postProcessBeanDefinitionRegistry方法！
					registryPostProcessor.postProcessBeanDefinitionRegistry(registry);
                    //将这个BeanDefinitionRegistryPostProcessor实现类加入到队列中
					registryPostProcessors.add(registryPostProcessor);
				}
				else {
                    //如果不是BeanDefinitionRegistryPostProcessor的实例，就放入到常规的PostProcessors中
					regularPostProcessors.add(postProcessor);
				}
			}

		    
            //这一步开始拿到BeanDefinitionRegistryPostProcessor.class的所有名字
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);

			//依次调用按优先级排序调用BeanDefinitionRegistryPostProcessor的方法
			List<BeanDefinitionRegistryPostProcessor> priorityOrderedPostProcessors = new ArrayList<BeanDefinitionRegistryPostProcessor>();
			for (String ppName : postProcessorNames) {
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			sortPostProcessors(beanFactory, priorityOrderedPostProcessors);
			registryPostProcessors.addAll(priorityOrderedPostProcessors);
			invokeBeanDefinitionRegistryPostProcessors(priorityOrderedPostProcessors, registry);

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			List<BeanDefinitionRegistryPostProcessor> orderedPostProcessors = new ArrayList<BeanDefinitionRegistryPostProcessor>();
			for (String ppName : postProcessorNames) {
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					orderedPostProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);
				}
			}
			sortPostProcessors(beanFactory, orderedPostProcessors);
			registryPostProcessors.addAll(orderedPostProcessors);
			invokeBeanDefinitionRegistryPostProcessors(orderedPostProcessors, registry);

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					if (!processedBeans.contains(ppName)) {
						BeanDefinitionRegistryPostProcessor pp = beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class);
						registryPostProcessors.add(pp);
						processedBeans.add(ppName);
						pp.postProcessBeanDefinitionRegistry(registry);
						reiterate = true;
					}
				}
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
            //这里调用实现了BeanDefinitionRegistryPostProcessor接口类的postProcessBeanFactory方法
			invokeBeanFactoryPostProcessors(registryPostProcessors, beanFactory);
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		else {
			// Invoke factory processors registered with the context instance.
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<BeanFactoryPostProcessor>();
		List<String> orderedPostProcessorNames = new ArrayList<String>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<String>();
		for (String ppName : postProcessorNames) {
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		sortPostProcessors(beanFactory, priorityOrderedPostProcessors);
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<BeanFactoryPostProcessor>();
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		sortPostProcessors(beanFactory, orderedPostProcessors);
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<BeanFactoryPostProcessor>();
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();
	}

```

总结一点就是，这个方法会先调用BeanDefinationRegistryPostProcessor接口的处理方法，再调用其父类接BeanFacotryPostProcessor的接口方法。当BeanDefinationRegistryPostProcessor接口的所有实现类全部调用完毕后，再调用BeanFactoryPostProcessor的实现类中的处理方法。