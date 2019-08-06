## Spring源码（二）----bean的创建流程

之前我们介绍到了事件的多播器和事件监听器创建加载入spring容器的流程。这篇文章是继上一篇的步骤下来的，现在的步骤是**bean的创建流程** 

**finishBeanFactoryInitialization(beanFactory);**

这个方法就是创建bean的最外层逻辑

```java
protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
		// Initialize conversion service for this context.
		if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
				beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
			beanFactory.setConversionService(
					beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
		}

		// Register a default embedded value resolver if no bean post-processor
		// (such as a PropertyPlaceholderConfigurer bean) registered any before:
		// at this point, primarily for resolution in annotation attribute values.
        //创建一个EmbeddedValueResolver主要用于解析字符串
		if (!beanFactory.hasEmbeddedValueResolver()) {
			beanFactory.addEmbeddedValueResolver(new StringValueResolver() {
				@Override
				public String resolveStringValue(String strVal) {
					return getEnvironment().resolvePlaceholders(strVal);
				}
			});
		}

		// Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early.
		String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
		for (String weaverAwareName : weaverAwareNames) {
			getBean(weaverAwareName);
		}

		// Stop using the temporary ClassLoader for type matching.
		beanFactory.setTempClassLoader(null);

		// Allow for caching all bean definition metadata, not expecting further changes.
		beanFactory.freezeConfiguration();

		// Instantiate all remaining (non-lazy-init) singletons.
        //这一步真正的创建bean
		beanFactory.preInstantiateSingletons();
	}

```

上面代码的最后一行：**beanFactory.preInstantiateSingletons();** 才是真正的创建我们的目标bean

我们看看sring容器究竟如何创建所有的bean在这个方法内部

```java
public void preInstantiateSingletons() throws BeansException {
		if (this.logger.isDebugEnabled()) {
			this.logger.debug("Pre-instantiating singletons in " + this);
		}

		//获取到所有的bean定义
		List<String> beanNames = new ArrayList<String>(this.beanDefinitionNames);

		// Trigger initialization of all non-lazy singleton beans...
		for (String beanName : beanNames) {
            //遍历所有的bean名字，去拿到bean定义
			RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);
            //检查bean定义是否不是抽象的，是否是单例的，是否非懒加载
			if (!bd.isAbstract() && bd.isSingleton() && !bd.isLazyInit()) {
                //检查这个bean是不是工厂bean（实现了FactoryBean接口的bean）
				if (isFactoryBean(beanName)) {
                    //工厂前缀&+beanName就可以拿出真正的工厂对象
					final FactoryBean<?> factory = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
					boolean isEagerInit;
 					//检查工厂FactoryBean是不是SmartFactoryBean的实例
                    if (System.getSecurityManager() != null && factory instanceof SmartFactoryBean) {
                        //执行其中方法
						isEagerInit = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
							@Override
							public Boolean run() {
								return ((SmartFactoryBean<?>) factory).isEagerInit();
							}
						}, getAccessControlContext());
					}
					else {
						isEagerInit = (factory instanceof SmartFactoryBean &&
								((SmartFactoryBean<?>) factory).isEagerInit());
					}
					if (isEagerInit) {
						getBean(beanName);
					}
				}
              
				else {
                    //如果不是FactoryBean就直接创建Bean
					getBean(beanName);
				}
			}
		}

		// Trigger post-initialization callback for all applicable beans...
		for (String beanName : beanNames) {
			Object singletonInstance = getSingleton(beanName);
			if (singletonInstance instanceof SmartInitializingSingleton) {
				final SmartInitializingSingleton smartSingleton = (SmartInitializingSingleton) singletonInstance;
				if (System.getSecurityManager() != null) {
					AccessController.doPrivileged(new PrivilegedAction<Object>() {
						@Override
						public Object run() {
							smartSingleton.afterSingletonsInstantiated();
							return null;
						}
					}, getAccessControlContext());
				}
				else {
					smartSingleton.afterSingletonsInstantiated();
				}
			}
		}
	}

```

上面代码中我们注意到需要以下的几个步骤创建目标bean

1. List<String> beanNames = new ArrayList<String>(this.beanDefinitionNames); 拿到bean的所有名字

2. 遍历所有bean的名字，分别拿到他们的bean定义信息RootBeanDefinition bd = getMergedLocalBeanDefinition(beanName);

3. 检查bean定义是否不是抽象的，是否是单例的，是否非懒加载

4. 如果满足条件3

   1. 检查这个bean是不是工厂bean（实现了FactoryBean接口的bean）
   2. 如果实现条件1
      1. 工厂前缀&+beanName就可以拿出真正的工厂对象
      2. 利用工厂Bean的getObject方法直接创建bean对象

   3. 如果不能实现条件1

      1. 直接叫spring创建bean，调用getBean(beanName)方法

      ```java
      public Object getBean(String name) throws BeansException {
      		return doGetBean(name, null, null, false);
      	}
      ```

      2. 内部继续调用doGetBean方法，将bean名字传递进去准备创建

      ```java
      protected <T> T doGetBean(
      			final String name, final Class<T> requiredType, final Object[] args, boolean typeCheckOnly)
      			throws BeansException {
             //拿到bean真正的名字
      		final String beanName = transformedBeanName(name);
      		Object bean;
      
      		// Eagerly check singleton cache for manually registered singletons.
              //实际上这一步调用	Object singletonObject = this.singletonObjects.get(beanName);去看看这个bean是否之前被创建过在缓存中
      		Object sharedInstance = getSingleton(beanName);
              //如果被创建在缓存中
      		if (sharedInstance != null && args == null) {
      			if (logger.isDebugEnabled()) {
                      //检查这个bean有没有处于循环依赖创建bean状态
      				if (isSingletonCurrentlyInCreation(beanName)) {
      					logger.debug("Returning eagerly cached instance of singleton bean '" + beanName +
      							"' that is not fully initialized yet - a consequence of a circular reference");
      				}
      				else {
      					logger.debug("Returning cached instance of singleton bean '" + beanName + "'");
      				}
      			}
                  //拿到bean对象
      			bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
      		}
      
      		else {
      			// Fail if we're already creating this bean instance:
      			// We're assumably within a circular reference.
                  //如果sharedInstance是空的，说明这个bean之前没被缓存过，检测是否存在循环依赖现象，如果是则抛出异常
      			if (isPrototypeCurrentlyInCreation(beanName)) {
      				throw new BeanCurrentlyInCreationException(beanName);
      			}
       
      			// Check if bean definition exists in this factory.
                  //检查bean工厂是否还有父级的bean工厂
      			BeanFactory parentBeanFactory = getParentBeanFactory();
                 
      			if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
      				// Not found -> check parent.
      				String nameToLookup = originalBeanName(name);
      				if (args != null) {
      					// Delegation to parent with explicit args.
      					return (T) parentBeanFactory.getBean(nameToLookup, args);
      				}
      				else {
      					// No args -> delegate to standard getBean method.
      					return parentBeanFactory.getBean(nameToLookup, requiredType);
      				}
      			}
      
      			if (!typeCheckOnly) {
                     //标记这个bean已经被创建
      				markBeanAsCreated(beanName);
      			}
      
      			try {
                      //获取bean定义
      				final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
                      //检查bena是否抽象，如果是则抛异常
      				checkMergedBeanDefinition(mbd, beanName, args);
      				//如果bean有知名depends-on的bean（xml中用这个指定）也就是在创建这个bean之前要创建哪些bean，那么我们就要拿到这些要提前准备好的bean
      				String[] dependsOn = mbd.getDependsOn();
      				if (dependsOn != null) {
                          //遍历要提前创建的bean
      					for (String dep : dependsOn) {
                              //检测是否这个bean也要循环依赖现在的bean，如果是则出现循环依赖问题，抛出异常！
      						if (isDependent(beanName, dep)) {
      							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
      									"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
      						}
                              //如果不是就组测这个要循环依赖的bean
      						registerDependentBean(dep, beanName);
                              //提前创建要依赖的bean，逻辑一样
      						getBean(dep);
      					}
      				}
      
      				// Create bean instance.
                      //检测bean是否为单例
      				if (mbd.isSingleton()) {
                          //调用createBean(beanName, mbd, args)方法创建返回bean
      					sharedInstance = getSingleton(beanName, new ObjectFactory<Object>() {
      						@Override
      						public Object getObject() throws BeansException {
      							try {
                                      //这一步真正的创建bean
      								return createBean(beanName, mbd, args);
      							}
      							catch (BeansException ex) {
      								// Explicitly remove instance from singleton cache: It might have been put there
      								// eagerly by the creation process, to allow for circular reference resolution.
      								// Also remove any beans that received a temporary reference to the bean.
      								destroySingleton(beanName);
      								throw ex;
      							}
      						}
      					});
      					bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
      				}
      
      				else if (mbd.isPrototype()) {
      					// It's a prototype -> create a new instance.
      					Object prototypeInstance = null;
      					try {
      						beforePrototypeCreation(beanName);
      						prototypeInstance = createBean(beanName, mbd, args);
      					}
      					finally {
      						afterPrototypeCreation(beanName);
      					}
      					bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
      				}
      
      				else {
      					String scopeName = mbd.getScope();
      					final Scope scope = this.scopes.get(scopeName);
      					if (scope == null) {
      						throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
      					}
      					try {
      						Object scopedInstance = scope.get(beanName, new ObjectFactory<Object>() {
      							@Override
      							public Object getObject() throws BeansException {
      								beforePrototypeCreation(beanName);
      								try {
      									return createBean(beanName, mbd, args);
      								}
      								finally {
      									afterPrototypeCreation(beanName);
      								}
      							}
      						});
      						bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
      					}
      					catch (IllegalStateException ex) {
      						throw new BeanCreationException(beanName,
      								"Scope '" + scopeName + "' is not active for the current thread; consider " +
      								"defining a scoped proxy for this bean if you intend to refer to it from a singleton",
      								ex);
      					}
      				}
      			}
      			catch (BeansException ex) {
      				cleanupAfterBeanCreationFailure(beanName);
      				throw ex;
      			}
      		}
      
      		// Check if required type matches the type of the actual bean instance.
      		if (requiredType != null && bean != null && !requiredType.isAssignableFrom(bean.getClass())) {
      			try {
      				return getTypeConverter().convertIfNecessary(bean, requiredType);
      			}
      			catch (TypeMismatchException ex) {
      				if (logger.isDebugEnabled()) {
      					logger.debug("Failed to convert bean '" + name + "' to required type '" +
      							ClassUtils.getQualifiedName(requiredType) + "'", ex);
      				}
      				throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
      			}
      		}
      		return (T) bean;
      	}
      ```

      注意上面的代码中的步骤

      1. 拿到bean真正的名字transformedBeanName(name)
      
      2. 根据bean的名字去查询是否已经被缓存过了 getSingleton(beanName);
   
      1. 如果创建再缓存中就去拿到bean对象bean = getObjectForBeanInstance(sharedInstance, name, beanName, null)
        
      3. 如果没有存在缓存中就去检测这个bean是否存在循环依赖问题
      
      4. 如果不存在循环依赖问题，就尝试获取是否存在父级bean工厂getParentBeanFactory();
   
5. 如果可以获取到父类工厂，并且子工厂中没有bean定义，就去父级工厂获取bean对象
  
6. 如果没有父级工厂，首先要将这个bean标记已创建状态markBeanAsCreated(beanName);
  
7. 获取这个bena的定义信息getMergedLocalBeanDefinition(beanName);
  
8. 检查这个bean是否抽象
      
      9. 如果bean有知名depends-on的bean（xml中用这个指定）也就是在创建这个bean之前要创建哪些bean，那么我们就要拿到这些要提前准备好的bean（调用getBean(dep)）
      
      10. 如果不存在bean实例，mbd.isSingleton()检测bean是否单例。
      
          1. 如果是单例bean，就调用getSingleton，并且传入ObjectFactory参数，其中这个参数的getObject()方法又return createBean(beanName, mbd, args)。所以最终调用创建bean方法是 createBean方法。
      
          ```java
          
          public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
          		synchronized (this.singletonObjects) {
                      //尝试获取bean
          			Object singletonObject = this.singletonObjects.get(beanName);
                      //如果是空
          			if (singletonObject == null) {
                          //检测是否该bean正在销毁，如果是就抛异常
          				if (this.singletonsCurrentlyInDestruction) {
          					throw new BeanCreationNotAllowedException(beanName,
          							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
          							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
          				}
          			    //在单实例初始化bean之前的操作，主要检测bean是否当前正被创建，以及创建排除bean队列（inCreationCheckExclusions.contain（beanName））是否包含它
          				beforeSingletonCreation(beanName);
          				boolean newSingleton = false;
                          boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
          				if (recordSuppressedExceptions) {
          					this.suppressedExceptions = new LinkedHashSet<Exception>();
          				}
          				try {
                              //这里最后调用singletonFactory.getObject()其实就是createBean!!!!
          					singletonObject = singletonFactory.getObject();
          					//标记新实例
                              newSingleton = true;
          				}
          				catch (IllegalStateException ex) {
          					// Has the singleton object implicitly appeared in the meantime ->
          					// if yes, proceed with it since the exception indicates that state.
          					singletonObject = this.singletonObjects.get(beanName);
          					if (singletonObject == null) {
          						throw ex;
          					}
          				}
          				catch (BeanCreationException ex) {
          					if (recordSuppressedExceptions) {
          						for (Exception suppressedException : this.suppressedExceptions) {
          							ex.addRelatedCause(suppressedException);
          						}
          					}
          					throw ex;
          				}
          				finally {
          					if (recordSuppressedExceptions) {
          						this.suppressedExceptions = null;
          					}
          					afterSingletonCreation(beanName);
          				}
          				if (newSingleton) {
          					addSingleton(beanName, singletonObject);
        				}
            			}
        			return (singletonObject != NULL_OBJECT ? singletonObject : null);
             		}
             	}
          ```
       
         ```
             
         createBean代码
             
          ```java
          protected Object createBean(String beanName, RootBeanDefinition mbd, Object[] args) throws BeanCreationException {
          		
          		RootBeanDefinition mbdToUse = mbd;
          
          		// Make sure bean class is actually resolved at this point, and
          		// clone the bean definition in case of a dynamically resolved Class
          		// which cannot be stored in the shared merged bean definition.
               
          		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
          		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
          			mbdToUse = new RootBeanDefinition(mbd);
          			mbdToUse.setBeanClass(resolvedClass);
          		}
          
          		// Prepare method overrides.
          		try {
          			mbdToUse.prepareMethodOverrides();
          		}
          		catch (BeanDefinitionValidationException ex) {
          			throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
          					beanName, "Validation of method overrides failed", ex);
          		}
          
          		try {
          		    //这个调用InstantiationAwareBeanPostProcessors的前置处理方法和后置处理方法，分别在bean创建前后调用（如果可以返回bean的话），就是给个机会去返回代理对象。
          			Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
          			if (bean != null) {
                          
          				return bean;
          			}
          		}
          		catch (Throwable ex) {
          			throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
          					"BeanPostProcessor before instantiation of bean failed", ex);
          		}
                  //如果之前的步骤都没能返回bean，那就用doCreateBean创建bean
       		Object beanInstance = doCreateBean(beanName, mbdToUse, args);
          		if (logger.isDebugEnabled()) {
        			logger.debug("Finished creating instance of bean '" + beanName + "'");
          		}
              //返回bean实例
          		return beanInstance;
          	}
         ```
      
          在上面的代码中，首先去检测执行所有类型为InstantiationAwareBeanPostProcessors的处理器，执行它的前置方法，如果前置方法有返回bean对象，就执行它的后置方法，如果有返回bean就直接返回（就是给后置处理器一个机会返回代理bean对象）。如果没有那么我们就执行doCreateBean方法创建bean
          
          ```java
          protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final Object[] args)
          			throws BeanCreationException {
          
          		// Instantiate the bean.
          		BeanWrapper instanceWrapper = null;
          		if (mbd.isSingleton()) {
                      //尝试的从factoryBean实例缓存中获取这个bean
          			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
          		}
              //如果没有能获取到bean就执行createBeanInstance(beanName, mbd, args);创建bean
          		if (instanceWrapper == null) {
                      //创建bean实例
          			instanceWrapper = createBeanInstance(beanName, mbd, args);
          		}
                  //获取真正的bean对象
          		final Object bean = (instanceWrapper != null ? instanceWrapper.getWrappedInstance() : null);
              //获取bean的类型
          		Class<?> beanType = (instanceWrapper != null ? instanceWrapper.getWrappedClass() : null);
                //处理的目标类型为bean的类型
          		mbd.resolvedTargetType = beanType;
          
          		// Allow post-processors to modify the merged bean definition.
            
          		synchronized (mbd.postProcessingLock) {
          			if (!mbd.postProcessed) {
          				try {  
                              //让后置处理器MergedBeanDefinitionPostProcessors处理bean，他会去遍历所有的后置处理器看看是否为MergedBeanDefinitionPostProcessor类型的，如果是则bdp.postProcessMergedBeanDefinition(mbd, beanType, beanName);执行这个方法处理传入的bean。此时bean没有给属性赋值
          					applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
          				}
          				catch (Throwable ex) {
          					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
          							"Post-processing of merged bean definition failed", ex);
          				}
          				mbd.postProcessed = true;
          			}
          		}
          
          		// Eagerly cache singletons to be able to resolve circular references
          		// even when triggered by lifecycle interfaces like BeanFactoryAware.
          		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
          				isSingletonCurrentlyInCreation(beanName));
          		if (earlySingletonExposure) {
          			if (logger.isDebugEnabled()) {
          				logger.debug("Eagerly caching bean '" + beanName +
          						"' to allow for resolving potential circular references");
          			}
          			addSingletonFactory(beanName, new ObjectFactory<Object>() {
          				@Override
          				public Object getObject() throws BeansException {
          					return getEarlyBeanReference(beanName, mbd, bean);
          				}
          			});
          		}
          
          		// Initialize the bean instance.
          		Object exposedObject = bean;
          		try {
                      //初始化bean，为bean赋值
          			populateBean(beanName, mbd, instanceWrapper);
          			if (exposedObject != null) {
                          //调用bean的Aware接口和BeanPostProcessor接口的处理方法
          				exposedObject = initializeBean(beanName, exposedObject, mbd);
          			}
          		}
          		catch (Throwable ex) {
          			if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
          				throw (BeanCreationException) ex;
          			}
          			else {
          				throw new BeanCreationException(
          						mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
          			}
          		}
          
          		if (earlySingletonExposure) {
          			Object earlySingletonReference = getSingleton(beanName, false);
          			if (earlySingletonReference != null) {
          				if (exposedObject == bean) {
          					exposedObject = earlySingletonReference;
          				}
          				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
          					String[] dependentBeans = getDependentBeans(beanName);
          					Set<String> actualDependentBeans = new LinkedHashSet<String>(dependentBeans.length);
          					for (String dependentBean : dependentBeans) {
          						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
          							actualDependentBeans.add(dependentBean);
          						}
          					}
          					if (!actualDependentBeans.isEmpty()) {
          						throw new BeanCurrentlyInCreationException（省略信息）
          				}
          			}
          		}
          
          		// Register bean as disposable.
          		try {
                      //注册bean的销毁方法
          			registerDisposableBeanIfNecessary(beanName, bean, mbd);
        		}
          		catch (BeanDefinitionValidationException ex) {
          			throw new BeanCreationException(
          					mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
          		}
          
          		return exposedObject;
          	}
      ```
          
          ```java
          protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, Object[] args) {
          		// Make sure bean class is actually resolved at this point.
                  //获取bean的类型
          		Class<?> beanClass = resolveBeanClass(mbd, beanName);
          
          		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
          			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
          					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
          		}
          
          		if (mbd.getFactoryMethodName() != null)  {
                      //这里真正的调用bean的初始化，并创建处bean（getObject）
                return instantiateUsingFactoryMethod(beanName, mbd, args);
        		}
          
          		//下面无关代码暂时省略
          	}
          
          
      ```
      
      初始化赋值bean
          
          ```java
          protected void populateBean(String beanName, RootBeanDefinition mbd, BeanWrapper bw) {
          		PropertyValues pvs = mbd.getPropertyValues();
          
          		if (bw == null) {
          			if (!pvs.isEmpty()) {
          				throw new BeanCreationException(
          						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
          			}
          			else {
          				// Skip property population phase for null instance.
          				return;
          			}
          		}
          
          		// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
          		// state of the bean before properties are set. This can be used, for example,
          		// to support styles of field injection.
          		boolean continueWithPropertyPopulation = true;
          
              //给InstantiationAwareBeanPostProcessors一个机会，在bean属性赋值之前，对bean进行改造
          		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
                      //遍历所有的bean，然后判断是否为InstantiationAwareBeanPostProcessors类型的
          			for (BeanPostProcessor bp : getBeanPostProcessors()) {
          				if (bp instanceof InstantiationAwareBeanPostProcessor) {
                              //如果是就强转
          					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
                              //调用它的postProcessAfterInstantiation方法，看看是否返回true
          					if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
                                  //如果是就不再继续为属性赋值
          						continueWithPropertyPopulation = false;
          						break;
          					}
          				}
          			}
          		}
          
          		if (!continueWithPropertyPopulation) {
          			return;
          		}
          
          		if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME ||
          				mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
          			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
          
          			// Add property values based on autowire by name if applicable.
          			if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_NAME) {
          				autowireByName(beanName, mbd, bw, newPvs);
          			}
          
          			// Add property values based on autowire by type if applicable.
          			if (mbd.getResolvedAutowireMode() == RootBeanDefinition.AUTOWIRE_BY_TYPE) {
          				autowireByType(beanName, mbd, bw, newPvs);
          			}
          
          			pvs = newPvs;
          		}
          
          		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
          		boolean needsDepCheck = (mbd.getDependencyCheck() != RootBeanDefinition.DEPENDENCY_CHECK_NONE);
          
          		if (hasInstAwareBpps || needsDepCheck) {
          			PropertyDescriptor[] filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
          			if (hasInstAwareBpps) {
          				for (BeanPostProcessor bp : getBeanPostProcessors()) {
          					if (bp instanceof InstantiationAwareBeanPostProcessor) {
          						InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
          						pvs = ibp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
          						if (pvs == null) {
          							return;
          						}
          					}
          				}
        			}
           			if (needsDepCheck) {
       				checkDependencies(beanName, mbd, filteredPds, pvs);
           			}
          		}
       
          		applyPropertyValues(beanName, mbd, bw, pvs);
         	}
      ```
          
      调用beanPostProcessor，以及bean的初始化方法
          
          ```java
          protected Object initializeBean(final String beanName, final Object bean, RootBeanDefinition mbd) {
          		if (System.getSecurityManager() != null) {
          			AccessController.doPrivileged(new PrivilegedAction<Object>() {
          				@Override
          				public Object run() {
                              //触发Aware的方法
          					invokeAwareMethods(beanName, bean);
          					return null;
          				}
          			}, getAccessControlContext());
          		}
          		else {
                       //触发Aware接口的方法，这里会判断是否为BeanNameAware/BeanClassLoaderAware/BeanFactoryAware
          			invokeAwareMethods(beanName, bean);
          		}
          
          		Object wrappedBean = bean;
          		if (mbd == null || !mbd.isSynthetic()) {
                      //调用BeanPostProcessorsBeforeInitialization方法
          			wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
          		}
          
          		try {
                      //触发init-method方法，bean初始化前调用这里还会判断是否实现了InitializingBean接口
          			invokeInitMethods(beanName, wrappedBean, mbd);
          		}
          		catch (Throwable ex) {
          			throw new BeanCreationException(
          					(mbd != null ? mbd.getResourceDescription() : null),
          					beanName, "Invocation of init method failed", ex);
          		}
          
        		if (mbd == null || !mbd.isSynthetic()) {
           			//触发BeanPostProcessorsAfterInitialization
                   wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
          		}
            //返回处理后的bean
          		return wrappedBean;
          	}
          
      ```
      
      上面的步骤会先去创造出bean的实例，然后通过populateBean为属性赋值，再通过initializeBean调用bean继承的Aware接口方法，再调用BeanPostProcessorsBeforeInitialization的前置处理方法，然后调用bean指定的初始化方法，最后调用BeanPostProcessorsAfterInitialization的后置处理方法。
          
      当上述步骤完成后，bean就已经初是化完毕了
      这个时候再去将bean的销毁方法进行注册就结束了bean的创建流程。
      
      
      
      
      ​       