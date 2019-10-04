package com.wj.test3;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.lang.Thread.sleep;

/**
 * @author jie
 * @date 2019/9/20 23:06
 */
public class MyThreadPool implements ThreadPool {

    /**
     * 任务队列容器   任务数最大值就是容器的大小
     */
    private final BlockingQueue<Runnable> tasks;
    /**
     * 工作线程集合
     */
    private final List<Worker> workers;
    /**
     * 核心线程数
     */
    private int corePoolSize;
    /**
     * 最大线程数
     */
    private int maxSize;
    /**
     * 当前线程数
     * volatile 可见性【一个线程修改了某个变量值，新值对其他线程是立即可见的】，有序性【禁止进行指令重排序】 单次读写原子性，不能保证i++
     * AtomicInteger 保证原子性 多个线程修改同一个变量时，只有一个能成功，其他的线程会向自旋锁一样一直尝试，直到成功
     */
    private volatile AtomicInteger activeSize = new AtomicInteger(0);
    /**
     * 线程空闲时间
     */
    private long keepAliveTime;
    /**
     * 线程池是否销毁
     */
    private volatile boolean destroy = false;
    /**
     * 维护线程池的线程
     */
    private Thread thread;
    /**
     * 丢弃策略
     */
    private MyRejectionStrategy handler;

    public MyThreadPool() {
        //偷个懒...
        this(5, 10, 1, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(16), new MyDiscardOldestPolicy());
    }

    public MyThreadPool(int corePoolSize, int maxSize, long keepAliveTime,
                        TimeUnit unit, BlockingQueue<Runnable> tasks, MyRejectionStrategy handler) {
//        校验参数是否正确
        if (corePoolSize < 1 || maxSize < corePoolSize || keepAliveTime < 0) {
            throw new IllegalArgumentException("请输入正确的参数...");
        }
        if (tasks == null || handler == null) {
            throw new NullPointerException();
        }
        this.corePoolSize = corePoolSize;
        this.maxSize = maxSize;
        //将时间转为以毫秒为单位
        this.keepAliveTime = unit.toMillis(keepAliveTime);
        this.tasks = tasks;
        /**
         * Collections.synchronizedList()
         * 返回由指定列表支持的同步（线程安全）列表。 为了保证串行访问，重要的是通过返回的列表完成对后台列表的所有访问。
         * 原子操作不需要同步
         * 非原子操作需要手动同步
         * 在迭代时，必须在返回的列表上手动同步
         * 多线程环境下迭代时，iterator.hasNext()里还有元素，
         * 调用iterator.next()的时候 其他线程也会进来调用next()方法所有必须手动同步
         */
        this.workers = Collections.synchronizedList(new ArrayList<>(maxSize));
        this.handler = handler;
        //初始化线程池
        initThreadPool();
    }

    /**
     * 初始化线程池
     */
    private void initThreadPool() {
//        创建核心线程,
        IntStream.range(0, corePoolSize).forEach(a -> this.createWorker(true));
        //创建维护线程池的线程，
        thread = new Thread(this::run);
        thread.start();
        //记录下核心线程的id
        Object[] coreIds = this.workers.stream().map(Worker::getId).toArray();
        System.out.println("线程池初始化完毕...  初始化核心线程id: = " + Arrays.toString(coreIds));
    }

    /**
     * 创建工作线程
     *
     * @param isCore isCore 标记是否是核心线程
     */
    private void createWorker(boolean isCore) {
        Worker worker = new Worker(isCore);
        workers.add(worker);
        worker.startWorker();
//        +1
        this.activeSize.getAndIncrement();
    }

    /**
     * 减少工作线程，保留核心线程
     */
    private void reduceThread() {
        Iterator<Worker> iter = workers.iterator();
//        手动同步，  这里不加锁是因为这里只有一条线程操作workers
        // synchronized (workers) {
        while (iter.hasNext()) {
            // System.out.println("执行移除的线程 = " + Thread.currentThread().getName());
            Worker next = iter.next();
            if (!next.isCore) {
                next.stopWorker();
                iter.remove();
                this.activeSize.getAndDecrement();
            }
        }
        //  }

        System.err.println("回收线程：" + this);
        Object[] ids = this.workers.stream().map(Worker::getId).toArray();
        System.out.println("保留的核心线程 = " + Arrays.toString(ids));
    }

    /**
     * 维护线程池
     */
    private void run() {
        //判断线程池是否已关闭
        while (!destroy) {
            try {
//                轮询，延迟启动保证工作线程先开始工作，
                sleep(1500L);
//                扩容线程  线程池还没满 && 任务队列不为空  tasks.size() > corePoolSize
                if (activeSize.get() < maxSize && !tasks.isEmpty()) {
//                      //扩容到最大线程数
                    IntStream.range(activeSize.get(), maxSize).forEach(a -> createWorker(false));
                    System.err.println("已扩容：" + this + " " + "  当前任务队列大小：" + tasks.size());

                }   // 没任务 && 当前线程大于corePoolSize  减少线程
                else if (tasks.isEmpty() && activeSize.get() > corePoolSize) {
//                  保证正在执行的都已执行完毕
                    sleep(keepAliveTime);

                    reduceThread();
                }
            } catch (InterruptedException e) {
                /**
                 * interrupt() 方法只是改变中断状态而已，它不会中断一个正在运行的线程。
                 *   如果线程的当前状态处于非阻塞状态，那么仅仅是线程的中断标志被修改为true而已；
                 *   如果线程被Object.wait, Thread.join和Thread.sleep三种方法之一阻塞，
                 *   此时调用该线程的interrupt()方法，那么该线程将抛出一个 InterruptedException中断异常。
                 */
                System.out.println("释放资源...");
            }
        }
    }

    /**
     * 提交任务
     *
     * @param task 任务
     */
    @Override
    public void execute(Runnable task) {
        if (destroy) {
            throw new IllegalStateException("线程池已关闭......");
        }
        Objects.requireNonNull(task, "请勿提交空任务...");
        System.out.println(Thread.currentThread().getName() + "提交任务");
        boolean offer = tasks.offer(task);
        if (!offer) {
            reject(task);
        }
        try {
            //提交任务慢点
            sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
//            唤醒等待的线程执行任务
    }

    /**
     * 判断线程池是否关闭
     *
     * @return true已关闭，false未关闭
     */
    @Override
    public boolean isShutdown() {
        return destroy;
    }

    /**
     * 关闭线程池
     */
    @Override
    public void shutdown() {
        try {
//        还有任务，睡一会...
            while (!tasks.isEmpty()) {
                sleep(100);
            }
//          为了显示线程回收效果， 让维护线程去回收线程
            sleep(6000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
//        直到所有线程结束
        while (workers.stream().filter(w -> w.getState() == Thread.State.TERMINATED).count() < workers.size()) {
            workers.forEach(w -> {
                if (w.getState() == Thread.State.WAITING) {
                    w.flag.set(false);
                    w.stopWorker();
                }
            });
        }
        //将线程池状态设置为关闭
        this.destroy = true;

        //打断维护线程池的线程
        thread.interrupt();
        //资源回收
        tasks.clear();
        workers.clear();
        System.out.println("关闭线程池...");
    }

    @Override
    public String toString() {
        return "MyThreadPool{" +
                " taskSize=" + tasks.size() +
                ", maxSize=" + maxSize +
                ", corePoolSize=" + corePoolSize +
                ", activeSize=" + activeSize.get() +
                '}';
    }

    /**
     * 调用给定任务的拒绝执行处理程序
     *
     * @param task 任务
     */
    final void reject(Runnable task) {
        handler.rejectedExecution(task, this);
    }


    /**
     * MyAbortPolicy 抛异常，不再添加任务
     */
    static class MyAbortPolicy implements MyRejectionStrategy {
        @Override
        public void rejectedExecution(Runnable task, MyThreadPool pool) {
            throw new RejectedExecutionException("Task " + task + " rejected from " + pool);
        }
    }

    /**
     * MyDiscardOldestPolicy 移除队头，尝试将任务加入队尾
     */
    static class MyDiscardOldestPolicy implements MyRejectionStrategy {
        @Override
        public void rejectedExecution(Runnable task, MyThreadPool pool) {
//           线程池是否关闭...
            if (!pool.destroy) {
                System.out.println("策略移除前" + pool.tasks.size());
//                移除队头
                pool.tasks.poll();
//                再次提交任务
                pool.execute(task);
                System.out.println("策略移除后" + pool.tasks.size());
            }
        }
    }

    /**
     * 工作线程
     */
    private final class Worker extends Thread {
        //标记是否是核心线程，核心线程不销毁
        private boolean isCore;
//        true启用线程 false关闭线程
          volatile AtomicBoolean flag = new AtomicBoolean(true);

        /**
         * @param isCore true 核心线程，  false 扩容的线程
         */
        public Worker(boolean isCore) {
            this.isCore = isCore;
        }

        @Override
        public void run() {
            Runnable task = null;

            while (!this.isInterrupted()) {
                try {
                    task = isCore ? tasks.take() : tasks.poll(keepAliveTime, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (isCore && !flag.get()) {
                        System.exit(0);
                    }
                    System.out.println(Thread.currentThread().getId() + "  =  " + Thread.currentThread().getState());
                }
                if (Objects.nonNull(task)) {
                    task.run();
//                    help gc
                    task = null;
                }
            }
        }

        void startWorker() {
            this.start();
        }

        void stopWorker() {
            this.interrupt();
        }
    }
}
