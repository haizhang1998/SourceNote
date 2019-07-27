# Spring注解驱动开发2

### 第一模块 bean的初始化和销毁方法

**在@Bean中指定初始化和构造方法**

我们可以在@Bean中指定bean的初始化(init-method) 和 销毁（destory-method）方法 ,当springIoc容器创建完成对象并且赋值好对象后，开始调用初始化方法。当容器关闭的时候调用销毁方法，但有个例外（proptotype不会再关闭容器时调用销毁方法!这个权力是交给你来调用）

现在来实战下：

定义一个Exmple类,并写初始化和销毁方法

```java
public class Example {

    public void initMethod(){
        System.out.println("调用初始化方法");
    }

    public void destoryMethod(){
        System.out.println("调用销毁方法");
    }

}
```

定义配置类，并再@bean注解中标注这两个方法

```java
@Configuration
public class Config4 {

    @Scope("singleton")
    @Bean(initMethod = "initMethod",destroyMethod = "destoryMethod")
    public Example example(){
        return  new Example();
    }
}
```

编写测试方法

```java
    @Test
    public void testApp4() {
      //创建ioc容器
      AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(Config4.class);
      //容器关闭
      applicationContext.close();
    }
```

测试结果

![1563798901268](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563798901268.png)

**现在我们把配置类中的@Scope作用域改变为prototype **再观测下结果

![1563799024692](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563799024692.png)

没任何的调用，说明原型模型不会再ioc创建和销毁的时候调用bean的初始化和销毁办法，但是再调用bean的时候会调用bean的初始化方法,不会调用销毁方法！

![1563799106292](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563799106292.png)



**使用@postConstruct和@preDestory注解标注在方法上**

我们可以换一种方式，不直接把初始化和摧毁方法写在@Bean中，而是直接用@postConstruct和@preDestory分别指定初始化方法和摧毁方法

```java
//修改Example类
public class Example {
    public Example(){
        System.out.println("Examle的构造方法...............");
    }
    @PostConstruct
    public void initMethod(){
        System.out.println("调用初始化方法");
    }

    @PreDestroy
    public void destoryMethod(){
        System.out.println("调用销毁方法");
    }
}


//修改后的Config4
@Configuration
public class Config4 {
    @Scope("singleton")
    @Bean
    public Example example(){
        return  new Example();
    }
}

//测试方法同上面
```

注意这里我们直接@Bean里面不用加任何内容就可以使bean的初始化和销毁方法起作用。发现初始化方法在bean对象创建完成且赋值完成后才调用

![1563845333001](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563845333001.png)



**使用InitializingBean和DisposibleBean定义bean初始化及销毁方法**

```java
public class Cat implements InitializingBean, DisposableBean {

    public Cat() {
        System.out.println("cat的构造方法");
    }

    /**
     * InitializingBean接口中的初始化方法，bean创建完成并赋值后调用
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("Cat的初始化方法");
    }

    /**
     * DisposableBean的销毁方法，在单列bean的情况下springIoc关闭时调用
     * @throws Exception
     */
    @Override
    public void destroy() throws Exception {
        System.out.println("cat的销毁方法");
    }
}

```

在Config配置类中声明bean

```java
@Bean
    public Cat cat (){
        return new Cat();
    }
```

测试结果

![1563845666233](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563845666233.png)

注意一点，如果你在一个类中实现了InitializingBean和DisposableBean的接口同时又在xml中指定了Init-method和destroy-method方法，则容器在初始化完对象后会先调用InitializingBean的初始化方法再调用Init-method指定的初始化方法，摧毁方法也同理。

```java
//这里进行一次验证
public class Cat implements InitializingBean, DisposableBean {

    public Cat() {
        System.out.println("cat的构造方法");
    }

    /**
     * InitializingBean接口中的初始化方法，bean创建完成并赋值后调用
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("Cat的初始化方法");
    }

    /**
     * DisposableBean的销毁方法，在单列bean的情况下springIoc关闭时调用
     * @throws Exception
     */
    @Override
    public void destroy() throws Exception {
        System.out.println("cat的销毁方法");
    }

    //让bean指定这个方法为初始化方法
    public void initmethod(){
        System.out.println("cat initmethod");
    }
    //让bean指定这个方法为摧毁方法
    public void destroymethod(){
        System.out.println("cat destroymethod");
    }

}



//Config类
@Configuration
public class Config{
    @Bean(initMethod = "initmethod",destroyMethod = "destroymethod")
    public Cat cat (){
        return new Cat();
    }
}

```

测试结果：

![1563846246527](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563846246527.png)



### 第二模块BeanPostProcessor原理及用法

用法介绍：(首先声明，这个不能完全叫做后置处理器，往后再讲述为什么)

BeanPostProcessor是一个接口，我们观测下它的源码定义

```java
public interface BeanPostProcessor {

	/**
	 * Apply this BeanPostProcessor to the given new bean instance <i>before</i> any bean
	 * initialization callbacks (like InitializingBean's {@code afterPropertiesSet}
	 * or a custom init-method). The bean will already be populated with property values.
	 * The returned bean instance may be a wrapper around the original.
	 * @param bean the new bean instance
	 * @param beanName the name of the bean
	 * @return the bean instance to use, either the original or a wrapped one;
	 * if {@code null}, no subsequent BeanPostProcessors will be invoked
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 
	 简单的将就是这个方法调用会在bean的自定义初始化方法前被执行，其中参数bean代表springIoc容器生成的bean实例，你可以对这个bean进行赋值。而beanName就是bean的名字。方法返回值你可以选择返回原生的Ioc容器创建的bean，也可以对其包装再返回处理后的bean
	 */
    
	Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException;

	/**
	 * Apply this BeanPostProcessor to the given new bean instance <i>after</i> any bean
	 * initialization callbacks (like InitializingBean's {@code afterPropertiesSet}
	 * or a custom init-method). The bean will already be populated with property values.
	 * The returned bean instance may be a wrapper around the original.
	 * <p>In case of a FactoryBean, this callback will be invoked for both the FactoryBean
	 * instance and the objects created by the FactoryBean (as of Spring 2.0). The
	 * post-processor can decide whether to apply to either the FactoryBean or created
	 * objects or both through corresponding {@code bean instanceof FactoryBean} checks.
	 * <p>This callback will also be invoked after a short-circuiting triggered by a
	 * {@link InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation} method,
	 * in contrast to all other BeanPostProcessor callbacks.
	 * @param bean the new bean instance
	 * @param beanName the name of the bean
	 * @return the bean instance to use, either the original or a wrapped one;
	 * if {@code null}, no subsequent BeanPostProcessors will be invoked
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 * @see org.springframework.beans.factory.FactoryBean
	 
	 这个意思和还是那个面postProcessBeforeInitialization差不多，只不过它再自定义的初始化方法调用完成后再进行调用。
	 */
	Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException;

}
```

我们只需要实现这个接口的方法，并把实现类标注为ioc容器的组件即可使用BeanPostProcessor

```java
//依旧拿之前的Cat类
public class Cat implements BeanPostProcessor {

    public Cat() {
        System.out.println("cat的构造方法");
    }


    public void initmethod(){
        System.out.println("cat initmethod");
    }

    public void destroymethod(){
        System.out.println("cat destroymethod");
    }
}



//定义后置处理器,将其加入IOC容器中
@Component
public class MyBeanPostProcessor implements BeanPostProcessor {

    //bean赋值后，initmethod前执行
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return null;
    }

    //bean赋值后，initmethod执行完后紧接着执行
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return null;
    }
}



//Config配置类把后BeanPostProcessor扫描进来
@Configuration
@ComponentScan
public class Config {
    @Bean(initMethod = "initmethod",destroyMethod = "destroymethod")
    public Cat cat (){
        return new Cat();
    }
}

```

测试方法略去（只用创建容器即可） 这里给出结果截图

![1563848194217](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563848194217.png)

结果很显然，Cat类被初始化赋值后调用BeanPostProcessor初始化方法再调用initmethod自定义初始化方法。

**BeanPostProcessor的执行内部原理**

这里我们可以打断点再BeanPostProcessor的postProcessBeforeInitialization和postProcessAfterInitialization然后点进去观看源码的调用，这里给出大体的调用流程

step1 :  spring内部首先调用populateBean 方法给bean进行赋值（赋值的时候也有spring自己定义的BeanPostProcessor执行）

step2: 接下来调用initializeBean初始化Bean

step3:调用applyBeanPostProcessorBeforeInitialization() 方法，这一步主要执行你自定义的BeanpostProcessor中的postProcessBeforeInitialization方法

step4:invokeInitMethods执行自定义初始化的方法

step5:调用applyBeanPostProcessoAfterInitialization() 方法，这一步主要执行你自定义的BeanpostProcessor的postProcessAfterInitialization方法

**拓展：** Spring底层对BeanPostProcessor使用比如：Bean赋值 ,@Autowired注解为什么可以自动导入，@Asyn异步注解，以及生命周期注解等等，都是使用Spring内部实现的BeanPostProcessor进行工作的！



### 第三模块 @Value使用

使用@Value可以给Bean赋值，使用的语法：

1. 基本数值
2. 可以写SpEL    #{}
3. 可以写${}  取出配置文件中的值（在运行环境变量里面的值）

注意点，使用${}取出配置文件中的值的时候，必须要将配置文件导入到运行环境变量中，使用@PropertySource注解，我们现在看一下它的源码

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(PropertySources.class)
public @interface PropertySource {

	/**
	 * Indicate the name of this property source. If omitted, a name will
	 * be generated based on the description of the underlying resource.
	 指定资源文件的名字，如果忽略，名字将自动创建基于底层资源的描述
	 */
	String name() default "";

	/**
	 * Indicate the resource location(s) of the properties file to be loaded.
	 * For example, {@code "classpath:/com/myco/app.properties"} or
	 * {@code "file:/path/to/file"}.
	 * <p>Resource location wildcards (e.g. *&#42;/*.properties) are not permitted;
	 * each location must evaluate to exactly one {@code .properties} resource.
	 * <p>${...} placeholders will be resolved against any/all property sources already
	 * registered with the {@code Environment}. See {@linkplain PropertySource above}
	 * for examples.
	 * <p>Each location will be added to the enclosing {@code Environment} as its own
	 * property source, and in the order declared.
	 这里就是写你资源文件的位置 一般资源文件放在resources目录下，位置就是classpath:/xxx.properties
	 */
	String[] value();

	/**
	 * Indicate if failure to find the a {@link #value() property resource} should be
	 * ignored.
	 * <p>{@code true} is appropriate if the properties file is completely optional.
	 * Default is {@code false}.
	 * @since 4.0
	 是否忽略文件找不到的结果，默认不忽略
	 */
	boolean ignoreResourceNotFound() default false;

	/**
	 * A specific character encoding for the given resources, e.g. "UTF-8".
	 * @since 4.3
	 指定资源文件编码
	 */
	String encoding() default "";

	/**
	 * Specify a custom {@link PropertySourceFactory}, if any.
	 * <p>By default, a default factory for standard resource files will be used.
	 * @since 4.3
	 * @see org.springframework.core.io.support.DefaultPropertySourceFactory
	 * @see org.springframework.core.io.support.ResourcePropertySource
	 */
	Class<? extends PropertySourceFactory> factory() default PropertySourceFactory.class;

}

```

**最后注意@PropertySource可以同时指定多个资源文件的路径，也可以标记多个@PropertySource注解**

有了上面的理解后，我们就可以使用@PropertySource注解标记然后再使用${}取对应资源文件的值

实践代码：

```java
//定义一个Man，并用@Value进行赋值
@Component("man")
public class Man {
    /**
     * 使用@Value可以给Bean赋值，使用的语法：
     *
     * 1. 基本数值
     * 2. 可以写SpEL    #{}
     * 3. 可以写${}  取出配置文件中的值（在运行环境变量里面的值）
     */
    @Value("海章")
    private  String name ;
    @Value("#{29-3}")
    private  Integer age ;
    @Value("${person.nikename}")
    private String nikeName;


    @Override
    public String toString() {
        return "Man{" +
                "name='" + name + '\'' +
                ", age=" + age +
                ", nikeName='" + nikeName + '\'' +
                '}';
    }

    public String getNikeName() {
        return nikeName;
    }

    public void setNikeName(String nikeName) {
        this.nikeName = nikeName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Man(){
        System.err.println("create Man");
    }
}

```

定义Config配置类

```java

@Configuration
//加载资源我呢见路径，可以同时声明多个@PropertySource
@PropertySource("classpath:person.properties")
public class Config5 {
    @Bean
    public Man man(){
        return  new Man();
    }
}
```

测试

```java
    @Test
    public void testApp5() {
        //创建ioc容器
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(Config5.class);
        System.out.println("ioc容器构建完毕");
        Man man = (Man) applicationContext.getBean("man");
        System.out.println(man);
    }
```

结果截图：

![1563851232024](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563851232024.png)

很清楚看到spring构架好Bean后自动按照@Value指定的值赋值。



### 第四模块 自动装配

Spring容器的DI（依赖注入）功能，能够实现注入bean中运行时所依赖的另一个bean。常用@Autowired注解来实现依赖自动装配。通常来讲，用@Autowired自动装配有以下几点：

1. 默认会优先按照类型去容器总找组件
2. 如果找到多个类型相同的bean声明再容器中，再根据**属性名称**(就是你声明变量的变量名称)作为组件id去容器中查找
3. @Quilifier("组件名称") 这个注解要配合@Autowired去使用，指定要自动装配哪个id的bean（在筛选出的所有同类型组件中查找）
4. 自动装配@Autowired一定要将属性赋值好，不然会报错。也就是自动装配一定要装配到对象。可以使用@Autowired(required=false)去标注该对象找不到的时候可以取null，但会有报错风险！
5. @Primary注解可以在自动装配的时候，设置默认首选的bean（"前提是没有Quilifier明确的指定要装配Bean的情况下"），在bean上面标注了@Primary注解后，@Autowired就会忽略默认根据变量名称匹配到的bean，而是使用@Primary标注的bean。

实战部分：

```java
//首先定义UserDao
@Repository("userDao")
public class UserDao {
    private String label ;

    @Override
    public String toString() {
        return "UserDao{" +
                "label='" + label + '\'' +
                '}';
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}

```

定义一个Config类，配置2个UserDao的bean （为了演示效果）

```java
@Configuration
@ComponentScan(value = {"springAnnotationAndSourceTest.dao","springAnnotationAndSourceTest.controller"})
public class Config6 {

    /**
     标注Id为userDao2为首选bean，意思是在没有Quilifier导入指定id的bean的时候，@Autowired      会直接注入这个bean
    */
    @Primary
    @Bean("userDao2")
    public UserDao userDao2(){
        UserDao userDao = new UserDao();
        userDao.setLabel("label2");
        return userDao;
    }
    
    @Bean("userDao1")
    public UserDao userDao1(){
        UserDao userDao = new UserDao();
        userDao.setLabel("label1");
        return userDao;
    }
}

```

定义一个UserController类，且依赖于userDao

```java


//其次定义UserController类
@Controller
public class UserController {

    //这里假设UserController会应用UserDao的对象，交给spring自动装配，并且用@Qualifier显示的指定要导入id为userDao1的bean
    @Qualifier("userDao1")
    @Autowired
    UserDao userDao1 ;

    public void getUserDaoInfo(){
        System.out.println(userDao1.getLabel());
    }
}
```

现在的情况是@Qualifier指定了要导入userDao1 ,而userDao2上又标注了@Primary注解，会导入哪个呢?测试以下：

```java
    @Test
    public void testApp6() {
        //创建ioc容器
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(Config6.class);
        String[] names = applicationContext.getBeanDefinitionNames();
        UserController userController = (UserController) applicationContext.getBean("userController");
        //这一句如果是label1就是userDao1，如果是label2就是userDao2
        userController.getUserDaoInfo();

    }
```

结果截图

![1563863688184](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563863688184.png)

发现返回的结果是userDao1的label属性值，现在我们可以把@Qulifier注解去掉，再测试

![1563863814486](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563863814486.png)

可以看到返回的结果是label2 也就是我们标注@Primary的userDao2的label属性值。

当然，如果把@Primary去掉，@Autowired导入的究竟是userDao1，还是userDao2呢？

![1563863917695](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563863917695.png)

显然是userDao1，@Autowired从筛选出的多个同类型结果会再根据属性名筛选，这里的属性名是userDao1 ，也就是UserController这一句

```java
    @Autowired
 //如果把userDao1改为userDao2就会导入userDao2    
UserDao userDao1 ;
```

注意：如果把userDao1改为userDao，bean中没有id为userDao喔！自然是匹配不到的！这个时候如果你想属性名和id名称不一致的情况还想导入指定id的bean就得使用@Quilifier进行注入！



**@Resource和@Inject来导入**

两者和Spring的@Autowired注解的区别：

​        @Resource: 可以和@Autowired一样实现自动装配功能，默认是按钮组件名称进行装配的，没有支持@Primary功能没有支持@Autowired(requred=false)的功能

​         @Inject： 需要导入javax.inject的包，和Autowired的功能一样，支持@Primary的功能，但是没有reuquired=false的功能。

**@Resource和@Inject注解是JSR250规范的，也就是不依赖Spring框架，是java支持的**

至于Spring为什么也支持@Resource和@Inject注解去自动装配bean，其实全都是AutowiredAnnotationBeanPostProcessor在起作用。（看源码）



**@Autowired的作用域及默认规则**

@Autowired不仅可以标注在定义的属性上，还可以标注在类的有参构造器上，方法参数上或者方法上。这里有些默认的规则，我们根据代码说明问题。

```java
//首先创建一个Car类
@Component
public class Car {
}



//其次创建Boss类，Boss拥有一辆car
@Component
public class Boss {
    private Car car;

    //标注在有参构造器上面
    @Autowired
    public Boss(Car car) {
        this.car = car;
        //打印出导入的car的内存地址
        System.out.println("boss的有参构造器："+car); 
    }

    public Car getCar() {
        return car;
    }

    public void setCar(Car car) {
        this.car = car;
    }
}
```

编写一个Config配置类扫描组件

```java
@Configuration
@ComponentScan
public class Config7 {
}

```

测试方法

```java
@Test
    public void testApp7() {
        //创建ioc容器
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(Config7.class);
        //拿到容器中car并打印出地址
        System.out.println(applicationContext.getBean("car"));
    }
```

结果截图

![1563866100193](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563866100193.png)

这里很明显看出boss的构造器导入的car内存地址和从spring容器中拿到的car的内存地址一致，说明是两个相同的对象，@Autowired会自动从容器中搜索car并自动导入。其实把@Autowired标注在setter方法上也是一样的效果，spring在给属性赋值的时候就会自动的搜索容器中的car再导入。

现在进阶一下，如果把上面Boss构造器中的@Autowired删除会怎么样呢？我们删除@Autowired再测试一次，结果如下：

![1563866375749](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563866375749.png)

依旧的，Spring还是会调用Boss的有参构造器，并从容器中搜索car自动注入，**如果只有一个有参构造器这个有参构造器的@Autowired可以省略！**这就是@Autowired的一些默认规则。

接下来有一个House类，也依赖于Car类

```java
//注意没有@Component注解
public class House {
   private Car car ;

   public House(Car car){
       System.out.println("House的有参构造:"+car);
   }

    public Car getCar() {
        return car;
    }

    public void setCar(Car car) {
        this.car = car;
    }

    @Override
    public String toString() {
        return "House{" +
                "car=" + car +
                '}';
    }
}

```

同样修改Config类

```java
@Configuration
@ComponentScan
public class Config7 {

    //这种情况下spring容器在创建House时，会自动的搜索容器里的car，将其自动注入进来
    @Bean
    public House house(Car car){
        House h = new House(car);
        return h ;
    }
}
```

运行上面的测试代码得到结果

![1563866800433](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563866800433.png)

可以发现Spring调用了House的有参构造器，并且注入的是Spring容器内部搜索到的car！故此我们又得到一个结论：@Bean+方法参数，方法参数会从Spring容器中获取！



**使用Aware相关组件，注入Spring底层的一些组件**

自定义组件如果想要获取Spring底层的一些组件（ApplicationContext,BeanFactory,......） 就要实现Spring框架提供的Aware接口的一些xxxAware（比如ApplicationContextAware） Spring容器会调用接口规定的方法来自动注入相关的组件（把Spring底层的组件注入到自定义的Bean中去）。

我们来举个例子解释这些说法

假设现在有一个AutoDefineUtil类，它想要获取applicationContext和使用一些Spirng底层的功能。

```java
//AutoDefineUtil

@Component
public class AutoDefineUtil implements ApplicationContextAware , EmbeddedValueResolverAware,  BeanNameAware , InitializingBean, DisposableBean {

    public AutoDefineUtil(){
        System.out.println("AutoDefineUtil构造器");
    }

    /**
     * ApplicationContextAware在Spring创建完AutoDefineUtil后自动注入applicationContext，本质使用了BeanPostProccessor
     * @param applicationContext
     * @throws BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        System.out.println("注入的applicationContext对象地址:"+applicationContext);
    }

    /**
     * EmbeddedValueResolverAware 用于解析字符串，比如字符串包含SPEL表达式#{}，以及资源获取和环境变量获取的表达式${}
     * @param resolver
     */
    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        String v=resolver.resolveStringValue("你好 #{29-1} 我是${os.name}");
        System.out.println("解析后的字符串:"+v);
    }

    /**
     * BeanNameAware的获取当前bean名字的方法
     * @param name
     */
    @Override
    public void setBeanName(String name) {
        System.out.println("当前bean的名字是:"+name);
    }

    /**
      自定义销毁方法
    */
    @Override
    public void destroy() throws Exception {
        System.out.println(this.getClass().getSimpleName()+"的摧毁方法");
    }

    /**
     自定义初始化方法
    */
    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println(this.getClass().getSimpleName()+"的自定义初始化方法");
    }
}

```

现在定义Config类加载AutoDefineUtil类进入spring容器

```java
@Configuration
@ComponentScan
public class Config8 {

}
```

测试一下，看看到底能不能获取到spring底层的组件以及执行spring底层组件的一些功能

```java
 @Test
    public void testApp8() {
        //创建ioc容器
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(Config8.class);
        System.out.println("测试用的applicationContext地址:"+applicationContext);
    }
```

结果

![1563869480886](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563869480886.png)

可以看出测试的applicationContext和AutoDefineUtil类中导入的内存地址一模一样，说明spring容器在内部将applicationContext注入到了AutodefineUtil的setApplicationContext(ApplicationContext applicationContext)方法中。

**我们继续看上面的结果生成的顺序** ，首先是调用了AutoDefineUtil的构造器，其次在AutoDefineUtil自定义初始化方法之前调用了实现的那几个Aware接口中的方法。是不是和BeanPostProcessor的顺序有点像？我们不妨自己定义一个BeanPostProcessor

```java
@Component
public class AutoDefineBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        System.out.println("postProcessBeforeInitialization"+beanName+"====》"+bean);
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        System.out.println(" postProcessAfterInitialization"+beanName+"====》"+bean);
        return bean;
    }
}

```

重新运行测试用例，观测结果

![1563869813070](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563869813070.png)

观测红线处的前面正好是Aware接口的调用方法结果！这里猜测Aware接口的这些拓展接口的底层是通过BeanPostProcessor进行处理的，在bean的自定义方法前和自定义的后置处理器方法前执行完毕。为了验证这一说法，我们就在AutoDefineUtil类的setApplicationContext处打断点，观测调用栈。

![1563870485055](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563870485055.png)

debug查看整个调用栈

![1563870612765](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563870612765.png)

重点关注在蓝色标记initializeBean方法，点进去发现这个方法中的几行代码

```java
Object wrappedBean = bean;
		if (mbd == null || !mbd.isSynthetic()) {
			wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
		}
```

紧接着又进入到了applyBeanPostProcessorsBeforeInitialization，意思是在bean调用自定义初始化方法前做的操作，点进去看看

```java
@Override
	public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		for (BeanPostProcessor beanProcessor : getBeanPostProcessors()) {
			result = beanProcessor.postProcessBeforeInitialization(result, beanName);
			if (result == null) {
				return result;
			}
		}
		return result;
	}
```

这个方法内部又调用了postProcessBeforeInitialization方法，注意getBeanPostProcessors()方法，我们用计算器计算一下

![1563871002072](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563871002072.png)

发现有我们想要的结果！紧接着这个方法遍历所有的BeanPostProcessor然后调用beanProcessor.postProcessBeforeInitialization(result, beanName);，我们跟着点进去查看

```java
@Override
	public Object postProcessBeforeInitialization(final Object bean, String beanName) throws BeansException {
		AccessControlContext acc = null;
        /**
          这里if整体就开始判断这个bean是否是xxxAware接口的实现类型了！
        */
		if (System.getSecurityManager() != null &&
				(bean instanceof EnvironmentAware || bean instanceof EmbeddedValueResolverAware ||
						bean instanceof ResourceLoaderAware || bean instanceof ApplicationEventPublisherAware ||
						bean instanceof MessageSourceAware || bean instanceof ApplicationContextAware)) {
			acc = this.applicationContext.getBeanFactory().getAccessControlContext();
		}

        //这里进行权限校验
		if (acc != null) {
			AccessController.doPrivileged(new PrivilegedAction<Object>() {
				@Override
				public Object run() {
					invokeAwareInterfaces(bean);
					return null;
				}
			}, acc);
		}
		else {
            //重点在这！！！
			invokeAwareInterfaces(bean);
		}

		return bean;
	}
```

invokeAwareInterfaces(bean); 这里是重点，将bean传进去并且调用xxxAware接口的方法。点进去继续看

```java
private void invokeAwareInterfaces(Object bean) {
		if (bean instanceof Aware) {
			if (bean instanceof EnvironmentAware) {
				((EnvironmentAware) bean).setEnvironment(this.applicationContext.getEnvironment());
			}
			if (bean instanceof EmbeddedValueResolverAware) {
				((EmbeddedValueResolverAware) bean).setEmbeddedValueResolver(this.embeddedValueResolver);
			}
			if (bean instanceof ResourceLoaderAware) {
				((ResourceLoaderAware) bean).setResourceLoader(this.applicationContext);
			}
			if (bean instanceof ApplicationEventPublisherAware) {
				((ApplicationEventPublisherAware) bean).setApplicationEventPublisher(this.applicationContext);
			}
			if (bean instanceof MessageSourceAware) {
				((MessageSourceAware) bean).setMessageSource(this.applicationContext);
			}
			if (bean instanceof ApplicationContextAware) {
				((ApplicationContextAware) bean).setApplicationContext(this.applicationContext);
			}
		}
	}
```

终于走到最后一步了，这个方法将现在的bean对象依次的判断是哪个Aware接口的实例，比如我们定义的AutoDefineUtil类实现了ApplicationContextAware , EmbeddedValueResolverAware,  BeanNameAware 这三个接口。就会ui一次的扫描并且强制转为接口对应的类型，然后再调用接口的方法，把Spring底层的组件传输进去！

这里也说明了，xxxAware接口时通过BeanPostProcessor来实现调用赋值的功能的！

**对Spring自动注入的方法总结**

总共有四种实现自动注入的方法：

1. 使用@Autowired结合@Quilifier及@Primary的方式实现自动注入功能。
2. 使用@Resource和@Inject注解实现自动注入（java规范里的东西）
3. 使用@Autowired在类的构造器/方法/参数上进行自动注入，spring默认对只有一个有参数的构造器实现自动注入参数，不需要标注@Autowired注解
4. 使用Aware接口的扩展接口，将Spring底层的组件注入。



### 第五模块 @Profile讲解

@Profile: Spring为我们提供可以根据当前环境，动态的激活和切换一系列组件的功能。比如我们日常工作中有开发环境，有生产环境，有测试环境。那么这几个环境都会用到不同的数据源接口，想根据不同的环境动态的转换数据源接口就可以用到@Profile注解去实现。

现在我们来实现一下案例，前提导入c3P0的依赖和mysql-connector的依赖

```java
 <properties>
  <c3p0.version>0.9.1.2</c3p0.version>
  <mysql.connector.version>5.1.44</mysql.connector.version>
</properties>

    <!--    数据源-->
    <dependency>
      <groupId>c3p0</groupId>
      <artifactId>c3p0</artifactId>
      <version>${c3p0.version}</version>
    </dependency>
    
    <!-- mysql-connector -->
    <dependency>
      <groupId>mysql</groupId>
      <artifactId>mysql-connector-java</artifactId>
      <version>${mysql.connector.version}</version>
    </dependency>
```

这里给出数据源的配置文件

```properties
db.user=root
db.password=xxxxx
db.driverClass=com.mysql.jdbc.Driver
db.basePath=jdbc:mysql://localhost:3306/
```

这里时配置类，其中定义了3个环境下的数据源以及一个开发环境下才管用的bean

```java
/**
 * @PropertySource导入数据源配置文件
 */
@Configuration
@PropertySource("classpath:datasource.properties")
public class ConfigProfile implements EmbeddedValueResolverAware {
    //数据库密码
    @Value("${db.password}")
    String password;
    //数据库路径的基路径
    String basePath;
    //数据库驱动
    String driverClass;

    /**
     * 开发模式下会生成red对象
     * @return
     */
    @Bean("red")
    @Profile("dev")
    public Red red(){
        return new Red();
    }

    /**
     * 开发环境的数据源
     * @param user
     * @return
     * @throws PropertyVetoException
     */
    @Bean("dataSourceDev")
    @Profile("dev")
    public DataSource dataSourceDev(@Value("${db.user}")String user) throws PropertyVetoException {
        ComboPooledDataSource comboPooledDataSource = new ComboPooledDataSource();
        comboPooledDataSource.setUser(user);
        comboPooledDataSource.setPassword(password);
        comboPooledDataSource.setDriverClass(driverClass);
        comboPooledDataSource.setJdbcUrl(basePath+"dev");
        return comboPooledDataSource;
    }

    /**
     * 测试环境的数据源
     * @param user
     * @return
     * @throws PropertyVetoException
     */
    @Bean("dataSourceTest")
    @Profile("test")
    public DataSource dataSourceTest(@Value("${db.user}")String user) throws PropertyVetoException {
        ComboPooledDataSource comboPooledDataSource = new ComboPooledDataSource();
        comboPooledDataSource.setUser(user);
        comboPooledDataSource.setPassword(password);
        comboPooledDataSource.setDriverClass(driverClass);
        comboPooledDataSource.setJdbcUrl(basePath+"test");
        return comboPooledDataSource;
    }

    /**
     * 生产环境的数据源
     * @param user
     * @return
     * @throws PropertyVetoException
     */
    @Bean("dataSourcePro")
    @Profile("pro")
    public DataSource dataSourcePro(@Value("${db.user}")String user) throws PropertyVetoException {
        ComboPooledDataSource comboPooledDataSource = new ComboPooledDataSource();
        comboPooledDataSource.setUser(user);
        comboPooledDataSource.setPassword(password);
        comboPooledDataSource.setDriverClass(driverClass);
        comboPooledDataSource.setJdbcUrl(basePath+"pro");
        return comboPooledDataSource;
    }

    /**
     * 获取String值的解析器，解析#{} ${}这些特殊的标签
     * @param resolver
     */
    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        String p = resolver.resolveStringValue("${db.basePath}");
        String c = resolver.resolveStringValue("${db.driverClass}");
        basePath = p;
        driverClass = c;
    }
}
```

现在我们测试下效果

```java
 @Test
    public void testConfigProfile() {
        //创建ioc容器
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(ConfigProfile.class);
        //打印所有bean名字
        for(String name : applicationContext.getBeanDefinitionNames()){
            System.out.println(name);
        }
    }
```

结果截图

![1563875416034](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563875416034.png)

很显然只有配置类加载进spring容器，其他的数据源呢？？ 当然不会在容器中，我们还没有设置激活的数据源类型，注意如果没有设置默认的类型就是default 也就是说@Profile("default")生效。如何设置数据源？主要有两种办法：

1. 使用命令行参数（设置VM options）在VM options后追加-Dspring.profiles.active=dev,test 也就是激活dev环境和test环境

   ![1563875571776](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563875571776.png)

2. 使用代码的方式设置激活状态

   ```java
     @Test
       public void testConfigProfile() {
           //创建ioc容器，注意这里不要传参数！有参构造的话会直接执行到                       applicationContext.refresh();之后在调用  applicationContext.getEnvironment().setActiveProfiles("test,dev");会不管用！
           AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
           //设置测试环境和开发环境管用
           applicationContext.getEnvironment().setActiveProfiles("dev","test");
           //注册配置类
           applicationContext.register(ConfigProfile.class);
           //刷新加载bean
           applicationContext.refresh();
           //遍历所有bean
           for(String name : applicationContext.getBeanDefinitionNames()){
               System.out.println(name);
           }
           //得到开发数据源
          ComboPooledDataSource dataSourceDev = (ComboPooledDataSource) applicationContext.getBean("dataSourceDev");
            //打印数据源信息            
           System.err.println(dataSourceDev.getUser()+":"+dataSourceDev.getPassword()+":"+dataSourceDev.getJdbcUrl()+":"+dataSourceDev.getDriverClass());
       }
   ```

   **注意上面代码去激活profile的时候，不要直接调用AnnotationConfigApplicationContext的有参构造**我们看下其中的源码：

   ```java
   //AnnotationConfigApplicationContext的部分源码
   /**
   	 * Create a new AnnotationConfigApplicationContext, deriving bean definitions
   	 * from the given annotated classes and automatically refreshing the context.
   	 * @param annotatedClasses one or more annotated classes,
   	 * e.g. {@link Configuration @Configuration} classes
   	 */
   	public AnnotationConfigApplicationContext(Class<?>... annotatedClasses) {
   		this();
   		register(annotatedClasses);
   		refresh();
   	}
   ```

   主要的意思是如果调用AnnotationConfigApplicationContext的有参构造方法，则会自动的刷新上下文（调用refresh（））这里加载完毕后你再去设置环境变量是不管用的！我们应该调用无参的AnnotationConfigApplicationContext（）,然后再设置环境变量，再依照它的执行顺序去一次调用初始化容器即可。

上面两个方法都可以得到相同的运行结果

![1563879437957](C:\Users\86137\AppData\Roaming\Typora\typora-user-images\1563879437957.png)

这里就实现了切换环境从而改变加载不同的bean。注意当@Profile注解在类上面标注的时候，作用范围就是整个类了，只有当前激活的环境和类所标注的环境一致，这个类才会被加载进ioc容器。否则不会加载进ioc容器。如果不指定@Profile注解，默认就是加载进ioc容器。

