### ThreadLocal如何保证线程安全----简介

> l  Spring通过各种模板类降低了开发者使用各种持久技术的难度。
>
> l  这些模板类都是线程安全的。
>
> l  模板类需要绑定数据连接或者会话的资源。
>
> l  这些资源本身是非线程安全的。
>
> l  虽然模板类通过资源池获取连接或者会话，
>
> l  但是资源池解决的是数据连接或者资源的缓存问题，
>
> l  而不是线程安全问题。
>
> l  按照惯例，采用synchronized进行线程同步。
>
> l  但是该线程同步机制解决具体问题时，开发难度大、降低并发性、影响系统性能。
>
> l  所以，模板类并未采用线程同步机制。
>
> l  那么，模板类究竟采用什么方式保证线程安全的呢？
>
> l  答案：ThreadLocal！



***ThreadLocal是什么？***

 

ThreadLocal，顾名思义，它不是一个线程，而是线程的一个本地化对象。多线程程序使用ThreadLocal维护变量时，每一个线程将拿到该变量的一个副本，从而，每个线程对各自变量的副本的更改都不会影响到其他线程。



***一个ThreadLocal实例***



***![img](https://mmbiz.qpic.cn/mmbiz_png/8GnpVPFXldvDKSlNAaoF23G7bzwxsR9fVPFxPBKsvawNdDBCsN9kZFshAyiaZQJv7h8SbbBSnk2UgLY2fS78QiaQ/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)***

 

![img](https://mmbiz.qpic.cn/mmbiz_png/8GnpVPFXldvDKSlNAaoF23G7bzwxsR9ftUmYoOWFbYY2OrJBQMbV3MJsOk6fqqyjgC4CsbTpgP47pATCJUT0oA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

 

上例很简单，三个线程都拿到Integer对象的副本，该Integer对象的初始化值设置为0，然后各自修改，互不影响。



除了set、get、initialValue之外，ThreadLocal还有一个方法：remove(),该方法将当前变量副本从该线程中删除，减少内存的占用。



*与Thread同步机制的比较*

 

在同步机制中，通过对象的锁机制保证同一时间只有一个线程访问变量，该变量是多个线程共享的，那么，每个线程在什么时候可以对变量读写，什么时候要对该对象加锁，什么时候释放对象锁等，都要准确判断，逻辑复杂，编写难度大。

 

ThreadLoacl为每一个线程提供一个变量的副本，隔离了多线程访问数据的冲突。ThreadLocal提供了线程安全的对象封装，在编写多线程代码时，可以把不安全的变量封装进ThreadLocal。

 

总之，对多线程共享的问题，同步机制采用了”**以时间换空间，访问串行化，对象共享化**”。而ThreadLocal则是“**以空间换时间，访问并行化，对象独享化****”**。前者只提供一份变量，让不同的线程排队访问，而后者为每一个线程都提供了一份变量，因此可以同时访问而互不影响。



***Spring与ThreadLocal***



有状态就是有数据存储功能。有状态对象(Stateful Bean)，就是有实例变量的对象，可以保存数据，是非线程安全的。在不同方法调用间不保留任何状态。



![img](https://mmbiz.qpic.cn/mmbiz_png/8GnpVPFXldvDKSlNAaoF23G7bzwxsR9fbUdhbic6H9DZKiclyKaTV2RfKxnqcrWQib6iculXmxqLgO2TsCnv63UA4w/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

 

![img](https://mmbiz.qpic.cn/mmbiz_png/8GnpVPFXldvDKSlNAaoF23G7bzwxsR9fuCNdEZRmxnnjj66xZ2gM7I9dDfu7RJcymuUgKZ7e0uKZoNtRu9GuHg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



**无状态**就是一次操作，不能保存数据。无状态对象(Stateless Bean)，就是没有实例变量的对象.不能保存数据，是不变类，是线程安全的。



![img](https://mmbiz.qpic.cn/mmbiz_png/8GnpVPFXldvDKSlNAaoF23G7bzwxsR9fDCl17fJJTtSy6iaETNMmRgoJpjZu2LNibQxq5N3Yw56hAxU2AffeJobA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

 

一般情况下，只有无状态bean才可以在多线程环境下共享（既然没有状态，不能保存数据，随便共享啦）



在spring中，绝大部分Bean都可以声明为singleton作用域。（如果在<bean>中指定Bean的作用范围是scopt="prototype",那么系统将bean返回给调用者，spring就不管了（如果两个实例调用的话，每一次调用都要重新初始化，一个实例的修改不会影响另一个实例的值。如果指定Bean的作用范围是scope="singleton"，则把bean放到缓冲池中，并将bean的引用返回给调用者。这个时候，如果两个实例调用的话，因为它们用的是同一个引用，任何一方的修改都会影响到另一方。））



正因为Spring对一些Bean(RequestContextholder、TransactionSynchronizationManager、LocaleContextHolder等)中非线程安全的”状态性对象”采用ThreadLocal封装，让它们成为线程安全的”状态性对象”，因此有状态的bean就能够以singleton方式在多线程中正常工作了。



***Spring对有状态bean的改造思路***



非线程安全：



![img](https://mmbiz.qpic.cn/mmbiz_png/8GnpVPFXldvDKSlNAaoF23G7bzwxsR9fUaXH3w7rLYfWepUiaJOv0KE0wFysXxCjDAIJHQUIIo1iboDicZGfNcdNg/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

  

由于第8行的conn是非线程安全的成员变量，

因此addTopic()方法也是非线程安全的，

每次使用时都必须新创建一个TopicDao实例(非singleton)。



对非线程安全的conn进行改造：



![img](https://mmbiz.qpic.cn/mmbiz_png/8GnpVPFXldvDKSlNAaoF23G7bzwxsR9foTOa5ppzMrk05RZoia8KPsNcNswmFeMiaM3ez2StuDGVicOicX7qBVeXCA/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)



上例仅为了简单说明原理，并不做深究，例子粗糙，并不能在实际环境中使用，还有很多要考虑的其他问题。