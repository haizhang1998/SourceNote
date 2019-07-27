# Spring注解驱动开发四---AOP续集

上一篇中我们说到了Spring是如何返回生成AOP代理对象的，这一篇的重点在于这个代理对象是如何让我们执行某一个方法时自动加上定义的通知方法。

本篇的目标：**我们想看下当我执行MyCalculate的add方法时，代理对象时什么样的顺序执行通知方法再到目标方法的**

让我们把以前的断点移除，重新在测试方法的调用MyCalculate的add方法处打上断点。

![1564055576331](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564055576331.png)

运行debug，我们可以看到spring返回的确实时cglib的代理对象。而且里面包含了我们想要看到的通知以及通知想要作用的目标对象（target）

我们继续debug进去。发现来到了CglibAopProxy的类中的intercept方法

```java
@Override
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			Object oldProxy = null;
			boolean setProxyContext = false;
			Class<?> targetClass = null;
			Object target = null;
			try {
				if (this.advised.exposeProxy) {
					// Make invocation available if necessary.
					oldProxy = AopContext.setCurrentProxy(proxy);
					setProxyContext = true;
				}
				// May be null. Get as late as possible to minimize the time we
				// "own" the target, in case it comes from a pool...
				target = getTarget();
				if (target != null) {
					targetClass = target.getClass();
				}
				List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
				Object retVal;
				// Check whether we only have one InvokerInterceptor: that is,
				// no real advice, but just reflective invocation of the target.
				if (chain.isEmpty() && Modifier.isPublic(method.getModifiers())) {
					// We can skip creating a MethodInvocation: just invoke the target directly.
					// Note that the final invoker must be an InvokerInterceptor, so we know
					// it does nothing but a reflective operation on the target, and no hot
					// swapping or fancy proxying.
					Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
					retVal = methodProxy.invoke(target, argsToUse);
				}
				else {
					// We need to create a method invocation...
					retVal = new CglibMethodInvocation(proxy, target, method, args, targetClass, chain, methodProxy).proceed();
				}
				retVal = processReturnType(proxy, target, method, retVal);
				return retVal;
			}
			finally {
				if (target != null) {
					releaseTarget(target);
				}
				if (setProxyContext) {
					// Restore old proxy.
					AopContext.setCurrentProxy(oldProxy);
				}
			}
		}

```

在这个方法中，List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass)，这里获取目标方法的拦截器链（就是通知方法又被包装为拦截器，利用MethodInterceptor机制）。

我们针对这个方法来看看拦截器链时怎么生成的

```java
/**
	 * Determine a list of {@link org.aopalliance.intercept.MethodInterceptor} objects
	 主要就是为给定的目标对象的执行方法，返回一个MethodInterceptor类型的链
	 * for the given method, based on this configuration.
	 * @param method the proxied method     代理类要执行的方法
	 * @param targetClass the target class  目标类的class
	 * @return List of MethodInterceptors (may also include InterceptorAndDynamicMethodMatchers)
	 */
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Method method, Class<?> targetClass) {
        //这两部表示缓存方法，记录方便以后查询
		MethodCacheKey cacheKey = new MethodCacheKey(method);
        //如果之前有缓存过这个方法，直接返回拦截器链
		List<Object> cached = this.methodCache.get(cacheKey);
		if (cached == null) {
            //没有缓存过则直接取拦截器链工厂创建这个方法的拦截器链
			cached = this.advisorChainFactory.getInterceptorsAndDynamicInterceptionAdvice(
					this, method, targetClass);
			this.methodCache.put(cacheKey, cached);
		}
		return cached;
	}
```

看到这一个语句**this.advisorChainFactory.getInterceptorsAndDynamicInterceptionAdvice** 

这个方法调用传进去目标类和它的调用方法，开始生成拦截器链

```java
    
    //config里面装了通知方法
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(
			Advised config, Method method, Class<?> targetClass) {

		// This is somewhat tricky... We have to process introductions first,
		// but we need to preserve order in the ultimate list.
        //创建丽娜姐器链，长度为增强器的数目
		List<Object> interceptorList = new ArrayList<Object>(config.getAdvisors().length);
        //得到这个代理类的真正的类
		Class<?> actualClass = (targetClass != null ? targetClass : method.getDeclaringClass());
		boolean hasIntroductions = hasMatchingIntroductions(config, actualClass);
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();

        //遍历所有的增强器
		for (Advisor advisor : config.getAdvisors()) {
            //判断增强器是否为PointcutAdvisor的类型
			if (advisor instanceof PointcutAdvisor) {
				// Add it conditionally.
				PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;
				if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
					MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
					if (MethodMatchers.matches(mm, method, actualClass, hasIntroductions)) {
						if (mm.isRuntime()) {
							// Creating a new object instance in the getInterceptors() method
							// isn't a problem as we normally cache created chains.
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						}
						else {
							interceptorList.addAll(Arrays.asList(interceptors));
						}
					}
				}
			}
			else if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}
			else {
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}

		return interceptorList;
	}
```

看到registry.getInterceptors(advisor)，遍历所有的增强器，并且每一个增强器都会进行判断是否为

IntroductionAdvisor以及PointcutAdvisor或者两者都不是。不管怎么样都会调用registry.getInterceptors(advisor)，将增强器传递进去，并且得到包装后的拦截器

我们点进getInterceptors这个方法中，看里面是如何对我们传递的advisor包装成拦截器的

```java
@Override
	public MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException {
        
		List<MethodInterceptor> interceptors = new ArrayList<MethodInterceptor>(3);
		Advice advice = advisor.getAdvice();
        //判断当前的拦截器是否为MethodInterceptor的实例
		if (advice instanceof MethodInterceptor) {
            //这一步就是将advice直接强转为MethodInterceptor类型并且加入到List<MethodInterceptor>队列中
			interceptors.add((MethodInterceptor) advice);
		}
        //这里遍历所有的通知适配器，查看适配器是否支持转换当前这个advisor
		for (AdvisorAdapter adapter : this.adapters) {
			if (adapter.supportsAdvice(advice)) {
                //如果支持就用适配器强转成MethodInterceptor类型的拦截器，并加入队列
				interceptors.add(adapter.getInterceptor(advisor));
			}
		}
        //如果到这一步不能将advisor成功转为拦截器，则抛异常
		if (interceptors.isEmpty()) {
			throw new UnknownAdviceTypeException(advisor.getAdvice());
		}
        //返回拦截器数组
		return interceptors.toArray(new MethodInterceptor[interceptors.size()]);
	}
```

我们看下其中的adapter.supportsAdvice(advice)方法，它又三种适配器可用，如下图

![1564067789901](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564067789901.png)

说明拦截器可以将@AfterReturning 和 @before 和@AfterThrowing转化为MethodInterceptor类型的拦截器。到此**增强器**转化到**拦截器**的过程就结束了。

在返回了拦截器链后就会进行如下两者判断：

* 如果没有拦截器链，就会直接执行目标方法。

* 如果有拦截器链，就把需要执行的目标对象，目标方法，拦截器链等信息传入创建一个CglibMethodInvocation对象，并调用它的proceed（）方法，执行。



**上面总结下：**

从spring容器获取得到AOP代理对象，当这个代理对象要调用方法时，就会被cglib的intercept方法捕获，然后去为指定的目标对象要执行的方法获取增强器，并且内部将这些增强器转化为拦截器（MethodInterceptor类型），将这些拦截器链返回并抛进CglibMethodInvocation类并传入目标方法，目标对象等元素，创建CglibMethodInvocation的对象，执行**（拦截器链的出发过程）**



**现在将重点放在链式调用通知方法的步骤上**

我们点进上面的CglibMethodInvocation.proceed()方法中

```java
    //proceed方法处理链时调用
	public Object proceed() throws Throwable {
		//	We start with an index of -1 and increment early.
        //这个时指定当前的拦截器数组坐标，初始值为-1，检测是否等于最后一个拦截器
		if (this.currentInterceptorIndex == this.interceptorsAndDynamicMethodMatchers.size() - 1) {
			return invokeJoinpoint();
		}
        //得到第this.currentInterceptorIndex+1个拦截器
		Object interceptorOrInterceptionAdvice =
		this.interceptorsAndDynamicMethodMatchers.get(++this.currentInterceptorIndex);
		//如果拦截器类型为InterceptorAndDynamicMethodMatcher就执行调用拦截器
        if (interceptorOrInterceptionAdvice instanceof InterceptorAndDynamicMethodMatcher) {
			// Evaluate dynamic method matcher here: static part will already have
			// been evaluated and found to match.
			InterceptorAndDynamicMethodMatcher dm =
					(InterceptorAndDynamicMethodMatcher) interceptorOrInterceptionAdvice;
			//如果当前的拦截器匹配到该方法
            if (dm.methodMatcher.matches(this.method, this.targetClass, this.arguments)) {               //调用拦截器（这里开始链式调用）
				return dm.interceptor.invoke(this);
			}
			else {
				// Dynamic matching failed.
				// Skip this interceptor and invoke the next in the chain.
				return proceed();
			}
		}
		else {
			// It's an interceptor, so we just invoke it: The pointcut will have
			// been evaluated statically before this object was constructed.
			return ((MethodInterceptor) interceptorOrInterceptionAdvice).invoke(this);
		}
	}

```

其中先知道拦截器数组又哪些拦截器

![1564103525913](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564103525913.png)

可以看到最初调用的数组下标为0的拦截器时ExposeInvocationInterceptor，逐步debug，在 proceed（）方法内部又调用了它的invoke（this）方法。

```java
//this.currentInterceptorIndex = 0
//ExposeInvocationInterceptor.invoke处理逻辑
@Override
	public Object invoke(MethodInvocation mi) throws Throwable {
		MethodInvocation oldInvocation = invocation.get();
		invocation.set(mi);
		try {
			return mi.proceed();
		}
		finally {
			invocation.set(oldInvocation);
		}
	}
```

而invoke方法内部我们看到又返回来链式调用了proceed方法！此时我们回到proceed方法中发现this.currentInterceptorIndex进行加一，且指向下一个拦截器。

![1564103851299](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564103851299.png)

我们继续看它有什么逻辑

```java
//this.currentInterceptorIndex = 1 
//AspectJAfterThrowingAdvice
@Override
	public Object invoke(MethodInvocation mi) throws Throwable {
		try {
			return mi.proceed();
		}
		catch (Throwable ex) {
			if (shouldInvokeOnThrowing(ex)) {
				invokeAdviceMethod(getJoinPointMatch(), null, ex);
			}
			throw ex;
		}
	}
```

继续看到这里只不过时在mi.proceed外部进行的捕获异常！然后继续调用用proceed方法，同样的逻辑

```java
//this.currentInterceptorIndex = 2
//AfterReturningAdviceInterceptor
@Override
	public Object invoke(MethodInvocation mi) throws Throwable {
		Object retVal = mi.proceed();
		this.advice.afterReturning(retVal, mi.getMethod(), mi.getArguments(), mi.getThis());
		return retVal;
	}
```

继续的AfterReturningAdviceInterceptor的invoke中又一次链式调用了mi.proceed

```java
//this.currentInterceptorIndex = 3
//AspectJAfterAdvice
@Override
	public Object invoke(MethodInvocation mi) throws Throwable {
		try {
			return mi.proceed();
		}
		finally {
			invokeAdviceMethod(getJoinPointMatch(), null, null);
		}
	}

//继续同样的方法，前置通知

//this.currentInterceptorIndex = 4
//MethodBeforeAdviceInterceptor
@Override
	public Object invoke(MethodInvocation mi) throws Throwable {
		this.advice.before(mi.getMethod(), mi.getArguments(), mi.getThis() );
		return mi.proceed();
	}

```

终于到了 MethodBeforeAdviceInterceptor这个拦截器中，它收首先进行了this.advice.before(mi.getMethod(), mi.getArguments(), mi.getThis() ) ，为代理方法添加前置通知

```java
//这个是this.advice.before方法的内部调用，紧接着执行代理方法
	@Override
	public void before(Method method, Object[] args, Object target) throws Throwable {
		invokeAdviceMethod(getJoinPointMatch(), null, null);
	}

//invokeAdviceMethod
//jpMatch：切入点      returnValue：方法的返回值    ex:方法执行过程中的返回异常
	protected Object invokeAdviceMethod(JoinPointMatch jpMatch, Object returnValue, Throwable ex) throws Throwable {
		return invokeAdviceMethodWithGivenArgs(argBinding(getJoinPoint(), jpMatch, returnValue, ex));
	}

```

下面就到了重点invokeAdviceMethodWithGivenArgs,出发通知方法依靠给定的方法参数（add的传入参数）

```java
protected Object invokeAdviceMethodWithGivenArgs(Object[] args) throws Throwable {
		Object[] actualArgs = args;
		if (this.aspectJAdviceMethod.getParameterTypes().length == 0) {
			actualArgs = null;
		}
		try {
			ReflectionUtils.makeAccessible(this.aspectJAdviceMethod);
			// TODO AopUtils.invokeJoinpointUsingReflection
            //这一步真正的去触发通知
			return this.aspectJAdviceMethod.invoke(this.aspectInstanceFactory.getAspectInstance(), actualArgs);
		}
		catch (IllegalArgumentException ex) {
			throw new AopInvocationException("Mismatch on arguments to advice method [" +
					this.aspectJAdviceMethod + "]; pointcut expression [" +
					this.pointcut.getPointcutExpression() + "]", ex);
		}
		catch (InvocationTargetException ex) {
			throw ex.getTargetException();
		}
	}
```

this.aspectJAdviceMethod.invoke(this.aspectInstanceFactory.getAspectInstance(), actualArgs) 注意这一步，**其实质就是利用了java中的反射技术，给定方法名，给定类的对象，给定实际的传入参数就可以触发(invoke)这个方法**,你可以看到this.aspectJAdviceMethod方法名究竟是谁

![1564128045527](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564128045527.png)

**就是我们写的切面类中beforeAdvice方法** 直接调用就可以在控制台打印结果！

![1564128118671](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564128118671.png)

这样我们就已经把前置通知再目标方法前调用完毕了。因为这个是最后一个拦截器链中的拦截器，所以它也会执行mi.proceed方法，这个方法去看看当前的currentIndex是否到了最后一个，如果是就执行**invokeJoinpoint()**方法,这个方法就开始调用我们真正想要调用的目标方法了！！！

```java
/**
		 * Gives a marginal performance improvement versus using reflection to
		 * invoke the target when invoking public methods.
		 */
         //与在调用公共方法时使用反射来调用目标相比，提供了微小的性能改进。
		@Override
		protected Object invokeJoinpoint() throws Throwable {
			if (this.publicMethod) {
				return this.methodProxy.invoke(this.target, this.arguments);
			}
			else {
				return super.invokeJoinpoint();
			}
		}
	}

//
```

看向methodProxy里面又什么信息

![1564128682342](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564128682342.png)

**很清楚记录到来当前的真实的类，以及调用的方法等信息。根据这些信息以及传进去的对象和参数，就可以通过反射调用我们的目标方法了！**（其内部的调用流程根据methodProxy的createInfo中的信息确定调用方法的位置，再找到这个位置利用invoke方法调用）

目前位置，这两个@before以及目标方法调用完成，又回到了@After方法的调用，它最终回到了proceed方法中调用语句 ((MethodInterceptor) interceptorOrInterceptionAdvice).invoke(this); 来出发它自身的通知方法

随后便是调用@AfterReturning的通知方法其内部调用了this.advice.afterReturning(retVal, mi.getMethod(), mi.getArguments(), mi.getThis());来得到目标方法的返回值通知结果，将结果存放在**retVal中存放**并返回。

**还有一个如果目标方法执行发生了异常怎么处理呢？**

其实spring内部处理很简单，直接把异常从目标方法执行往上去抛，如果没能力处理的话就继续跑出去，我们的调用链是层层嵌套的，如果哪一块链中没办法处理这个异常，就会抛像上一个链（注意，这时候这个链的方法通知不会得到执行！AfterReturningAdviceInterceptor会被跨过处理，因其内部无法处理目标方法的异常）这是后异常就会到达AspectJAfterThrowingAdvice中

```java
@Override
	public Object invoke(MethodInvocation mi) throws Throwable {
		try {
			return mi.proceed();
		}
		catch (Throwable ex) {
            //这一步判断这个类型的异常是否因该得到通知处理呢
			if (shouldInvokeOnThrowing(ex)) {
                //如果校验通过就执行@AfterThrowing的通知处理流程
				invokeAdviceMethod(getJoinPointMatch(), null, ex);
			}
			throw ex;
		}
	}
```

捕获代码异常就在这个方法处理掉了。

为了更加方便的了解整个调用流程，附送下面的图：（黑色箭头是链式调用，而红色箭头是方法返回）

![1564102982137](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564102982137.png)





最后的总结：

1. @EnableAspectJAutoProxy  开启AOP注解功能

2. @EnableAspectJAutoProxy  会给容器中注册一个组件AnnotationAwareAspectJAutoProxyCreator（是一个后置处理器）

3. 容器的创建流程：

   1. registerBeanPostProcessors() 注册后置处理器 :创建AnnotationAwareAspectJAutoProxyCreator对象保存再spring容器中

   2. finishBeanFactoryInitialization() 初始化剩下的单实例bean

      1. 创建业务逻辑组件和切面组件

      2. AnnotationAwareAspectJAutoProxyCreator会拦截这些单实例bean的创建过程

      3. 组件创建完成后，判断是否需要增强这些组件

         ​    如果是: 切面的通知方法，包装成增强器（Advisor）;给业务逻辑组件创建一个代理对象

4. 执行目标方法

   1. 代理对象执行目标方法

   2. CglibAopProxy.intercept（）;

      1. 得到目标方法的拦截器链（增强器包装成拦截器MethodInterceptor）

      2. 利用拦截器的链式机制，一次进入每一个拦截器进行执行

      3. 效果：

         正常流程：前置通知（在拦截器链的最后，最先执行）---> 目标方法 ---> 后置通知 --- > 返回通知

         出现异常 :  前置通知（在拦截器链的最后，最先执行）---> 目标方法 ---> 后置通知 --- > 异常通知

   总结完毕！

2019/7/26