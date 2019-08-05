# Spring注解驱动开发（五）----声明式事务

**事务的概念**

理解事务之前，先讲一个你日常生活中最常干的事：取钱。 
比如你去ATM机取1000块钱，大体有两个步骤：首先输入密码金额，银行卡扣掉1000元钱；然后ATM出1000元钱。这两个步骤必须是要么都执行要么都不执行。如果银行卡扣除了1000块但是ATM出钱失败的话，你将会损失1000元；如果银行卡扣钱失败但是ATM却出了1000块，那么银行将损失1000元。所以，如果一个步骤成功另一个步骤失败对双方都不是好事，如果不管哪一个步骤失败了以后，整个取钱过程都能回滚，也就是完全取消所有操作的话，这对双方都是极好的。 
事务就是用来解决类似问题的。事务是一系列的动作，它们综合在一起才是一个完整的工作单元，这些动作必须全部完成，如果有一个失败的话，那么事务就会回滚到最开始的状态，仿佛什么都没发生过一样。 
在企业级应用程序开发中，事务管理必不可少的技术，用来确保数据的完整性和一致性。 

**事务的四种特性ACID（原子性，一致性，隔离性，持久性）**

**Spring中的事务管理器**

Spring并不直接管理事务，而是提供了多种事务管理器，他们将事务管理的职责委托给Hibernate或者JTA等持久化机制所提供的相关平台框架的事务来实现。 
Spring事务管理器的接口是org.springframework.transaction.PlatformTransactionManager，通过这个接口，Spring为各个平台如JDBC、Hibernate等都提供了对应的事务管理器，但是具体的实现就是各个平台自己的事情了。我们可以看下事务管理器的内容：

```java
Public interface PlatformTransactionManager()...{  
    // 由TransactionDefinition得到TransactionStatus对象
    TransactionStatus getTransaction(TransactionDefinition definition) throws TransactionException; 
    // 提交
    Void commit(TransactionStatus status) throws TransactionException;  
    // 回滚
    Void rollback(TransactionStatus status) throws TransactionException;  
    } 
```

从这里可知具体的具体的事务管理机制对Spring来说是透明的，它并不关心那些，那些是对应各个平台需要关心的，所以Spring事务管理的一个优点就是为不同的事务API提供一致的编程模型，如JTA、JDBC、Hibernate、JPA。

如果应用程序中直接使用JDBC来进行持久化。我们就需要用到DataSourceTransactionManager，并传入当前的数据源。实际上，DataSourceTransactionManager是通过调用java.sql.Connection来管理事务，而前者是通过DataSource获取到的。通过调用连接的commit()方法来提交事务，同样，事务失败则通过调用rollback()方法进行回滚。

**我们举个例子去更好的说明Spring中事务时如何起到作用的**

要用到事务管理数据源，我们必须先要配置好数据源，为了方便使用spring操作数据库，需要导入spring-jdbc模块。下面我们先声明一个配置类，并将数据源，JDBCTemplate，以及管理数据源的事务管理器声明好,加到spring容器中。

```java
/**
 * 声明式事务
 *
 * 1.导入相关依赖
 *      数据源，数据驱动，spring-jdbc模块
 * 2.配置数据源、JdbcTemplate（Spring简化数据库的操作工具）操作数据
     给想要进行事务管理的方法标注@Transactional注解
 * 3.@EnableTransactionManagement开启spring事务
 * 4.声明PlatformTransactionManager管理数据源
 */
@EnableTransactionManagement
@Configuration
@ComponentScan
@PropertySource("classpath:datasource.properties")
public class TxConfiguration implements EmbeddedValueResolverAware {

    StringValueResolver stringValueResolver ;
    String driverClass;
    String username ;
    String password;
    @Value("${db.basePath}")
    String basePath ;

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        //拓展，使用EmbeddedValueResolverAware接口去获取配置文件中的值
        this.stringValueResolver = resolver;
        driverClass = stringValueResolver.resolveStringValue("${db.driverClass}");
        username = stringValueResolver.resolveStringValue("${db.user}");
        password = stringValueResolver.resolveStringValue("${db.password}");
    }

    /**
     * 创建数据源
     */
    @Bean
    public DataSource dataSource() throws PropertyVetoException, NamingException {
        ComboPooledDataSource comboPooledDataSource = new ComboPooledDataSource();
        comboPooledDataSource.setDriverClass(driverClass);
        comboPooledDataSource.setUser(username);
        comboPooledDataSource.setPassword(password);
        comboPooledDataSource.setJdbcUrl(basePath+"test?useSSL=false");
        return  comboPooledDataSource;
    }

    /**
     * 创建spring对数据库的操作模板
     * @return
     * @throws PropertyVetoException
     * @throws NamingException
     */
    @Bean
    public JdbcTemplate jdbcTemplate() throws PropertyVetoException, NamingException {
        //注意这里调用dataSource（）的时候不会再次执行dataSource（）的创建逻辑，spring对@Configuration注解下的组件bean的调用，会从spring容器内部去找对应的bean
        return new JdbcTemplate(dataSource());
    }

    /**
     * 声明事务管理器，必须把数据源注入进去，这样这个管理器才能去管理数据源的每个连接
     * @return
     */
    @Bean
    public PlatformTransactionManager platformTransactionManager() throws PropertyVetoException, NamingException {
        return new DataSourceTransactionManager(dataSource());
    }
}

```

配置类必须标注有@EnableTransactionManagement和 PlatformTransactionManager返回类型的bean，以及数据源3样东西，只有这样spring事务才可以管理每一条数据源的连接。数据源的配置文件大致如下

```properties
db.user=root
db.password=luo44091998
db.driverClass=com.mysql.jdbc.Driver
db.basePath=jdbc:mysql://localhost:3306/
```

先定义一张表，这里声明在test数据库中的user表,将id设为自增列代表主键

![1564555723799](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564555723799.png)

接下来我们定义DAO以及Service层的信息，像数据库中test的user表插入数据

```java
//UserDao实现
@Repository
public class UserDao {

    @Autowired
    JdbcTemplate jdbcTemplate;

   public void insertDao(){
       String username = UUID.randomUUID().toString();
       String password = UUID.randomUUID().toString();
       //插入一条数据
       jdbcTemplate.update("insert into user(username,password) values(?,?)",username,password);
   }
}


//UserService实现
@Service
public class UserService {

    @Autowired
    UserDao userDao;

    //告诉spring这个方法时要用事务管理的，如果没有spring就无法对这个方法进行事务控制！
    @Transactional
    public void insertUser(){
        userDao.insertDao();
        //显示插入成功
        System.out.println("插入成功");
        //这里故意给个报错，演示事务效果
        int i = 10/0 ;
    }
}
```

看到UserService中insetUser内部处理插入数据之后，故意的制作了一个异常，观察数据是否能碰到异常之后进行回滚，我们新建测试

```java
 /**
     * 测试spring事务
     */
    @Test
    public void testTx(){
        AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext(TxConfiguration.class);
        UserService userService = annotationConfigApplicationContext.getBean(UserService.class);
        userService.insertUser();
    }
```

运行结果：![1564556580517](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1564556580517.png)

你可以去数据库核对，刚刚那条数据记录时不会出现在user表中的，这说明**spring对这个inserUser方法进行了事务回滚，只要这个方法流程的任意一步出问题，就要对这个方法进行回滚**

如果你把@EnableTransactionManagement或者@Transactional直接去掉，那么事务就会失效，也就是spring就无法使用事务功能或者目标方法出现异常后不能进行数据库回滚。



**@EnableTransactionManagement的原理**

同样，为什么必须加@EnableTransactionManagement Spring才能为我们开启事务控制呢？也就是它究竟给容器注入了哪些组件？起到了什么作用？我们点进这个注解

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
//看到这里导入了TransactionManagementConfigurationSelector这个类
@Import(TransactionManagementConfigurationSelector.class)
public @interface EnableTransactionManagement {

	boolean proxyTargetClass() default false;

	AdviceMode mode() default AdviceMode.PROXY;

	/**
	  当有很多增强器作用于指定的接口时，指定事务增强器的执行顺序
	 */
	int order() default Ordered.LOWEST_PRECEDENCE;

}

```

这个注解利用@Import导入了(TransactionManagementConfigurationSelector，我们点入这个类

```java
public class TransactionManagementConfigurationSelector extends AdviceModeImportSelector<EnableTransactionManagement> {

	/**
	 * {@inheritDoc}
	 * @return {@link ProxyTransactionManagementConfiguration} or
	 * {@code AspectJTransactionManagementConfiguration} for {@code PROXY} and
	 * {@code ASPECTJ} values of {@link EnableTransactionManagement#mode()}, respectively
	 */
	@Override
	protected String[] selectImports(AdviceMode adviceMode) {
		switch (adviceMode) {
			case PROXY:
				return new String[] {AutoProxyRegistrar.class.getName(), ProxyTransactionManagementConfiguration.class.getName()};
			case ASPECTJ:
				return new String[] {TransactionManagementConfigUtils.TRANSACTION_ASPECT_CONFIGURATION_CLASS_NAME};
			default:
				return null;
		}
	}
}
```

这个方法很简单，重写了ImportSelector的选择selectImports的方法，它会拿到AdviceMode，去判断adviceMode时不是PROXY类型或者是ASPECTJ类型，这里的adviceMode就是@EnableTransactionManagement的参数，如果没指定adviceMode，**默认为PROXY类型**。 

**所以@EnableTransactionManagement注解最终注入了AutoProxyRegistrar和ProxyTransactionManagementConfiguration两个组件到spring容器中，我们分别来看看到底他们做了什么**

1. AutoProxyRegistrar

   ```java
   public class AutoProxyRegistrar implements ImportBeanDefinitionRegistrar {
   
   	private final Log logger = LogFactory.getLog(getClass());
   
   	/**
   	 * Register, escalate, and configure the standard auto proxy creator (APC) against the
   	 * given registry. Works by finding the nearest annotation declared on the importing
   	 * {@code @Configuration} class that has both {@code mode} and {@code proxyTargetClass}
   	 * attributes. If {@code mode} is set to {@code PROXY}, the APC is registered; if
   	 * {@code proxyTargetClass} is set to {@code true}, then the APC is forced to use
   	 * subclass (CGLIB) proxying.
   	 * <p>Several {@code @Enable*} annotations expose both {@code mode} and
   	 * {@code proxyTargetClass} attributes. It is important to note that most of these
   	 * capabilities end up sharing a {@linkplain AopConfigUtils#AUTO_PROXY_CREATOR_BEAN_NAME
   	 * single APC}. For this reason, this implementation doesn't "care" exactly which
   	 * annotation it finds -- as long as it exposes the right {@code mode} and
   	 * {@code proxyTargetClass} attributes, the APC can be registered and configured all
   	 * the same.
   	 */
   	@Override
   	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
   		boolean candidateFound = false;
           //拿到配置类的所有标注的注解，比如@Configuration,@EnableTransactionManagement
   		Set<String> annoTypes = importingClassMetadata.getAnnotationTypes();
   		//遍历这些注解
           for (String annoType : annoTypes) {
               //解析每个注解内部的属性值，并保存在candidate中
   			AnnotationAttributes candidate = AnnotationConfigUtils.attributesFor(importingClassMetadata, annoType);
               //如果属性值为空，就跳过这个注解继续
   			if (candidate == null) {
   				continue;
   			}
               //找到这个注解中的mode属性
   			Object mode = candidate.get("mode");
               //找到这个注解中的proxyTargetClass属性
   			Object proxyTargetClass = candidate.get("proxyTargetClass");
   		
               if (mode != null && proxyTargetClass != null && AdviceMode.class == mode.getClass() &&
   					Boolean.class == proxyTargetClass.getClass()) { 
   				candidateFound = true;
   				if (mode == AdviceMode.PROXY) {
   				//注册AutoProxyCreator，和AOP的原理一样              
                       AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
   					if ((Boolean) proxyTargetClass) {
   						AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
   						return;
   					}
   				}
   			}
   		}
   		if (!candidateFound) {
   			String name = getClass().getSimpleName();
   			logger.warn(String.format("%s was imported but no annotations were found " +
   					"having both 'mode' and 'proxyTargetClass' attributes of type " +
   					"AdviceMode and boolean respectively. This means that auto proxy " +
   					"creator registration and configuration may not have occurred as " +
   					"intended, and components may not be proxied as expected. Check to " +
   					"ensure that %s has been @Import'ed on the same class where these " +
   					"annotations are declared; otherwise remove the import of %s " +
   					"altogether.", name, name, name));
   		}
   	}
   
   }
   
   ```

   可以看到AutoProxyRegistrar实现了 ImportBeanDefinitionRegistrar又给spring容器注入了一些组件，看到这一行 AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry); 主要做了下面两步

   ```java
   public static BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry) {
   		return registerAutoProxyCreatorIfNecessary(registry, null);
   	}
   
   	public static BeanDefinition registerAutoProxyCreatorIfNecessary(BeanDefinitionRegistry registry, Object source) {
   		return registerOrEscalateApcAsRequired(InfrastructureAdvisorAutoProxyCreator.class, registry, source);
   	}
   
   ```

   其中registerAutoProxyCreatorIfNecessary又会调用**registerOrEscalateApcAsRequired(InfrastructureAdvisorAutoProxyCreator.class, registry, source);**这个方法。看到这个方法传入了InfrastructureAdvisorAutoProxyCreator.class参数。如果你继续点进去会发现最后它是个BeanFactoryProcessor，并且还是**InstantiationAwareBeanPostProcessor，它会在单实例bean没有创建的时候尝试去获取一个代理对象！会对每个bean创建前后进行一次拦截**

   ```java
   //InfrastructureAdvisorAutoProxyCreator的继承树
   InfrastructureAdvisorAutoProxyCreator
       --AbstractAdvisorAutoProxyCreator
           --AbstractAdvisorAutoProxyCreator
               --ProxyProcessorSupport
               --SmartInstantiationAwareBeanPostProcessor  // 跟AOP是原理是一样的
                   --InstantiationAwareBeanPostProcessor
                       --BeanPostProcessor
               --BeanFactoryAware
   ```

   下面代码就是讲明如何将InfrastructureAdvisorAutoProxyCreator注入到spring容器中去的

   ```java
   	private static BeanDefinition registerOrEscalateApcAsRequired(Class<?> cls, BeanDefinitionRegistry registry, Object source) {
   		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
   		if (registry.containsBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME)) {
   			BeanDefinition apcDefinition = registry.getBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME);
   			if (!cls.getName().equals(apcDefinition.getBeanClassName())) {
   				int currentPriority = findPriorityForClass(apcDefinition.getBeanClassName());
   				int requiredPriority = findPriorityForClass(cls);
   				if (currentPriority < requiredPriority) {
   					apcDefinition.setBeanClassName(cls.getName());
   				}
   			}
   			return null;
   		}
           //如果当前的spring容器并不存在AUTO_PROXY_CREATOR_BEAN_NAME，就将当前传入的InfrastructureAdvisorAutoProxyCreator注册到spring容器中。
   		RootBeanDefinition beanDefinition = new RootBeanDefinition(cls);
   		beanDefinition.setSource(source);
   		beanDefinition.getPropertyValues().add("order", Ordered.HIGHEST_PRECEDENCE);
   		beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
   		registry.registerBeanDefinition(AUTO_PROXY_CREATOR_BEAN_NAME, beanDefinition);
   		return beanDefinition;
   	}
   ```

   在注册完InfrastructureAdvisorAutoProxyCreator之后，剩下的操作就和springAop的AnootationAnnotationAwareAspectJAutoProxyCreator一样了。利用后置处理器机制在对象创建完成之后，包装对象，返回一个代理对象（增强器），代理对象执行方法，将增强器包装成拦截器，会进行链式调用拦截器。

2. ProxyTransactionManagementConfiguration

   ```java
   @Configuration
   public class ProxyTransactionManagementConfiguration extends AbstractTransactionManagementConfiguration {
   
   	@Bean(name = TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME)
   	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
       
       //给spring容器中注入一个增强器
   	public BeanFactoryTransactionAttributeSourceAdvisor transactionAdvisor() {
           
   		BeanFactoryTransactionAttributeSourceAdvisor advisor = new BeanFactoryTransactionAttributeSourceAdvisor();
           //需要传入事务的注解信息（这里会解析@Transactional的属性）
   		advisor.setTransactionAttributeSource(transactionAttributeSource());
           //需要传入拦截器
   		advisor.setAdvice(transactionInterceptor());
   		advisor.setOrder(this.enableTx.<Integer>getNumber("order"));
   		return advisor;
   	}
   
   	@Bean
   	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
   	public TransactionAttributeSource transactionAttributeSource() {
   		return new AnnotationTransactionAttributeSource();
   	}
   
   	@Bean
   	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
   	public TransactionInterceptor transactionInterceptor() {
   		TransactionInterceptor interceptor = new TransactionInterceptor();
   		interceptor.setTransactionAttributeSource(transactionAttributeSource());
   		if (this.txManager != null) {
   			interceptor.setTransactionManager(this.txManager);
   		}
   		return interceptor;
   	}
   
   }
```
   
看到transactionAttributeSource() 内部又新建了AnnotationTransactionAttributeSource();点进这个构造器
   
   ```java
   public AnnotationTransactionAttributeSource(boolean publicMethodsOnly) {
   		this.publicMethodsOnly = publicMethodsOnly;
       //这里就是事务的注解解析器
   		this.annotationParsers = new LinkedHashSet<TransactionAnnotationParser>(2);
       //包括spring的事务注解解析器和jta的解析器以及ejb的注解解析器
   		this.annotationParsers.add(new SpringTransactionAnnotationParser());
   		if (jta12Present) {
   			this.annotationParsers.add(new JtaTransactionAnnotationParser());
   		}
   		if (ejb3Present) {
   			this.annotationParsers.add(new Ejb3TransactionAnnotationParser());
   		}
   	}
   
```
   
继续点进spring的事务注解解析器中
   
   ```java
   //这一个方法就是用于解析事务的注解的，具体的逻辑在 parseTransactionAnnotation中
   @Override
   	public TransactionAttribute parseTransactionAnnotation(AnnotatedElement ae) {
   		AnnotationAttributes attributes = AnnotatedElementUtils.getMergedAnnotationAttributes(ae, Transactional.class);
   		if (attributes != null) {
   			return parseTransactionAnnotation(attributes);
   		}
   		else {
   			return null;
   		}
   	}
   
   //parseTransactionAnnotation(attributes);
   //这里就会对事务的行为等（@Transactional标注的属性进行解析）
   protected TransactionAttribute parseTransactionAnnotation(AnnotationAttributes attributes) {
   		RuleBasedTransactionAttribute rbta = new RuleBasedTransactionAttribute();
   		Propagation propagation = attributes.getEnum("propagation");
   		rbta.setPropagationBehavior(propagation.value());
   		Isolation isolation = attributes.getEnum("isolation");
   		rbta.setIsolationLevel(isolation.value());
   		rbta.setTimeout(attributes.getNumber("timeout").intValue());
   		rbta.setReadOnly(attributes.getBoolean("readOnly"));
   		rbta.setQualifier(attributes.getString("value"));
   		ArrayList<RollbackRuleAttribute> rollBackRules = new ArrayList<RollbackRuleAttribute>();
   		Class<?>[] rbf = attributes.getClassArray("rollbackFor");
   		for (Class<?> rbRule : rbf) {
   			RollbackRuleAttribute rule = new RollbackRuleAttribute(rbRule);
   			rollBackRules.add(rule);
   		}
   		String[] rbfc = attributes.getStringArray("rollbackForClassName");
   		for (String rbRule : rbfc) {
   			RollbackRuleAttribute rule = new RollbackRuleAttribute(rbRule);
   			rollBackRules.add(rule);
   		}
   		Class<?>[] nrbf = attributes.getClassArray("noRollbackFor");
   		for (Class<?> rbRule : nrbf) {
   			NoRollbackRuleAttribute rule = new NoRollbackRuleAttribute(rbRule);
   			rollBackRules.add(rule);
   		}
   		String[] nrbfc = attributes.getStringArray("noRollbackForClassName");
   		for (String rbRule : nrbfc) {
   			NoRollbackRuleAttribute rule = new NoRollbackRuleAttribute(rbRule);
   			rollBackRules.add(rule);
   		}
   		rbta.getRollbackRules().addAll(rollBackRules);
   		return rbta;
   	}
   
```
   
   1. 给容器中注册事务增强器：
      1. 事务增强器要用事务注解的信息，AnnotationTransactionAttributeSource来解析事务的注解。
   2. 事务拦截器，TransactionInterceptor，保存了事务属性信息，事务管理器。它是一个MethodInterceptor方法拦截器，什么是方法拦截器呢？ 代理对象要执行目标方法时拦截器就会进行工作，怎么工作呢？TransactionInterceptor有一个invoke方法，而invoke方法调用了 invokeWithinTransaction方法。
      
      ```java
      	protected Object invokeWithinTransaction(Method method, Class<?> targetClass, final InvocationCallback invocation)
      			throws Throwable {
      
      		// 先获取@Transactional中事务的属性
      		final TransactionAttribute txAttr = getTransactionAttributeSource().getTransactionAttribute(method, targetClass);
              //在根据这些事务属性获取transactionManager
      		final PlatformTransactionManager tm = determineTransactionManager(txAttr);
      		final String joinpointIdentification = methodIdentification(method, targetClass, txAttr);
      
      		if (txAttr == null || !(tm instanceof CallbackPreferringPlatformTransactionManager)) {
      			// Standard transaction demarcation with getTransaction and commit/rollback calls.
      			TransactionInfo txInfo = createTransactionIfNecessary(tm, txAttr, joinpointIdentification);
      			Object retVal = null;
      			try {
      				// This is an around advice: Invoke the next interceptor in the chain.
      				// This will normally result in a target object being invoked.
      				     //这里相当于方法的执行这里就和aop中链时调用的方法逻辑是一样的，会对其进行事务的拦截，如果执行方法正常，还会有返回值
                      retVal = invocation.proceedWithInvocation();
      			}
      			catch (Throwable ex) {
      				// target invocation exception
                      //这里时目标方法执行后抛出异常的解决方案	，内部会执行txInfo.getTransactionManager().rollback(txInfo.getTransactionStatus());都是事务管理起去做的回滚操作
      				completeTransactionAfterThrowing(txInfo, ex);
      				throw ex;
      			}
      			finally {
      				cleanupTransactionInfo(txInfo);
      			}
                  //如果一切顺利，就会提交更新数据库	txInfo.getTransactionManager().commit(txInfo.getTransactionStatus());同样事务管理器会执行commit方法提交事务
      			commitTransactionAfterReturning(txInfo);
                  //返回目标方法的执行结果
      			return retVal;
      		}
      
      		else {
      			// It's a CallbackPreferringPlatformTransactionManager: pass a TransactionCallback in.
      			try {
      				Object result = ((CallbackPreferringPlatformTransactionManager) tm).execute(txAttr,
      						new TransactionCallback<Object>() {
      							@Override
      							public Object doInTransaction(TransactionStatus status) {
      								TransactionInfo txInfo = prepareTransactionInfo(tm, txAttr, joinpointIdentification, status);
      								try {
                                     
      									return invocation.proceedWithInvocation();
      								}
                                    
      								catch (Throwable ex) {
      									if (txAttr.rollbackOn(ex)) {
      										// A RuntimeException: will lead to a rollback.
      										if (ex instanceof RuntimeException) {
      											throw (RuntimeException) ex;
      										}
      										else {
      											throw new ThrowableHolderException(ex);
      										}
      									}
      									else {
      										// A normal return value: will lead to a commit.
      										return new ThrowableHolder(ex);
      									}
      								}
      								finally {
      									cleanupTransactionInfo(txInfo);
      								}
      							}
      						});
      
      				// Check result: It might indicate a Throwable to rethrow.
      				if (result instanceof ThrowableHolder) {
      					throw ((ThrowableHolder) result).getThrowable();
      				}
      				else {
      					return result;
      				}
      			}
      			catch (ThrowableHolderException ex) {
      				throw ex.getCause();
      			}
      		}
      	}
      ```
      
      上面的方法中spring会先获取目标方法中@Transactional中事务的属性，再根据这些事务属性获取transactionManager，怎么获取transactionManager呢？
      
      ```java
      //根据给出的事务属性去获取事务管理器	
      protected PlatformTransactionManager determineTransactionManager(TransactionAttribute txAttr) {
      		// Do not attempt to lookup tx manager if no tx attributes are set
                //如果事务属性没有设置的话，不会尝试获取事务管理器
      		if (txAttr == null || this.beanFactory == null) {
                  //直接返回已有的事务管理起
      			return getTransactionManager();
      		}
              //如果有@Transactional(transactionManger=***)指定事务管理器的话
      		String qualifier = txAttr.getQualifier();
              //就检测这个注解是否有值
      		if (StringUtils.hasText(qualifier)) {
                  //获取指定名字的事务管理器
      			return determineQualifiedTransactionManager(qualifier);
      		}
              //这里也是获取指定的事务管理起
      		else if (StringUtils.hasText(this.transactionManagerBeanName)) {
      			return determineQualifiedTransactionManager(this.transactionManagerBeanName);
      		}
          //如果没有指定的事务管理器的话
      		else {
                  //获取spring自动装配好的事务管理器
      			PlatformTransactionManager defaultTransactionManager = getTransactionManager();
                  //如果这个默认的事务管理器没有值
      			if (defaultTransactionManager == null) {
                      //就去缓存中找
      				defaultTransactionManager = this.transactionManagerCache.get(DEFAULT_TRANSACTION_MANAGER_KEY);
                      //如果缓存中也没有
      				if (defaultTransactionManager == null) {
                          //就去springIOC容器中创建PlatformTransactionManager这个类型的事务管理器，并返回这个事务管理器
      					defaultTransactionManager = this.beanFactory.getBean(PlatformTransactionManager.class);
                          //将这个事务管理器加载到缓存中
      					this.transactionManagerCache.putIfAbsent(
      							DEFAULT_TRANSACTION_MANAGER_KEY, defaultTransactionManager);
      				}
      			}
                  //返回默认的事务管理器
      			return defaultTransactionManager;
      		}
      	}
      ```
      
      如过之前没有指定任何transactionManger就会去spring容器找PlatformTransactionManager类型的事务管理器。
   
   3. 有了事务管理器后就执行目标方法，并且进行事务控制invocation.proceedWithInvocation()，如果抛异常就直接用事务管理器进行回滚事务，否则就提交并返回处理结果。
   
   