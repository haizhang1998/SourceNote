# Spring扩展原理----BeanFactoryPostProcessor

**前言：**BeanPostProcessor在bean创建的前后调用前置方法和后置方法，而BeanFactoryPostProcessor是**BeanFactory标准初始化之后进行调用，此时所有的bean定义已经加载进BeanFactory中，但是没有创建bean的对象**

为了说明beanFactoryPostProcessor的工作时机，我们需要写下代码用于展示

1. 创建一个类实现BeanFactoryPostProcessor

```java
/**
 * 自定义一个BeanFactoryPostProcessor，将他声明为spring组件
 */
@Component
public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    public MyBeanFactoryPostProcessor(){
        System.out.println("now Create MyBeanFactoryPostProcessor constructor");
    }
    /**
     * 在beanFactory创建完后执行，注意没有前置方法喔
     * @param beanFactory
     * @throws BeansException
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
          //我们从这里拿到当前beanFactory所加载的bean定义
        System.out.println(" MyBeanFactoryPostProcessor invoke postProcessBeanFactory ....");
        String[] names = beanFactory.getBeanDefinitionNames();
        System.out.println("现在beanFactory拥有"+names.length+"个bean定义");
        System.out.println(Arrays.asList(names));
    }
}

```

2. 创建一个bean，用于演示它和BeanFactoryPostProcessor的调用时机

   ```java
   /**
    * 创建一个普通的bean，演示它的创建时机
    */
   public class Blue {
   
       public Blue(){
           System.out.println("blue constructor 。。。。。。");
       }
   }
   ```

3. 创建配置类，装配扫描上面两个bean

   ```java
   /**
    * 配置类，
    */
   @Configuration
   @ComponentScan
   public class EtxConfiguration {
       @Bean
       public Blue blue(){
           return new Blue();
       }
   }
   
   ```

   所有步骤都完成了，我们新建一个测试类进行测试

   ```JAVA
       @Test    
   	public void testBeanFactoryPostProcessor(){
           //只需要创建容器将配置类加载进去即可，ioc容器会自动调用BeanFactoryPostProcessor的方法
           AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext(EtxConfiguration.class);
    }
   ```

运行结果：

![1564653427889](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564653427889.png)

看上面的结果，发现Blue的创建实在MyBeanFactoryPostProcessor之后的！在beanFactory发现了9个bean定义，他们都还没真正的实例化的喔！

我们来追踪源码，看看spring到底在BeanFacoryPostProcessor做了哪些操作。打断点在MyBeanFactoryPostProcessor的后置处理方法中,debug

![1564653736035](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564653736035.png)

跟着debug调用栈走，首先它会调用refresh()方法，其中又调用了invokeBeanFactoryPostProcessors(beanFactory);这个方法主要是将beanFactorypostProcessor进行初始化并调用其中的方法。

![1564670116487](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564670116487.png)

```java
//调用所有注册了BeanFactoryPostProcessors的beans，必须要在单实例bean初始化前进行调用。
protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
     //去beanFactory中拿到所有beanFactoryPostProcessors并执行他们的方法
		PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

		// Detect a LoadTimeWeaver and prepare for weaving, if found in the meantime
		// (e.g. through an @Bean method registered by ConfigurationClassPostProcessor)
		if (beanFactory.getTempClassLoader() == null && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}
	}
```

上面代码调用了invokeBeanFactoryPostProcessors去执行beanFactory已经加载进去的所有实现进来BeanFactoryPostProcessor的bean。

```java
	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// 前面省略调用 BeanDefinitionRegistryPostProcessors，不在研究重点，省略代码
	       
		//不要再这里初始化单实例bean，我们可能要将单实例bean用beanFactoryPostProcessor进行修改。
        //这一步主要从beanFactory中获取类型为BeanFactoryPostProcessor.class的bean的名称。
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
        //将BeanFactoryPostProcessor进行按比较级划分。实现了 PriorityOrdered接口的beanFactoryPostProcessor优先得到执行
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<BeanFactoryPostProcessor>();
        
		List<String> orderedPostProcessorNames = new ArrayList<String>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<String>();
        //遍历所有的 postProcessorNames，查看他们的继承实现关系判断是否实现了Ordered之类的接口
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
        //由于我们的bean没有实现Ordered和PriorityOrdered接口，所以最后被执行
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<BeanFactoryPostProcessor>();
		for (String postProcessorName : nonOrderedPostProcessorNames) {
            //从容器中，拿到并创建真正的BeanFactoryPostProcessor
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
        //调用BeanFactoryPostProcessor后置处理方法
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();
	}

```

代码注释讲的很详细了

```java
private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}
```

上面遍历所有BeanFactoryPostProcessor (nonOrderd的) ，然后执行他们的postProcessBeanFactory方法

这里就回到了我们自定义的方法执行啦，这就是BeanFactoryPostProcessor的整个执行流程。无非就是再beanFactory创建之后，再单实例bean创建初始化之前进行调用，可以像beanFactory拿到容器中单实例bean的定义，然后增加一些修改操作等。