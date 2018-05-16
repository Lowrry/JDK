/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * An {@link ExecutorService} that executes each submitted task using
 * one of possibly several pooled threads, normally configured
 * using {@link Executors} factory methods.
 *
 * <p>Thread pools address two different problems: they usually         //线程池解决两个问题
 * provide improved performance when executing large numbers of         //在执行大量任务时提供更好的表现
 * asynchronous tasks, due to reduced per-task invocation overhead,     //因为减少了每个任务调用的开销
 * and they provide a means of bounding and managing the resources,     //可以限制和管理资源
 * including threads, consumed when executing a collection of tasks.    //包括线程,执行任务时的消耗
 * Each {@code ThreadPoolExecutor} also maintains some basic            //每个ThreadPoolExecutor还维护了一些基础的统计,比如完成的任务数
 * statistics, such as the number of completed tasks.
 *
 * <p>To be useful across a wide range of contexts, this class          //为了提供更广泛的场景使用
 * provides many adjustable parameters and extensibility                //类提供了很多可配置和可扩展的点
 * hooks. However, programmers are urged to use the more convenient     //建议使用Executors工厂方法创建线程池
 * {@link Executors} factory methods {@link
 * Executors#newCachedThreadPool} (unbounded thread pool, with
 * automatic thread reclamation), {@link Executors#newFixedThreadPool}
 * (fixed size thread pool) and {@link
 * Executors#newSingleThreadExecutor} (single background thread), that
 * preconfigure settings for the most common usage
 * scenarios. Otherwise, use the following guide when manually
 * configuring and tuning this class:
 *
 * <dl>
 *
 * <dt>Core and maximum pool sizes</dt>
 *
 * <dd>A {@code ThreadPoolExecutor} will automatically adjust the
 * pool size (see {@link #getPoolSize})
 * according to the bounds set by
 * corePoolSize (see {@link #getCorePoolSize}) and
 * maximumPoolSize (see {@link #getMaximumPoolSize}).
 *
 * When a new task is submitted in method {@link #execute}, and fewer
 * than corePoolSize threads are running, a new thread is created to
 * handle the request, even if other worker threads are idle.  If
 * there are more than corePoolSize but less than maximumPoolSize
 * threads running, a new thread will be created only if the queue is
 * full.  By setting corePoolSize and maximumPoolSize the same, you
 * create a fixed-size thread pool. By setting maximumPoolSize to an
 * essentially unbounded value such as {@code Integer.MAX_VALUE}, you
 * allow the pool to accommodate an arbitrary number of concurrent
 * tasks. Most typically, core and maximum pool sizes are set only
 * upon construction, but they may also be changed dynamically using
 * {@link #setCorePoolSize} and {@link #setMaximumPoolSize}. </dd>  //setCorePoolSize,setMaximumPoolSize可以动态的修改线程池属性.
 *
 * <dt>On-demand construction</dt>
 *
 * <dd> By default, even core threads are initially created and         //默认核心线程都是在任务到达时初始化创建
 * started only when new tasks arrive, but this can be overridden
 * dynamically using method {@link #prestartCoreThread} or {@link       //可以用prestartCoreThread,prestartAllCoreThreads预先启动线程
 * #prestartAllCoreThreads}.  You probably want to prestart threads if  //什么样的场景会需要预先启动线程呢?
 * you construct the pool with a non-empty queue. </dd>                 //作者给出了这样一个场景: 构造的任务队列不是空的,也就是线程池构造好了就得执行任务了.
 *
 * <dt>Creating new threads</dt>
 *
 * <dd>New threads are created using a {@link ThreadFactory}.  If not   //新线程是使用ThreadFactory创建的
 * otherwise specified, a {@link Executors#defaultThreadFactory} is     //默认使用Executors#defaultThreadFactory
 * used, that creates threads to all be in the same {@link              //默认创建的线程都在同一个ThreadGroup,都是同一个优先级,都是非守护线程
 * ThreadGroup} and with the same {@code NORM_PRIORITY} priority and    //ThreadGroup是用来将线程进行分批管理的
 * non-daemon status. By supplying a different ThreadFactory, you can
 * alter the thread's name, thread group, priority, daemon status,      //可以自定义ThreadFactory,来订制线程的名字,group,优先级和是否守护线程.
 * etc. If a {@code ThreadFactory} fails to create a thread when asked  //如果ThreadFactory创建线程失败,newThread方法会返回null
 * by returning null from {@code newThread}, the executor will          //executor会继续
 * continue, but might not be able to execute any tasks. Threads        //但是不能执行任何任务
 * should possess the "modifyThread" {@code RuntimePermission}. If      //线程应该调用 RuntimePermission (modifyThread)
 * worker threads or other threads using the pool do not possess this   //如果工作线程或者其他线程 使用当前这个线程池没有权限
 * permission, service may be degraded: configuration changes may not   //服务可能会被降级
 * take effect in a timely manner, and a shutdown pool may remain in a  //配置变化可能不会及时生效
 * state in which termination is possible but not completed.</dd>       //关闭的线程池保持在一个状态: 可能终止了,但是没有完成
 *
 * <dt>Keep-alive times</dt>
 *
 * <dd>If the pool currently has more than corePoolSize threads,
 * excess threads will be terminated if they have been idle for more
 * than the keepAliveTime (see {@link #getKeepAliveTime}). This
 * provides a means of reducing resource consumption when the pool is   //keepAliveTime可以用来在线程没有被使用时,降低资源的消耗 (线程本身不会空轮询,但是服务器的线程都是有限的)
 * not being actively used. If the pool becomes more active later, new  //如果线程池过会又有任务了,会创建新线程
 * threads will be constructed. This parameter can also be changed
 * dynamically using method {@link #setKeepAliveTime}. Using a value    //setKeepAliveTime可以动态修改参数
 * of {@code Long.MAX_VALUE} {@link TimeUnit#NANOSECONDS} effectively   //可以使用Long.MAX_VALUE有效的禁止空闲线程关闭
 * disables idle threads from ever terminating prior to shut down. By
 * default, the keep-alive policy applies only when there are more      //默认只有在存活线程大于核心线程时才会使用keep-alive策略
 * than corePoolSizeThreads. But method {@link
 * #allowCoreThreadTimeOut(boolean)} can be used to apply this          //但是allowCoreThreadTimeOut也可以用来对核心线程设置超时策略
 * time-out policy to core threads as well, so long as the              //keepAliveTime值必须要为非0
 * keepAliveTime value is non-zero. </dd>
 *
 * <dt>Queuing</dt>
 *
 * <dd>Any {@link BlockingQueue} may be used to transfer and hold       //任何一个阻塞队列都可以用来传输和暂存提交的任务
 * submitted tasks.  The use of this queue interacts with pool sizing:  //队列的使用和线程池的大小之间的关系
 *
 * <ul>
 *
 * <li> If fewer than corePoolSize threads are running, the Executor    //如果小于核心线程,偏向于创建新线程来处理,而不是让任务进去队列等待
 * always prefers adding a new thread
 * rather than queuing.</li>
 *
 * <li> If corePoolSize or more threads are running, the Executor       //如果大于等于核心线程数在运行,偏向于将任务保存到队列中
 * always prefers queuing a request rather than adding a new
 * thread.</li>
 *
 * <li> If a request cannot be queued, a new thread is created unless   //如果队列不能存放,线程数也已经达到最大线程数,任务会被抛弃.
 * this would exceed maximumPoolSize, in which case, the task will be
 * rejected.</li>
 *
 * </ul>
 *
 * There are three general strategies for queuing:                      //有三种排队策略
 * <ol>
 *
 * <li> <em> Direct handoffs.</em> A good default choice for a work     // 1. Direct handoffs: 直接手递手, 一个很好的选择就是SynchronousQueue
 * queue is a {@link SynchronousQueue} that hands off tasks to threads
 * without otherwise holding them. Here, an attempt to queue a task
 * will fail if no threads are immediately available to run it, so a    //如果没有线程能立马执行它,任务就会失败
 * new thread will be constructed. This policy avoids lockups when      //所以需要创建一个新的线程
 * handling sets of requests that might have internal dependencies.     //这个策略在处理多个内部相互依赖的请求任务时,避免了锁
 * Direct handoffs generally require unbounded maximumPoolSizes to      //直接手递手,一般需要没有最大线程数限制的,避免拒绝新的请求任务
 * avoid rejection of new submitted tasks. This in turn admits the      //这个反过来允许这样的可能性: 当任务继续执行时
 * possibility of unbounded thread growth when commands continue to     //到达平均速度比处理速度要快
 * arrive on average faster than they can be processed.  </li>
 *
 * <li><em> Unbounded queues.</em> Using an unbounded queue (for        // 2. 无界队列
 * example a {@link LinkedBlockingQueue} without a predefined           // 例如LinkedBlockingQueue (没有指定容量)
 * capacity) will cause new tasks to wait in the queue when all         //当所有的核心线程都被占用了,新来的任务会在队列中等待
 * corePoolSize threads are busy. Thus, no more than corePoolSize       //因为队列没有上限,所以就不会创建额外的线程来处理
 * threads will ever be created. (And the value of the maximumPoolSize
 * therefore doesn't have any effect.)  This may be appropriate when    //如果每个任务都是彼此独立的,那么任务就不会相互影响,这种情况就是合适的
 * each task is completely independent of others, so tasks cannot       //例如在web服务器,所有http请求都是无状态的,各个请求都是相互不影响的,所以适合用这种线程;
 * affect each others execution; for example, in a web page server.     //(web服务器资源不是无限的,所以需要设置线程的上限)
 * While this style of queuing can be useful in smoothing out           //这种形式的队列,在削峰处理很有用 (就是一个突发的请求高峰)
 * transient bursts of requests, it admits the possibility of           //它允许这样一种可能性: 就是任务队列的无限增长
 * unbounded work queue growth when commands continue to arrive on      //当任务到达平均速度大于处理速度时
 * average faster than they can be processed.  </li>
 *
 * <li><em>Bounded queues.</em> A bounded queue (for example, an        // 3. 有界的队列
 * {@link ArrayBlockingQueue}) helps prevent resource exhaustion when   // 例如ArrayBlockingQueue
 * used with finite maximumPoolSizes, but can be more difficult to      //有限的maximumPoolSizes可以避免资源耗尽,但是更难以控制
 * tune and control.  Queue sizes and maximum pool sizes may be traded
 * off for each other: Using large queues and small pools minimizes     //使用大队列和小线程池,可以减低cpu消耗 (线程是消耗cpu资源的),系统资源,上下文切换损耗
 * CPU usage, OS resources, and context-switching overhead, but can
 * lead to artificially low throughput.  If tasks frequently block (for //但会人为导致低吞吐量
 * example if they are I/O bound), a system may be able to schedule     //如果任务频繁阻塞,例如i/o操作阻塞
 * time for more threads than you otherwise allow. Use of small queues  //系统可能能比你设置的参数,执行更多的线程
 * generally requires larger pool sizes, which keeps CPUs busier but    //使用小队列一般来说需要更大的线程池,充分利用cpu
 * may encounter unacceptable scheduling overhead, which also           //但是线程多了,会导致无法接受的调度开销,这个也会降低吞吐量
 * decreases throughput.  </li>                                         //这样看来队列的大小和线程数设置还是需要综合考虑的
 *
 * </ol>
 *
 * </dd>
 *
 * <dt>Rejected tasks</dt>
 *
 * <dd> New tasks submitted in method {@link #execute} will be
 * <em>rejected</em> when the Executor has been shut down, and also     //如果线程池已经关闭,新来的任务会被拒绝
 * when the Executor uses finite bounds for both maximum threads and
 * work queue capacity, and is saturated.  In either case, the {@code
 * execute} method invokes the {@link
 * RejectedExecutionHandler#rejectedExecution} method of its {@link
 * RejectedExecutionHandler}.  Four predefined handler policies are
 * provided:
 *
 * <ol>
 *
 * <li> In the default {@link ThreadPoolExecutor.AbortPolicy}, the      ///默认拒绝策略:ThreadPoolExecutor.AbortPolicy
 * handler throws a runtime {@link RejectedExecutionException} upon     //会抛异常:RejectedExecutionException
 * rejection. </li>
 *
 * <li> In {@link ThreadPoolExecutor.CallerRunsPolicy}, the thread      //ThreadPoolExecutor.CallerRunsPolicy策略是让提交任务的线程自己执行任务
 * that invokes {@code execute} itself runs the task. This provides a
 * simple feedback control mechanism that will slow down the rate that  //这提供了一个简单的反馈控制体系,会导致提交任务的线程提交任务慢一点
 * new tasks are submitted. </li>                                       //(个人感觉有个好处就是永远不会丢失任务,最多让提交任务的客户端缓慢些,就是对客户端限流了)
 *
 * <li> In {@link ThreadPoolExecutor.DiscardPolicy}, a task that        //ThreadPoolExecutor.DiscardPolicy直接抛弃,不抛出异常
 * cannot be executed is simply dropped.  </li>
 *
 * <li>In {@link ThreadPoolExecutor.DiscardOldestPolicy}, if the        //DiscardOldestPolicy
 * executor is not shut down, the task at the head of the work queue    //等待队列中的head任务会被丢弃
 * is dropped, and then execution is retried (which can fail again,     //然后重试提交任务, 这样不断重复上面的步骤.
 * causing this to be repeated.) </li>
 *
 * </ol>
 *
 * It is possible to define and use other kinds of {@link               //可以自定义其他的RejectedExecutionHandler (线程池确实设计的很精巧,很多的扩展点!)
 * RejectedExecutionHandler} classes. Doing so requires some care       //这需要特别注意:
 * especially when policies are designed to work only under particular  //自定义的策略是用来处理特定容量的队列或者入队策略
 * capacity or queuing policies. </dd>
 *
 * <dt>Hook methods</dt>                                                //钩子方法,也是扩展点
 *
 * <dd>This class provides {@code protected} overridable {@link         //本类提供了可重写的方法:
 * #beforeExecute} and {@link #afterExecute} methods that are called    //beforeExecute 和 afterExecute
 * before and after execution of each task.  These can be used to       //在每个任务的执行前和执行后会被调用
 * manipulate the execution environment; for example, reinitializing    //可以用来操控执行环境
 * ThreadLocals, gathering statistics, or adding log        //例如重新初始化ThreadLocal变量(这个在线程池中做确实挺好的,就不用每次都在方法里面做了!!), 收集和统计功能, 记录日志功能
 * entries. Additionally, method {@link #terminated} can be overridden  //terminated方法可以重写,
 * to perform any special processing that needs to be done once the     //用来在线程池完全关闭后, 执行特定的处理 (这个作用也蛮大的)
 * Executor has fully terminated.
 *
 * <p>If hook or callback methods throw exceptions, internal worker     //如果钩子方法或者回调方法抛异常
 * threads may in turn fail and abruptly terminate.</dd>                //内部工作线程可能会失败,并突然关闭
 *
 * <dt>Queue maintenance</dt>                                           //队列维护
 *
 * <dd> Method {@link #getQueue} allows access to the work queue for    //getQueue用于监控和断点测试的
 * purposes of monitoring and debugging.  Use of this method for any    //不建议用来做其他目的
 * other purpose is strongly discouraged.  Two supplied methods,
 * {@link #remove} and {@link #purge} are available to assist in        //remove(单个删除)和purge(清空)可以用来
 * storage reclamation when large numbers of queued tasks become        //取消队列中大量任务时
 * cancelled.</dd>
 *
 * <dt>Finalization</dt>
 *
 * <dd> A pool that is no longer referenced in a program <em>AND</em>   //线程池如果没有引用指向它, 并且没有剩余的线程是会被自动关闭, 这是怎么实现的呢??
 * has no remaining threads will be {@code shutdown} automatically. If
 * you would like to ensure that unreferenced pools are reclaimed even  //如果你希望保证没有被引用的线程池,在用户忘了调用shutdown方法时,也保证被GC回收
 * if users forget to call {@link #shutdown}, then you must arrange
 * that unused threads eventually die, by setting appropriate           //你必须保证没有用的线程最终会死掉
 * keep-alive times, using a lower bound of zero core threads and/or    //要设置合适的存活时间, 设置核心线程为0 或者 设置allowCoreThreadTimeOut
 * setting {@link #allowCoreThreadTimeOut(boolean)}.  </dd>             //允许核心线程也超时死亡
 *
 * </dl>
 *
 * <p> <b>Extension example</b>. Most extensions of this class      //对线程池进行扩展的例子
 * override one or more of the protected hook methods. For example, //(思考:线程池的扩展就是在线程池或者线程的生命周期中,增加一些自定义的步骤)
 * here is a subclass that adds a simple pause/resume feature:      //扩展成一个可以暂停任务的线程池.
 *
 *  <pre> {@code
 * class PausableThreadPoolExecutor extends ThreadPoolExecutor {
 *   private boolean isPaused;
 *   private ReentrantLock pauseLock = new ReentrantLock();
 *   private Condition unpaused = pauseLock.newCondition();
 *
 *   public PausableThreadPoolExecutor(...) { super(...); }
 *
 *   protected void beforeExecute(Thread t, Runnable r) {
 *     super.beforeExecute(t, r);
 *     pauseLock.lock();
 *     try {
 *       while (isPaused) unpaused.await();
 *     } catch (InterruptedException ie) {
 *       t.interrupt();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void pause() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = true;
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 *
 *   public void resume() {
 *     pauseLock.lock();
 *     try {
 *       isPaused = false;
 *       unpaused.signalAll();
 *     } finally {
 *       pauseLock.unlock();
 *     }
 *   }
 * }}</pre>
 *
 * @since 1.5
 * @author Doug Lea
 */
public class ThreadPoolExecutor extends AbstractExecutorService {
    /**
     * The main pool control state, ctl, is an atomic integer packing   // ctl 是一个原子变量,用来保存两个值
     * two conceptual fields
     *   workerCount, indicating the effective number of threads        // 当前线程数
     *   runState,    indicating whether running, shutting down etc     // 线程池的状态
     *
     * In order to pack them into one int, we limit workerCount to      //为了将两个值打包到一个int大小的变量中
     * (2^29)-1 (about 500 million) threads rather than (2^31)-1 (2     //我们限制工作线程数小于(2^29)-1, 这个大约5亿
     * billion) otherwise representable. If this is ever an issue in    //而不是(2^31)-1, (大约20亿)
     * the future, the variable can be changed to be an AtomicLong,     //如果这个在未来存在太小,可以换成AtomicLong
     * and the shift/mask constants below adjusted. But until the need  //并需要调整shift和mask
     * arises, this code is a bit faster and simpler using an int.      //AtomicInteger还是比AtomicLong快一些的,以为计算的位毕竟少一些
     *
     * The workerCount is the number of workers that have been          //workerCount
     * permitted to start and not permitted to stop.  The value may be
     * transiently different from the actual number of live threads,
     * for example when a ThreadFactory fails to create a thread when
     * asked, and when exiting threads are still performing
     * bookkeeping before terminating. The user-visible pool size is
     * reported as the current size of the workers set.
     *
     * The runState provides the main lifecyle control, taking on values:   //runState控制线程池的生命周期
     *
     *   RUNNING:  Accept new tasks and process queued tasks
     *   SHUTDOWN: Don't accept new tasks, but process queued tasks         //不接收任务,但是还在执行队列中的任务
     *   STOP:     Don't accept new tasks, don't process queued tasks,      //不接收任务,不执行队列中任务,中断正在执行的任务
     *             and interrupt in-progress tasks
     *   TIDYING:  All tasks have terminated, workerCount is zero,          //所有的任务被停止了,workerCount为0,会调用terminated钩子方法
     *             the thread transitioning to state TIDYING
     *             will run the terminated() hook method
     *   TERMINATED: terminated() has completed                             //完全关闭
     *
     * The numerical order among these values matters, to allow
     * ordered comparisons. The runState monotonically increases over
     * time, but need not hit each state. The transitions are:
     *
     * RUNNING -> SHUTDOWN
     *    On invocation of shutdown(), perhaps implicitly in finalize()
     * (RUNNING or SHUTDOWN) -> STOP
     *    On invocation of shutdownNow()
     * SHUTDOWN -> TIDYING
     *    When both queue and pool are empty
     * STOP -> TIDYING
     *    When pool is empty
     * TIDYING -> TERMINATED
     *    When the terminated() hook method has completed
     *
     * Threads waiting in awaitTermination() will return when the   //线程等待线程池关闭会返回
     * state reaches TERMINATED.
     *
     * Detecting the transition from SHUTDOWN to TIDYING is less
     * straightforward than you'd like because the queue may become
     * empty after non-empty and vice versa during SHUTDOWN state, but
     * we can only terminate if, after seeing that it is empty, we see
     * that workerCount is 0 (which sometimes entails a recheck -- see
     * below).
     */
    // ctl 是一个原子变量,用来保存两个值(当前线程池线程数和线程池状态), 由于这两个值是相互影响的, 不能并发修改, 要不就加锁
    // 做成在一个原子变量里,每次修改线程数和线程池状态,都可以用CAS,不需要加锁, 不得不说, 作者真的考虑的很周到!!
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    private static final int COUNT_BITS = Integer.SIZE - 3;     //32 - 3 = 29
    private static final int CAPACITY   = (1 << COUNT_BITS) - 1;    //111...1 (28位1)

    // runState is stored in the high-order bits            // runState(线程池的状态)存储在ctl的高阶位中
    private static final int RUNNING    = -1 << COUNT_BITS; // 11100000000000000000000000000000 (一共32位,3个1,其余都是0)
    private static final int SHUTDOWN   =  0 << COUNT_BITS; //                                0
    private static final int STOP       =  1 << COUNT_BITS; //   100000000000000000000000000000 (一共30位)
    private static final int TIDYING    =  2 << COUNT_BITS; //  1000000000000000000000000000000 (一共31位)
    private static final int TERMINATED =  3 << COUNT_BITS; //  1100000000000000000000000000000 (一共31位)
                                                            // 作者是想用32位里的高3位用来表示运行状态,剩下的29位,用来表示线程数,所以最大线程数为2^29-1
    // Packing and unpacking ctl                            // 从ctl中分别求出两个值(unpack操作); 根据两个值计算ctl的值(pack操作);
    private static int runStateOf(int c)     { return c & ~CAPACITY; }  //将CAPACITY按位取反, & 按位相与,计算出高三位的值,低29位都为0,结果就是线程运行状态
    private static int workerCountOf(int c)  { return c & CAPACITY; }   //& 按位相与, 高三位都为0, 结果就是当前线程数
    private static int ctlOf(int rs, int wc) { return rs | wc; }        //rs就是runState,wc就是workerCount,这个是根据rs和wc求出ctl的值
                                                                        //因为rs低位全为0,wc高位全为0,所以只要按位或操作,结果就是ctl的值
    /*
     * Bit field accessors that don't require unpacking ctl.                    //位域并不需要对ctl解包
     * These depend on the bit layout and on workerCount being never negative.  //这些位计算的方法依赖两个条件: 位的设计和workerCount(线程数)从不为负数
     */

    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    private static boolean isRunning(int c) {
        return c < SHUTDOWN;    //SHUTDOWN值为0, 小于SHUTDOWN说明高位为111,也就是为负数才表示RUNNING状态.
    }

    /**
     * Attempt to CAS-increment the workerCount field of ctl.
     */
    private boolean compareAndIncrementWorkerCount(int expect) {    //CAS自增
        return ctl.compareAndSet(expect, expect + 1);   //用atomicInteger好处就是每次更新都可以CAS,不需要加锁,线程池的提高吞吐量.
    }

    /**
     * Attempt to CAS-decrement the workerCount field of ctl.
     */
    private boolean compareAndDecrementWorkerCount(int expect) {    //CAS自减
        return ctl.compareAndSet(expect, expect - 1);
    }

    /**
     * Decrements the workerCount field of ctl. This is called only on
     * abrupt termination of a thread (see processWorkerExit). Other
     * decrements are performed within getTask.
     */
    private void decrementWorkerCount() {   //循环进行CAS自减,直到成功
        do {} while (! compareAndDecrementWorkerCount(ctl.get()));
    }

    /**
     * The queue used for holding tasks and handing off to worker
     * threads.  We do not require that workQueue.poll() returning
     * null necessarily means that workQueue.isEmpty(), so rely
     * solely on isEmpty to see if the queue is empty (which we must
     * do for example when deciding whether to transition from
     * SHUTDOWN to TIDYING).  This accommodates special-purpose
     * queues such as DelayQueues for which poll() is allowed to
     * return null even if it may later return non-null when delays
     * expire.
     */
    private final BlockingQueue<Runnable> workQueue;

    /**
     * Lock held on access to workers set and related bookkeeping.
     * While we could use a concurrent set of some sort, it turns out
     * to be generally preferable to use a lock. Among the reasons is
     * that this serializes interruptIdleWorkers, which avoids
     * unnecessary interrupt storms, especially during shutdown.
     * Otherwise exiting threads would concurrently interrupt those
     * that have not yet interrupted. It also simplifies some of the
     * associated statistics bookkeeping of largestPoolSize etc. We
     * also hold mainLock on shutdown and shutdownNow, for the sake of
     * ensuring workers set is stable while separately checking
     * permission to interrupt and actually interrupting.
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * Set containing all worker threads in pool. Accessed only when
     * holding mainLock.
     */
    private final HashSet<Worker> workers = new HashSet<Worker>();  //访问这个集合必须持有mainLock

    /**
     * Wait condition to support awaitTermination
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * Tracks largest attained pool size. Accessed only under
     * mainLock.
     */
    private int largestPoolSize;

    /**
     * Counter for completed tasks. Updated only on termination of
     * worker threads. Accessed only under mainLock.
     */
    private long completedTaskCount;    //完成的任务数

    /*
     * All user control parameters are declared as volatiles so that
     * ongoing actions are based on freshest values, but without need
     * for locking, since no internal invariants depend on them
     * changing synchronously with respect to other actions.
     */

    /**
     * Factory for new threads. All threads are created using this
     * factory (via method addWorker).  All callers must be prepared
     * for addWorker to fail, which may reflect a system or user's
     * policy limiting the number of threads.  Even though it is not
     * treated as an error, failure to create threads may result in
     * new tasks being rejected or existing ones remaining stuck in
     * the queue.
     *
     * We go further and preserve pool invariants even in the face of
     * errors such as OutOfMemoryError, that might be thrown while
     * trying to create threads.  Such errors are rather common due to
     * the need to allocate a native stack in Thread#start, and users
     * will want to perform clean pool shutdown to clean up.  There
     * will likely be enough memory available for the cleanup code to
     * complete without encountering yet another OutOfMemoryError.
     */
    private volatile ThreadFactory threadFactory;

    /**
     * Handler called when saturated or shutdown in execute.
     */
    private volatile RejectedExecutionHandler handler;

    /**
     * Timeout in nanoseconds for idle threads waiting for work.
     * Threads use this timeout when there are more than corePoolSize
     * present or if allowCoreThreadTimeOut. Otherwise they wait
     * forever for new work.
     */
    private volatile long keepAliveTime;    //单位是nanoseconds 纳秒

    /**
     * If false (default), core threads stay alive even when idle.
     * If true, core threads use keepAliveTime to time out waiting
     * for work.
     */
    private volatile boolean allowCoreThreadTimeOut;    //默认false

    /**
     * Core pool size is the minimum number of workers to keep alive
     * (and not allow to time out etc) unless allowCoreThreadTimeOut
     * is set, in which case the minimum is zero.
     */
    private volatile int corePoolSize;

    /**
     * Maximum pool size. Note that the actual maximum is internally
     * bounded by CAPACITY.                                             //最大线程数的上限是 CAPACITY
     */
    private volatile int maximumPoolSize;

    /**
     * The default rejected execution handler
     */
    private static final RejectedExecutionHandler defaultHandler =      //默认策略是抛弃任务,也就是抛出异常: RejectedExecutionException
        new AbortPolicy();

    /**
     * Permission required for callers of shutdown and shutdownNow.
     * We additionally require (see checkShutdownAccess) that callers
     * have permission to actually interrupt threads in the worker set
     * (as governed by Thread.interrupt, which relies on
     * ThreadGroup.checkAccess, which in turn relies on
     * SecurityManager.checkAccess). Shutdowns are attempted only if    //shutDown操作,需要校验权限: ThreadGroup.checkAccess, SecurityManager.checkAccess
     * these checks pass.
     *
     * All actual invocations of Thread.interrupt (see
     * interruptIdleWorkers and interruptWorkers) ignore
     * SecurityExceptions, meaning that the attempted interrupts
     * silently fail. In the case of shutdown, they should not fail
     * unless the SecurityManager has inconsistent policies, sometimes
     * allowing access to a thread and sometimes not. In such cases,
     * failure to actually interrupt threads may disable or delay full
     * termination. Other uses of interruptIdleWorkers are advisory,
     * and failure to actually interrupt will merely delay response to
     * configuration changes so is not handled exceptionally.
     */
    private static final RuntimePermission shutdownPerm =
        new RuntimePermission("modifyThread");

    /**
     * Class Worker mainly maintains interrupt control state for            //Worker 主要维护任务线程的中断控制状态
     * threads running tasks, along with other minor bookkeeping.           //还有些次要的记录
     * This class opportunistically extends AbstractQueuedSynchronizer
     * to simplify acquiring and releasing a lock surrounding each
     * task execution.  This protects against interrupts that are
     * intended to wake up a worker thread waiting for a task from
     * instead interrupting a task being run.  We implement a simple        //我们实现了一个简单的非重入互斥锁,没有用ReentrantLock
     * non-reentrant mutual exclusion lock rather than use                  //Worker是直接继承的AQS,所以是不可重入的,tryLock会直接失败
     * ReentrantLock because we do not want worker tasks to be able to      //我们不希望任务的工作线程能够获取锁,修改线程的参数
     * reacquire the lock when they invoke pool control methods like
     * setCorePoolSize.  Additionally, to suppress interrupts until
     * the thread actually starts running tasks, we initialize lock         //我们初始化锁state为负数,在开始的时候清空它
     * state to a negative value, and clear it upon start (in
     * runWorker).
     */
    private final class Worker                  //这里把线程池里面的工作线程抽象成一个一个的Worker对象,实现了Runnable接口
        extends AbstractQueuedSynchronizer      //Worker里面有个Thread实例, 其实Worker就是thread的代理对象
        implements Runnable                     //Worker的run方法就是调用thread的run方法
    {
        /**
         * This class will never be serialized, but we provide a
         * serialVersionUID to suppress a javac warning.
         */
        private static final long serialVersionUID = 6138294804551838833L;

        /** Thread this worker is running in.  Null if factory fails. */    //thread可能会为null
        final Thread thread;
        /** Initial task to run.  Possibly null. */                         //初始化时就有任务给他执行的,也可以为null
        Runnable firstTask;                                                 //这是线程提交给线程池的任务
        /** Per-thread task counter */                                      //每个线程完成的任务数
        volatile long completedTasks;

        /**
         * Creates with given first task and thread from ThreadFactory.
         * @param firstTask the first task (null if none)
         */
        Worker(Runnable firstTask) {
            setState(-1); // inhibit interrupts until runWorker           //设置-1禁止中断
            this.firstTask = firstTask;
            this.thread = getThreadFactory().newThread(this);           //根据上面的注释,自定义的ThreadFactory如果创建线程失败,可以返回null
        }

        /** Delegates main run loop to outer runWorker  */
        public void run() {
            runWorker(this);    //todo,简单描述下做了什么
        }

        // Lock methods
        //
        // The value 0 represents the unlocked state.   //AQS的state 0 表示未加锁
        // The value 1 represents the locked state.     //1表示加锁了

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        public void lock()        { acquire(1); }
        public boolean tryLock()  { return tryAcquire(1); }
        public void unlock()      { release(1); }
        public boolean isLocked() { return isHeldExclusively(); }

        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }

    /*
     * Methods for setting control state
     */

    /**
     * Transitions runState to given target, or leaves it alone if          // 转换runState为目标状态targetState
     * already at least the given target.
     *
     * @param targetState the desired state, either SHUTDOWN or STOP
     *        (but not TIDYING or TERMINATED -- use tryTerminate for that)
     */
    private void advanceRunState(int targetState) {
        for (;;) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) ||                          // 这里会判断是否符合条件
                ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                break;
        }
    }

    /**
     * Transitions to TERMINATED state if either (SHUTDOWN and pool     // 如果处于SHUTDOWN状态并且线程池和队列为空.
     * and queue empty) or (STOP and pool empty).  If otherwise         // 或者处于STOP状态并且线程池为空, 将线程池状态转换为TERMINATED状态.
     * eligible to terminate but workerCount is nonzero, interrupts an  // 如果符合关闭条件, 但是线程数不为0, 则中断空闲的线程.
     * idle worker to ensure that shutdown signals propagate. This      // 确保关闭的信号传播下去.
     * method must be called following any action that might make       // 这个方法必须在可能导致termination操作之后调用.
     * termination possible -- reducing worker count or removing tasks  // 比如减少线程池线程数或者再shutdown时删除队列中的任务.
     * from the queue during shutdown. The method is non-private to     // 这个方法设置成非私有的,是为了提供给ScheduledThreadPoolExecutor使用的.
     * allow access from ScheduledThreadPoolExecutor.
     */
    final void tryTerminate() {
        for (;;) {
            int c = ctl.get();
            if (isRunning(c) ||
                runStateAtLeast(c, TIDYING) ||
                (runStateOf(c) == SHUTDOWN && ! workQueue.isEmpty()))   // 符合这些条件的一个则不允许关闭线程池
                return;
            if (workerCountOf(c) != 0) { // Eligible to terminate       // 线程数不为0,需要中断空闲的线程
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        terminated();
                    } finally {
                        ctl.set(ctlOf(TERMINATED, 0));
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // else retry on failed CAS
        }
    }

    /*
     * Methods for controlling interrupts to worker threads.
     */

    /**
     * If there is a security manager, makes sure caller has
     * permission to shut down threads in general (see shutdownPerm).
     * If this passes, additionally makes sure the caller is allowed
     * to interrupt each worker thread. This might not be true even if
     * first check passed, if the SecurityManager treats some threads
     * specially.
     */
    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers)
                    security.checkAccess(w.thread);
            } finally {
                mainLock.unlock();
            }
        }
    }

    /**
     * Interrupts all threads, even if active. Ignores SecurityExceptions
     * (in which case some threads may remain uninterrupted).
     */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers)
                w.interruptIfStarted();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Interrupts threads that might be waiting for tasks (as
     * indicated by not being locked) so they can check for
     * termination or configuration changes. Ignores
     * SecurityExceptions (in which case some threads may remain
     * uninterrupted).
     *
     * @param onlyOne If true, interrupt at most one worker. This is
     * called only from tryTerminate when termination is otherwise
     * enabled but there are still other workers.  In this case, at
     * most one waiting worker is interrupted to propagate shutdown
     * signals in case all threads are currently waiting.
     * Interrupting any arbitrary thread ensures that newly arriving
     * workers since shutdown began will also eventually exit.
     * To guarantee eventual termination, it suffices to always
     * interrupt only one idle worker, but shutdown() interrupts all
     * idle workers so that redundant workers exit promptly, not
     * waiting for a straggler task to finish.
     */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers) {
                Thread t = w.thread;
                if (!t.isInterrupted() && w.tryLock()) {    //如果线程t没有中断,就尝试获取当前worker的锁.所以这边锁也只是锁一个Worker线程.
                    try {                                   //注意: Worker执行runWorker()方法的时候也会获取锁,这里tryLock就会返回false,所以这个方法是不会中断工作中的线程的.
                        t.interrupt();                      //获取到了锁,再中断线程.
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Common form of interruptIdleWorkers, to avoid having to
     * remember what the boolean argument means.
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    private static final boolean ONLY_ONE = true;

    /*
     * Misc utilities, most of which are also exported to
     * ScheduledThreadPoolExecutor
     */

    /**
     * Invokes the rejected execution handler for the given command.
     * Package-protected for use by ScheduledThreadPoolExecutor.
     */
    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    /**
     * Performs any further cleanup following run state transition on
     * invocation of shutdown.  A no-op here, but used by
     * ScheduledThreadPoolExecutor to cancel delayed tasks.
     */
    void onShutdown() {
    }

    /**
     * State check needed by ScheduledThreadPoolExecutor to
     * enable running tasks during shutdown.
     *
     * @param shutdownOK true if should return true if SHUTDOWN
     */
    final boolean isRunningOrShutdown(boolean shutdownOK) {
        int rs = runStateOf(ctl.get());
        return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
    }

    /**
     * Drains the task queue into a new list, normally using
     * drainTo. But if the queue is a DelayQueue or any other kind of
     * queue for which poll or drainTo may fail to remove some
     * elements, it deletes them one by one.
     */
    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        List<Runnable> taskList = new ArrayList<Runnable>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }

    /*
     * Methods for creating, running and cleaning up after workers
     */

    /**
     * Checks if a new worker can be added with respect to current
     * pool state and the given bound (either core or maximum). If so,
     * the worker count is adjusted accordingly, and, if possible, a
     * new worker is created and started, running firstTask as its
     * first task. This method returns false if the pool is stopped or
     * eligible to shut down. It also returns false if the thread
     * factory fails to create a thread when asked.  If the thread
     * creation fails, either due to the thread factory returning
     * null, or due to an exception (typically OutOfMemoryError in
     * Thread#start), we roll back cleanly.
     *
     * @param firstTask the task the new thread should run first (or
     * null if none). Workers are created with an initial first task
     * (in method execute()) to bypass queuing when there are fewer
     * than corePoolSize threads (in which case we always start one),
     * or when the queue is full (in which case we must bypass queue).
     * Initially idle threads are usually created via
     * prestartCoreThread or to replace other dying workers.
     *
     * @param core if true use corePoolSize as bound, else
     * maximumPoolSize. (A boolean indicator is used here rather than a
     * value to ensure reads of fresh values after checking other pool
     * state).
     * @return true if successful
     */
    private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.
            if (rs >= SHUTDOWN &&
                ! (rs == SHUTDOWN &&
                   firstTask == null &&
                   ! workQueue.isEmpty()))
                return false;

            for (;;) {
                int wc = workerCountOf(c);
                if (wc >= CAPACITY ||
                    wc >= (core ? corePoolSize : maximumPoolSize))
                    return false;
                if (compareAndIncrementWorkerCount(c))  // 增加线程数.
                    break retry;
                c = ctl.get();  // Re-read ctl
                if (runStateOf(c) != rs)
                    continue retry;
                // else CAS failed due to workerCount change; retry inner loop
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            final ReentrantLock mainLock = this.mainLock;
            w = new Worker(firstTask);      // 创建一个新的线程,Worker是线程的包装对象.
            final Thread t = w.thread;
            if (t != null) {
                mainLock.lock();
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    int c = ctl.get();
                    int rs = runStateOf(c);

                    if (rs < SHUTDOWN ||
                        (rs == SHUTDOWN && firstTask == null)) {
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();
                        workers.add(w);
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            if (! workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }

    /**
     * Rolls back the worker thread creation.
     * - removes worker from workers, if present
     * - decrements worker count
     * - rechecks for termination, in case the existence of this
     *   worker was holding up termination
     */
    private void addWorkerFailed(Worker w) {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            if (w != null)
                workers.remove(w);
            decrementWorkerCount();
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Performs cleanup and bookkeeping for a dying worker. Called
     * only from worker threads. Unless completedAbruptly is set,
     * assumes that workerCount has already been adjusted to account
     * for exit.  This method removes thread from worker set, and
     * possibly terminates the pool or replaces the worker if either
     * it exited due to user task exception or if fewer than
     * corePoolSize workers are running or queue is non-empty but
     * there are no workers.
     *
     * @param w the worker
     * @param completedAbruptly if the worker died due to user exception
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        if (completedAbruptly) // If abrupt, then workerCount wasn't adjusted
            decrementWorkerCount();

        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            completedTaskCount += w.completedTasks;
            workers.remove(w);
        } finally {
            mainLock.unlock();
        }

        tryTerminate();

        int c = ctl.get();
        if (runStateLessThan(c, STOP)) {
            if (!completedAbruptly) {
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && ! workQueue.isEmpty())
                    min = 1;
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            addWorker(null, false);
        }
    }

    /**
     * Performs blocking or timed wait for a task, depending on
     * current configuration settings, or returns null if this worker
     * must exit because of any of:
     * 1. There are more than maximumPoolSize workers (due to
     *    a call to setMaximumPoolSize).
     * 2. The pool is stopped.
     * 3. The pool is shutdown and the queue is empty.
     * 4. This worker timed out waiting for a task, and timed-out
     *    workers are subject to termination (that is,
     *    {@code allowCoreThreadTimeOut || workerCount > corePoolSize})
     *    both before and after the timed wait.
     *
     * @return task, or null if the worker must exit, in which case
     *         workerCount is decremented
     */
    private Runnable getTask() {
        boolean timedOut = false; // Did the last poll() time out?

        retry:
        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);

            // Check if queue empty only if necessary.
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                decrementWorkerCount();
                return null;
            }

            boolean timed;      // Are workers subject to culling?

            for (;;) {
                int wc = workerCountOf(c);
                timed = allowCoreThreadTimeOut || wc > corePoolSize;

                if (wc <= maximumPoolSize && ! (timedOut && timed))
                    break;
                if (compareAndDecrementWorkerCount(c))
                    return null;
                c = ctl.get();  // Re-read ctl
                if (runStateOf(c) != rs)
                    continue retry;
                // else CAS failed due to workerCount change; retry inner loop
            }

            try {
                Runnable r = timed ?
                    workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                    workQueue.take();   // 阻塞队列的take方法,如果没有任务会一直阻塞下去(所以在没有任务时,空闲的核心线程都是在阻塞的等待获取任务,这样就不会消耗CPU了)
                if (r != null)
                    return r;
                timedOut = true;
            } catch (InterruptedException retry) {
                timedOut = false;
            }
        }
    }

    /**
     * Main worker run loop.  Repeatedly gets tasks from queue and
     * executes them, while coping with a number of issues:
     *
     * 1. We may start out with an initial task, in which case we
     * don't need to get the first one. Otherwise, as long as pool is
     * running, we get tasks from getTask. If it returns null then the
     * worker exits due to changed pool state or configuration
     * parameters.  Other exits result from exception throws in
     * external code, in which case completedAbruptly holds, which
     * usually leads processWorkerExit to replace this thread.
     *
     * 2. Before running any task, the lock is acquired to prevent
     * other pool interrupts while the task is executing, and
     * clearInterruptsForTaskRun called to ensure that unless pool is
     * stopping, this thread does not have its interrupt set.
     *
     * 3. Each task run is preceded by a call to beforeExecute, which
     * might throw an exception, in which case we cause thread to die
     * (breaking loop with completedAbruptly true) without processing
     * the task.
     *
     * 4. Assuming beforeExecute completes normally, we run the task,
     * gathering any of its thrown exceptions to send to
     * afterExecute. We separately handle RuntimeException, Error
     * (both of which the specs guarantee that we trap) and arbitrary
     * Throwables.  Because we cannot rethrow Throwables within
     * Runnable.run, we wrap them within Errors on the way out (to the
     * thread's UncaughtExceptionHandler).  Any thrown exception also
     * conservatively causes thread to die.
     *
     * 5. After task.run completes, we call afterExecute, which may
     * also throw an exception, which will also cause thread to
     * die. According to JLS Sec 14.20, this exception is the one that
     * will be in effect even if task.run throws.
     *
     * The net effect of the exception mechanics is that afterExecute
     * and the thread's UncaughtExceptionHandler have as accurate
     * information as we can provide about any problems encountered by
     * user code.
     *
     * @param w the worker
     */
    final void runWorker(Worker w) {
        Thread wt = Thread.currentThread();
        Runnable task = w.firstTask;
        w.firstTask = null;
        w.unlock(); // allow interrupts
        boolean completedAbruptly = true;
        try {
            while (task != null || (task = getTask()) != null) {
                w.lock();
                // If pool is stopping, ensure thread is interrupted;
                // if not, ensure thread is not interrupted.  This
                // requires a recheck in second case to deal with
                // shutdownNow race while clearing interrupt
                if ((runStateAtLeast(ctl.get(), STOP) ||
                     (Thread.interrupted() &&
                      runStateAtLeast(ctl.get(), STOP))) &&
                    !wt.isInterrupted())
                    wt.interrupt();
                try {
                    beforeExecute(wt, task);
                    Throwable thrown = null;
                    try {
                        task.run();
                    } catch (RuntimeException x) {
                        thrown = x; throw x;
                    } catch (Error x) {
                        thrown = x; throw x;
                    } catch (Throwable x) {
                        thrown = x; throw new Error(x);
                    } finally {
                        afterExecute(task, thrown);
                    }
                } finally {
                    task = null;
                    w.completedTasks++;
                    w.unlock();
                }
            }
            completedAbruptly = false;
        } finally {
            processWorkerExit(w, completedAbruptly);
        }
    }

    // Public constructors and methods

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory and rejected execution handler.
     * It may be more convenient to use one of the {@link Executors} factory
     * methods instead of this general purpose constructor.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             Executors.defaultThreadFactory(), defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default rejected execution handler.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             threadFactory, defaultHandler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters and default thread factory.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
             Executors.defaultThreadFactory(), handler);
    }

    /**
     * Creates a new {@code ThreadPoolExecutor} with the given initial
     * parameters.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *        if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize the maximum number of threads to allow in the
     *        pool
     * @param keepAliveTime when the number of threads is greater than
     *        the core, this is the maximum time that excess idle threads
     *        will wait for new tasks before terminating.
     * @param unit the time unit for the {@code keepAliveTime} argument
     * @param workQueue the queue to use for holding tasks before they are
     *        executed.  This queue will hold only the {@code Runnable}
     *        tasks submitted by the {@code execute} method.
     * @param threadFactory the factory to use when the executor
     *        creates a new thread
     * @param handler the handler to use when execution is blocked
     *        because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if one of the following holds:<br>
     *         {@code corePoolSize < 0}<br>
     *         {@code keepAliveTime < 0}<br>
     *         {@code maximumPoolSize <= 0}<br>
     *         {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException if {@code workQueue}
     *         or {@code threadFactory} or {@code handler} is null
     */
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize ||
                keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }

    /**
     * Executes the given task sometime in the future.  The task            //在未来的某个时间执行指定的任务,说明不是立马执行
     * may execute in a new thread or in an existing pooled thread.         //可能新建一个线程来执行任务或者在一个已经存在的线程中执行任务
     *
     * If the task cannot be submitted for execution, either because this
     * executor has been shutdown or because its capacity has been reached, //线程池关闭或者队列到上限会根据RejectedExecutionHandler策略处理
     * the task is handled by the current {@code RejectedExecutionHandler}.
     *
     * @param command the task to execute
     * @throws RejectedExecutionException at discretion of
     *         {@code RejectedExecutionHandler}, if the task
     *         cannot be accepted for execution
     * @throws NullPointerException if {@code command} is null
     */
    public void execute(Runnable command) {     //这个就是执行task任务了
        if (command == null)
            throw new NullPointerException();
        /*
         * Proceed in 3 steps:
         *
         * 1. If fewer than corePoolSize threads are running, try to
         * start a new thread with the given command as its first
         * task.  The call to addWorker atomically checks runState and
         * workerCount, and so prevents false alarms that would add
         * threads when it shouldn't, by returning false.
         *
         * 2. If a task can be successfully queued, then we still need
         * to double-check whether we should have added a thread
         * (because existing ones died since last checking) or that
         * the pool shut down since entry into this method. So we
         * recheck state and if necessary roll back the enqueuing if
         * stopped, or start a new thread if there are none.
         *
         * 3. If we cannot queue task, then we try to add a new
         * thread.  If it fails, we know we are shut down or saturated
         * and so reject the task.
         */
        int c = ctl.get();
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true))
                return;
            c = ctl.get();
        }
        if (isRunning(c) && workQueue.offer(command)) {     // 任务添加到队列失败, SynchronousQueue没有容量,如果没有等待线程offer会返回false.
            int recheck = ctl.get();
            if (! isRunning(recheck) && remove(command))
                reject(command);
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        else if (!addWorker(command, false))            // 任务添加失败就走到这里, 创建(false)非核心线程.
            reject(command);
    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #awaitTermination awaitTermination}
     * to do that.
     *
     * @throws SecurityException {@inheritDoc}
     */
    public void shutdown() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(SHUTDOWN);
            interruptIdleWorkers();
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
    }

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks
     * that were awaiting execution. These tasks are drained (removed)
     * from the task queue upon return from this method.
     *
     * <p>This method does not wait for actively executing tasks to
     * terminate.  Use {@link #awaitTermination awaitTermination} to
     * do that.
     *
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  This implementation
     * cancels tasks via {@link Thread#interrupt}, so any task that
     * fails to respond to interrupts may never terminate.
     *
     * @throws SecurityException {@inheritDoc}
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            advanceRunState(STOP);
            interruptWorkers();
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }
        tryTerminate();
        return tasks;
    }

    public boolean isShutdown() {
        return ! isRunning(ctl.get());
    }

    /**
     * Returns true if this executor is in the process of terminating
     * after {@link #shutdown} or {@link #shutdownNow} but has not
     * completely terminated.  This method may be useful for
     * debugging. A return of {@code true} reported a sufficient
     * period after shutdown may indicate that submitted tasks have
     * ignored or suppressed interruption, causing this executor not
     * to properly terminate.
     *
     * @return true if terminating but not yet terminated
     */
    public boolean isTerminating() {
        int c = ctl.get();
        return ! isRunning(c) && runStateLessThan(c, TERMINATED);
    }

    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (;;) {
                if (runStateAtLeast(ctl.get(), TERMINATED))
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Invokes {@code shutdown} when this executor is no longer
     * referenced and it has no threads.
     */
    protected void finalize() {
        shutdown();
    }

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory the new thread factory
     * @throws NullPointerException if threadFactory is null
     * @see #getThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null)
            throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     * @see #setThreadFactory
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler the new handler
     * @throws NullPointerException if handler is null
     * @see #getRejectedExecutionHandler
     */
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        this.handler = handler;
    }

    /**
     * Returns the current handler for unexecutable tasks.
     *
     * @return the current handler
     * @see #setRejectedExecutionHandler
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    /**
     * Sets the core number of threads.  This overrides any value set
     * in the constructor.  If the new value is smaller than the
     * current value, excess existing threads will be terminated when
     * they next become idle.  If larger, new threads will, if needed,
     * be started to execute any queued tasks.
     *
     * @param corePoolSize the new core size
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @see #getCorePoolSize
     */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0)
            throw new IllegalArgumentException();
        int delta = corePoolSize - this.corePoolSize;
        this.corePoolSize = corePoolSize;
        if (workerCountOf(ctl.get()) > corePoolSize)    //正在运行的线程大于待设置的核心线程,所以需要中断线程.
            interruptIdleWorkers();                     //中断变成空闲的线程
        else if (delta > 0) {
            // We don't really know how many new threads are "needed".
            // As a heuristic, prestart enough new workers (up to new
            // core size) to handle the current number of tasks in
            // queue, but stop if queue becomes empty while doing so.
            int k = Math.min(delta, workQueue.size());
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty())
                    break;
            }
        }
    }

    /**
     * Returns the core number of threads.
     *
     * @return the core number of threads
     * @see #setCorePoolSize
     */
    public int getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Starts a core thread, causing it to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed. This method will return {@code false}
     * if all core threads have already been started.
     *
     * @return {@code true} if a thread was started
     */
    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize &&   //判断如果当前线程数小于核心线程
            addWorker(null, true);              //新建空闲的线程,等待获取任务
    }

    /**
     * Same as prestartCoreThread except arranges that at least one
     * thread is started even if corePoolSize is 0.
     */
    void ensurePrestart() {
        int wc = workerCountOf(ctl.get());
        if (wc < corePoolSize)
            addWorker(null, true);
        else if (wc == 0)
            addWorker(null, false);
    }

    /**
     * Starts all core threads, causing them to idly wait for work. This
     * overrides the default policy of starting core threads only when
     * new tasks are executed.
     *
     * @return the number of threads started
     */
    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true))   //预先创建所有的核心线程,感觉这个方法用的比较少,核心线程被动创建就可以了
            ++n;
        return n;
    }

    /**
     * Returns true if this pool allows core threads to time out and
     * terminate if no tasks arrive within the keepAlive time, being
     * replaced if needed when new tasks arrive. When true, the same
     * keep-alive policy applying to non-core threads applies also to
     * core threads. When false (the default), core threads are never
     * terminated due to lack of incoming tasks.
     *
     * @return {@code true} if core threads are allowed to time out,
     *         else {@code false}
     *
     * @since 1.6
     */
    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    /**
     * Sets the policy governing whether core threads may time out and
     * terminate if no tasks arrive within the keep-alive time, being
     * replaced if needed when new tasks arrive. When false, core
     * threads are never terminated due to lack of incoming
     * tasks. When true, the same keep-alive policy applying to
     * non-core threads applies also to core threads. To avoid
     * continual thread replacement, the keep-alive time must be
     * greater than zero when setting {@code true}. This method
     * should in general be called before the pool is actively used.
     *
     * @param value {@code true} if should time out, else {@code false}
     * @throws IllegalArgumentException if value is {@code true}
     *         and the current keep-alive time is not greater than zero
     *
     * @since 1.6
     */
    public void allowCoreThreadTimeOut(boolean value) {
        if (value && keepAliveTime <= 0)
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            if (value)
                interruptIdleWorkers();
        }
    }

    /**
     * Sets the maximum allowed number of threads. This overrides any
     * value set in the constructor. If the new value is smaller than
     * the current value, excess existing threads will be
     * terminated when they next become idle.
     *
     * @param maximumPoolSize the new maximum
     * @throws IllegalArgumentException if the new maximum is
     *         less than or equal to zero, or
     *         less than the {@linkplain #getCorePoolSize core pool size}
     * @see #getMaximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();
        this.maximumPoolSize = maximumPoolSize;
        if (workerCountOf(ctl.get()) > maximumPoolSize)
            interruptIdleWorkers();
    }

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     * @see #setMaximumPoolSize
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Sets the time limit for which threads may remain idle before
     * being terminated.  If there are more than the core number of
     * threads currently in the pool, after waiting this amount of
     * time without processing a task, excess threads will be
     * terminated.  This overrides any value set in the constructor.
     *
     * @param time the time to wait.  A time value of zero will cause
     *        excess threads to terminate immediately after executing tasks.
     * @param unit the time unit of the {@code time} argument
     * @throws IllegalArgumentException if {@code time} less than zero or
     *         if {@code time} is zero and {@code allowsCoreThreadTimeOut}
     * @see #getKeepAliveTime
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0)
            throw new IllegalArgumentException();
        if (time == 0 && allowsCoreThreadTimeOut())
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        long keepAliveTime = unit.toNanos(time);
        long delta = keepAliveTime - this.keepAliveTime;
        this.keepAliveTime = keepAliveTime;
        if (delta < 0)
            interruptIdleWorkers();
    }

    /**
     * Returns the thread keep-alive time, which is the amount of time
     * that threads in excess of the core pool size may remain
     * idle before being terminated.
     *
     * @param unit the desired time unit of the result
     * @return the time limit
     * @see #setKeepAliveTime
     */
    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    /* User-level queue utilities */

    /**
     * Returns the task queue used by this executor. Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieving the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }

    /**
     * Removes this task from the executor's internal queue if it is
     * present, thus causing it not to be run if it has not already
     * started.
     *
     * <p> This method may be useful as one part of a cancellation
     * scheme.  It may fail to remove tasks that have been converted
     * into other forms before being placed on the internal queue. For
     * example, a task entered using {@code submit} might be
     * converted into a form that maintains {@code Future} status.
     * However, in such cases, method {@link #purge} may be used to
     * remove those Futures that have been cancelled.
     *
     * @param task the task to remove
     * @return true if the task was removed
     */
    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }

    /**
     * Tries to remove from the work queue all {@link Future}
     * tasks that have been cancelled. This method can be useful as a
     * storage reclamation operation, that has no other impact on
     * functionality. Cancelled tasks are never executed, but may
     * accumulate in work queues until worker threads can actively
     * remove them. Invoking this method instead tries to remove them now.
     * However, this method may fail to remove tasks in
     * the presence of interference by other threads.
     */
    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    it.remove();
            }
        } catch (ConcurrentModificationException fallThrough) {
            // Take slow path if we encounter interference during traversal.
            // Make copy for traversal and call remove for cancelled entries.
            // The slow path is more likely to be O(N*N).
            for (Object r : q.toArray())
                if (r instanceof Future<?> && ((Future<?>)r).isCancelled())
                    q.remove(r);
        }

        tryTerminate(); // In case SHUTDOWN and now empty
    }

    /* Statistics */

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // Remove rare and surprising possibility of
            // isTerminated() && getPoolSize() > 0
            return runStateAtLeast(ctl.get(), TIDYING) ? 0
                : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate number of threads that are actively
     * executing tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers)
                if (w.isLocked())
                    ++n;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have ever been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation.
     *
     * @return the number of tasks
     */
    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked())
                    ++n;
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers)
                n += w.completedTasks;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state and estimated worker and
     * task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked())
                    ++nactive;
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String rs = (runStateLessThan(c, SHUTDOWN) ? "Running" :
                     (runStateAtLeast(c, TERMINATED) ? "Terminated" :
                      "Shutting down"));
        return super.toString() +
            "[" + rs +
            ", pool size = " + nworkers +
            ", active threads = " + nactive +
            ", queued tasks = " + workQueue.size() +
            ", completed tasks = " + ncompleted +
            "]";
    }

    /* Extension hooks */

    /**
     * Method invoked prior to executing the given Runnable in the
     * given thread.  This method is invoked by thread {@code t} that
     * will execute task {@code r}, and may be used to re-initialize
     * ThreadLocals, or to perform logging.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.beforeExecute} at the end of
     * this method.
     *
     * @param t the thread that will run task {@code r}
     * @param r the task that will be executed
     */
    protected void beforeExecute(Thread t, Runnable r) { }

    /**
     * Method invoked upon completion of execution of the given Runnable.
     * This method is invoked by the thread that executed the task. If
     * non-null, the Throwable is the uncaught {@code RuntimeException}
     * or {@code Error} that caused execution to terminate abruptly.
     *
     * <p>This implementation does nothing, but may be customized in
     * subclasses. Note: To properly nest multiple overridings, subclasses
     * should generally invoke {@code super.afterExecute} at the
     * beginning of this method.
     *
     * <p><b>Note:</b> When actions are enclosed in tasks (such as
     * {@link FutureTask}) either explicitly or via methods such as
     * {@code submit}, these task objects catch and maintain
     * computational exceptions, and so they do not cause abrupt
     * termination, and the internal exceptions are <em>not</em>
     * passed to this method. If you would like to trap both kinds of
     * failures in this method, you can further probe for such cases,
     * as in this sample subclass that prints either the direct cause
     * or the underlying exception if a task has been aborted:
     *
     *  <pre> {@code
     * class ExtendedExecutor extends ThreadPoolExecutor {
     *   // ...
     *   protected void afterExecute(Runnable r, Throwable t) {
     *     super.afterExecute(r, t);
     *     if (t == null && r instanceof Future<?>) {
     *       try {
     *         Object result = ((Future<?>) r).get();
     *       } catch (CancellationException ce) {
     *           t = ce;
     *       } catch (ExecutionException ee) {
     *           t = ee.getCause();
     *       } catch (InterruptedException ie) {
     *           Thread.currentThread().interrupt(); // ignore/reset
     *       }
     *     }
     *     if (t != null)
     *       System.out.println(t);
     *   }
     * }}</pre>
     *
     * @param r the runnable that has completed
     * @param t the exception that caused termination, or null if
     * execution completed normally
     */
    protected void afterExecute(Runnable r, Throwable t) { }

    /**
     * Method invoked when the Executor has terminated.  Default
     * implementation does nothing. Note: To properly nest multiple
     * overridings, subclasses should generally invoke
     * {@code super.terminated} within this method.
     */
    protected void terminated() { }

    /* Predefined RejectedExecutionHandlers */

    /**
     * A handler for rejected tasks that runs the rejected task
     * directly in the calling thread of the {@code execute} method,
     * unless the executor has been shut down, in which case the task
     * is discarded.
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code CallerRunsPolicy}.
         */
        public CallerRunsPolicy() { }

        /**
         * Executes task r in the caller's thread, unless the executor
         * has been shut down, in which case the task is discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                r.run();
            }
        }
    }

    /**
     * A handler for rejected tasks that throws a
     * {@code RejectedExecutionException}.
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        /**
         * Creates an {@code AbortPolicy}.
         */
        public AbortPolicy() { }

        /**
         * Always throws RejectedExecutionException.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         * @throws RejectedExecutionException always.
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                                                 " rejected from " +
                                                 e.toString());
        }
    }

    /**
     * A handler for rejected tasks that silently discards the
     * rejected task.
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardPolicy}.
         */
        public DiscardPolicy() { }

        /**
         * Does nothing, which has the effect of discarding task r.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    /**
     * A handler for rejected tasks that discards the oldest unhandled
     * request and then retries {@code execute}, unless the executor
     * is shut down, in which case the task is discarded.
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        /**
         * Creates a {@code DiscardOldestPolicy} for the given executor.
         */
        public DiscardOldestPolicy() { }

        /**
         * Obtains and ignores the next task that the executor
         * would otherwise execute, if one is immediately available,
         * and then retries execution of task r, unless the executor
         * is shut down, in which case task r is instead discarded.
         *
         * @param r the runnable task requested to be executed
         * @param e the executor attempting to execute this task
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}

