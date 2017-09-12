/*
 * @(#)FutureTask.java	1.7 04/04/15
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

import java.util.concurrent.locks.*;

/**
 * A cancellable asynchronous computation.  This class provides a base
 * implementation of {@link Future}, with methods to start and cancel
 * a computation, query to see if the computation is complete, and
 * retrieve the result of the computation.  The result can only be
 * retrieved when the computation has completed; the <tt>get</tt>       //get方法会一直阻塞到任务有结果了
 * method will block if the computation has not yet completed.  Once
 * the computation has completed, the computation cannot be restarted   //任务结束了,就不能重启或者取消了
 * or cancelled.
 * <p/>
 * <p>A <tt>FutureTask</tt> can be used to wrap a {@link Callable} or
 * {@link java.lang.Runnable} object.  Because <tt>FutureTask</tt>
 * implements <tt>Runnable</tt>, a <tt>FutureTask</tt> can be
 * submitted to an {@link Executor} for execution.
 * <p/>
 * <p>In addition to serving as a standalone class, this class provides
 * <tt>protected</tt> functionality that may be useful when creating
 * customized task classes.
 *
 * @param <V> The result type returned by this FutureTask's <tt>get</tt> method
 * @author Doug Lea
 * @since 1.5
 */
public class FutureTask<V> implements Future<V>, Runnable {
    /**
     * Synchronization control for FutureTask
     */
    private final Sync sync;

    /**
     * Creates a <tt>FutureTask</tt> that will upon running, execute the
     * given <tt>Callable</tt>.
     *
     * @param callable the callable task
     * @throws NullPointerException if callable is null
     */
    public FutureTask(Callable<V> callable) {
        if (callable == null)
            throw new NullPointerException();
        sync = new Sync(callable);  //设置回调方法
    }

    /**
     * Creates a <tt>FutureTask</tt> that will upon running, execute the
     * given <tt>Runnable</tt>, and arrange that <tt>get</tt> will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result   the result to return on successful completion. If
     *                 you don't need a particular result, consider using
     *                 constructions of the form:
     *                 <tt>Future&lt;?&gt; f = new FutureTask&lt;Object&gt;(runnable, null)</tt>
     * @throws NullPointerException if runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        sync = new Sync(Executors.callable(runnable, result));
    }

    public boolean isCancelled() {
        return sync.innerIsCancelled();
    }

    public boolean isDone() {
        return sync.innerIsDone();
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return sync.innerCancel(mayInterruptIfRunning);
    }

    public V get() throws InterruptedException, ExecutionException {
        return sync.innerGet();
    }

    public V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return sync.innerGet(unit.toNanos(timeout));
    }

    /**
     * Protected method invoked when this task transitions to state
     * <tt>isDone</tt> (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() {
    }

    /**
     * Sets the result of this Future to the given value unless
     * this future has already been set or has been cancelled.
     *
     * @param v the value
     */
    protected void set(V v) {
        sync.innerSet(v);
    }

    /**
     * Causes this future to report an <tt>ExecutionException</tt>
     * with the given throwable as its cause, unless this Future has
     * already been set or has been cancelled.
     *
     * @param t the cause of failure.
     */
    protected void setException(Throwable t) {
        sync.innerSetException(t);
    }

    /**
     * Sets this Future to the result of computation unless
     * it has been cancelled.
     */
    public void run() {
        sync.innerRun();    //调用sync的run方法
    }

    /**
     * Executes the computation without setting its result, and then
     * resets this Future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled.  This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     *
     * @return true if successfully run and reset
     */
    protected boolean runAndReset() {
        return sync.innerRunAndReset();
    }

    /**
     * Synchronization control for FutureTask. Note that this must be
     * a non-static inner class in order to invoke the protected        //非static的类,用于调用对象里面的done方法
     * <tt>done</tt> method. For clarity, all inner class support
     * methods are same as outer, prefixed with "inner".
     * <p/>
     * Uses AQS sync state to represent run status
     */
    private final class Sync extends AbstractQueuedSynchronizer {
        /**
         * State value representing that task is running
         */
        private static final int RUNNING = 1;
        /**
         * State value representing that task ran
         */
        private static final int RAN = 2;
        /**
         * State value representing that task was cancelled
         */
        private static final int CANCELLED = 4;

        /**
         * The underlying callable
         */
        private final Callable<V> callable;
        /**
         * The result to return from get()
         */
        private V result;
        /**
         * The exception to throw from get()
         */
        private Throwable exception;

        /**
         * The thread running task. When nulled after set/cancel, this
         * indicates that the results are accessible.  Must be
         * volatile, to ensure visibility upon completion.
         */
        private volatile Thread runner;

        Sync(Callable<V> callable) {
            this.callable = callable;
        }

        private boolean ranOrCancelled(int state) {
            return (state & (RAN | CANCELLED)) != 0;
        }

        /**
         * Implements AQS base acquire to succeed if ran or cancelled
         */
        protected int tryAcquireShared(int ignore) {
            return innerIsDone() ? 1 : -1;
        }

        /**
         * Implements AQS base release to always signal after setting
         * final done status by nulling runner thread.
         */
        protected boolean tryReleaseShared(int ignore) {
            runner = null;
            return true;
        }

        boolean innerIsCancelled() {
            return getState() == CANCELLED;
        }

        boolean innerIsDone() {
            return ranOrCancelled(getState()) && runner == null;
        }

        V innerGet() throws InterruptedException, ExecutionException {
            acquireSharedInterruptibly(0);  //获取共享锁,还没有结果时会阻塞
            if (getState() == CANCELLED)
                throw new CancellationException();
            if (exception != null)
                throw new ExecutionException(exception);
            return result;
        }

        V innerGet(long nanosTimeout) throws InterruptedException, ExecutionException, TimeoutException {
            if (!tryAcquireSharedNanos(0, nanosTimeout))    //获取共享锁,还没有结果时会阻塞,有超时时间
                throw new TimeoutException();
            if (getState() == CANCELLED)
                throw new CancellationException();
            if (exception != null)
                throw new ExecutionException(exception);
            return result;
        }

        void innerSet(V v) {
            for (; ; ) {
                int s = getState();
                if (ranOrCancelled(s))
                    return;
                if (compareAndSetState(s, RAN))
                    break;
            }
            result = v;
            releaseShared(0);   // 给线程一个许可,等待获取任务结果的线程就会被唤醒;
            done();
        }

        /*
         * 任务还没结束,等待获取任务结果的线程会被挂起,挂起的线程不占cpu,会释放当前占有的锁
         * sleep是阻塞,仍然会占有对象的锁.
         */

        void innerSetException(Throwable t) {
            for (; ; ) {
                int s = getState();
                if (ranOrCancelled(s))
                    return;
                if (compareAndSetState(s, RAN))
                    break;
            }
            exception = t;
            result = null;
            releaseShared(0);
            done();
        }

        boolean innerCancel(boolean mayInterruptIfRunning) {
            for (; ; ) {
                int s = getState();
                if (ranOrCancelled(s))
                    return false;
                if (compareAndSetState(s, CANCELLED))
                    break;
            }
            if (mayInterruptIfRunning) {
                Thread r = runner;
                if (r != null)
                    r.interrupt();
            }
            releaseShared(0);
            done();
            return true;
        }

        void innerRun() {
            if (!compareAndSetState(0, RUNNING))
                return;
            try {
                runner = Thread.currentThread();
                innerSet(callable.call());  //设置callable的回调结果
            } catch (Throwable ex) {
                innerSetException(ex);
            }
        }

        boolean innerRunAndReset() {
            if (!compareAndSetState(0, RUNNING))
                return false;
            try {
                runner = Thread.currentThread();
                callable.call(); // don't set result
                runner = null;
                return compareAndSetState(RUNNING, 0);
            } catch (Throwable ex) {
                innerSetException(ex);
                return false;
            }
        }
    }
}
