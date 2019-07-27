# Spring---Bean的循环依赖问题

我们先研究ApplicationContext这个类，去看下里面是怎么解决初始化bean循环依赖的问题，在此之前先了解下什么是bean的循环依赖问题

* 假设有两个Bean，InstanceA 在构造的时候需要InstanceB的对象做为构造方法参数去构造A对象。而InstancceB同时也要InstanceA对象作为参数去构造InstanceB对象。这种情况下就会出现所谓的循环依赖的问题。具体代码体现如下

```java
package bean.circleDependsProblem;
import org.springframework.stereotype.Component;

/**
 * 为了说明spring的循环依赖问题，InstanceA里需要依赖InstanceB
 * 而InstanceB里需要依赖InstanceA,并在构造器时需要注入依赖
 */

@Component("instanceA")
public class InstanceA {

    private InstanceB instanceB;

    public InstanceA(InstanceB instanceB){
        this.instanceB=instanceB;
    }


    public InstanceB getInstanceB() {
        return instanceB;
    }

    public void setInstanceB(InstanceB instanceB) {
        this.instanceB = instanceB;
    }
}

```

```java

/**
 * 为了说明spring的循环依赖问题，InstanceA里需要依赖InstanceB
 * 而InstanceB里需要依赖InstanceA,并在构造器时需要注入依赖
 */
@Component("instanceB")
public class InstanceB {
    private InstanceA instanceA;

    public InstanceB(InstanceA instanceA){
        this.instanceA=instanceA;
    }

    public InstanceA getInstanceA() {
        return instanceA;
    }

    public void setInstanceA(InstanceA instanceA) {
        this.instanceA = instanceA;
    }
}
```

在有了这些bean的情况下，通过一个配置类去扫描Scan就可以把这两个bean加载进spring容器里

如下拿取从spring容器中声明的bean  

```java
  @Test
  public void testApp(){
      ApplicationContext applicationContext = new 		  AnnotationConfigApplicationContext(Config.class);
      //从容器中拿到InstanceA对象
      InstanceA instanceA = applicationContext.getBean(InstanceA.class);
 }
```

通过测试可以发现报如下异常，意思是instanceA无法在构建的时候满足依赖循环，也就是说instanceB正在构建中不能得到B的实例。

```markdown
org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'instanceA' defined in file , **Error creating bean with name 'instanceA': Requested bean is currently in creation: Is there an unresolvable circular reference?**

```

究竟是怎么抛出的异常，怎么判断的呢？ 下面开始解析源码，主要进入的是ApplicationContext中。先给出生成bean的主要步骤大纲：

1) 点进去ApplicationContext中