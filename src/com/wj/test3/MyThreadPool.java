package com.wj.test3;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.lang.Thread.sleep;

/**
 * @author jie
 * @date 2019/9/20 23:06
 * ━━━━━━神兽出没━━━━━━
 * 　　　┏┓　　　┏┓
 * 　　┏┛┻━━━┛┻┓
 * 　　┃　　　　　　　┃
 * 　　┃　　　━　　　┃
 * 　　┃　┳┛　┗┳　┃
 * 　　┃　　　　　　　┃
 * 　　┃　　　┻　　　┃
 * 　　┃　　　　　　　┃
 * 　　┗━┓　　　┏━┛
 * 　　　　┃　　　┃神兽保佑, 永无BUG!
 * 　　　　 ┃　　　┃Code is far away from bug with the animal protecting
 * 　　　　┃　　　┗━━━┓
 * 　　　　┃　　　　　　　┣┓
 * 　　　　┃　　　　　　　┏┛
 * 　　　　┗┓┓┏━┳┓┏┛
 * 　　　　　┃┫┫　┃┫┫
 * 　　　　　┗┻┛　┗┻┛
 */
public class MyThreadPool implements ThreadPool {

    //任务队列容器   任务数最大值就是容器的大小
    private final BlockingQueue<Runnable> tasks;
    //工作线程集合
    private final List<Worker> workers;
    //核心线程数
    private volatile int corePoolSize;
    //最大线程数
    private int maxSize;
    /**
     * 当前线程数
     * volatile 可见性【一个线程修改了某个变量值，新值对其他线程是立即可见的】，有序性【禁止进行指令重排序】 单次读写原子性，不能保证i++
     * AtomicInteger 保证原子性
     */
    private volatile AtomicInteger activeSize = new AtomicInteger(0);
    //线程空闲时间
    private long keepAliveTime;
    //线程池是否销毁
    private volatile boolean destory = false;
    //维护线程池的线程
    private Thread thread;
    //初始化丢弃策略
    private MyRejectionStrategy handler = new MyDiscardOldestPolicy();

    public MyThreadPool() {
        //偷个懒...
        this(5, 10, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>(16));
    }

    public MyThreadPool(int corePoolSize, int maxSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> tasks) {
//        校验参数是否正确
        if (corePoolSize < 1 || maxSize < corePoolSize || keepAliveTime < 0) {
            throw new IllegalArgumentException("请输入正确的参数...");
        }
        Objects.requireNonNull(tasks);

        this.corePoolSize = corePoolSize;
        this.maxSize = maxSize;
        //将时间转为以毫秒为单位
        this.keepAliveTime = unit.toMillis(keepAliveTime);
        this.tasks = tasks;
        /**
         * Collections.synchronizedList()
         * 返回由指定列表支持的同步（线程安全）列表。 为了保证串行访问，重要的是通过返回的列表完成对后台列表的所有访问。
         * 在迭代时，必须在返回的列表上手动同步
         */
        workers = Collections.synchronizedList(new ArrayList<>(maxSize));
        //初始化线程池
        initThreadPool();
    }


    /**
     * 初始化线程池
     */
    private void initThreadPool() {
//        创建核心线程
        IntStream.range(0, corePoolSize).forEach(a -> this.createWorker(true));
        //创建维护线程池的线程
        thread = new Thread(this::run);
        thread.start();
        //记录下核心线程的id
        Object[] coreIds = this.workers.stream().map(Worker::getId).toArray();
        System.out.println("线程池初始化完毕...  初始化核心线程id: = " + Arrays.toString(coreIds));
    }

    /**
     * 创建工作线程
     *
     * @param isCore isCore 标记是否是核心线程， isTasked 标记是否是新的线程
     */
    private void createWorker(boolean isCore) {
        Worker worker = new Worker(isCore, true);
        workers.add(worker);
        worker.startWorker();
//        +1
        this.activeSize.getAndIncrement();
    }

    /**
     * 减少工作线程，保留核心线程
     */
    private void reduceThread() {
//        手动同步
        synchronized (workers) {

            //  保留 corePool
            Iterator<Worker> iter = workers.iterator();
            while (activeSize.get() > corePoolSize) {
                if (iter.hasNext()) {
                    Worker next = iter.next();
                    //判断是不是核心线程
                    if (!next.isCore && next.getState() == Thread.State.WAITING) {
                        next.stopWorker();
                        iter.remove();
//                    -1
                        activeSize.getAndDecrement();
                    }
                } else {
                    iter = workers.iterator();
                }

            }
            System.err.println("回收线程：" + this);
            Object[] ids = this.workers.stream().map(Worker::getId).toArray();
            System.out.println("保留的核心线程 = " + Arrays.toString(ids));
        }
    }

    /**
     * 维护线程池
     */
    private void run() {
        //判断线程池是否已关闭
        while (!destory) {
            // System.out.println("轮询的线程："+Thread.currentThread().getName());
            try {
                //每3秒轮询一次
                sleep(3000L);
//                扩容线程  线程池还没满 && 任务队列size 大于 当前线程数
                if (activeSize.get() < maxSize && tasks.size() > activeSize.get()) {
//                      //扩容到最大线程数
                    IntStream.range(activeSize.get(), maxSize).forEach(a -> createWorker(false));
                    System.err.println("已扩容：" + this + " " + "  当前任务队列大小：" + tasks.size());

                }   // 没任务 && 当前线程大于5  减少线程
                else if (tasks.size() == 0 && activeSize.get() > corePoolSize) {

                    System.err.println("线程空闲ing...");
                    // 睡 N 秒
                    sleep(this.keepAliveTime);
                    //清除线程    没任务清除
                    if (tasks.size() < 1) {
                        reduceThread();
                    } else {
                        System.out.println("清除线程失败, 来任务了...");
                    }
                }
            } catch (InterruptedException e) {
//           interrupt() 方法只是改变中断状态而已，它不会中断一个正在运行的线程。
//           如果线程被Object.wait, Thread.join和Thread.sleep三种方法之一阻塞，此时调用该线程的interrupt()方法，那么该线程将抛出一个 InterruptedException中断异常。
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
        if (destory) {
            throw new IllegalStateException("线程池已关闭...");
        }
//        notifyAll()     java.lang.IllegalMonitorStateException
        synchronized (tasks) {
            //   System.out.println("加任务的线程： " + Thread.currentThread().getName());

       /*     if (tasks.size() > 16) {
                reject(task);
            }else {
            boolean offer = tasks.offer(task);
            }*/

            //添加任务，如果队列已满添加失败返回false
            boolean offer = tasks.offer(task);
            if (!offer) {
                reject(task);
            }
            try {
//                睡一小会为了让加任务慢点...
                sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
//            唤醒等待的线程执行任务
            tasks.notifyAll();
        }
    }

    /**
     * 判断线程池是否关闭
     *
     * @return true已关闭，false未关闭
     */
    @Override
    public boolean isShutdown() {
        return destory;
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

         //   保证任务都已执行完毕
            sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

//        直到所有线程结束
        while (workers.stream().filter(w -> w.getState() == Thread.State.TERMINATED).count() < workers.size()) {
            workers.forEach(w -> {
                if (w.getState() == Thread.State.WAITING) {
                    w.stopWorker();
                }
            });
        }
        //将线程池状态设置为关闭
        this.destory = true;
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
            if (!pool.destory) {
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
        // 标记是否已执行过任务
        private volatile boolean isTasked;

        /**
         * @param isCore   true 核心线程，  false 扩容的线程
         * @param isTasked true 已执行任务, false 未执行任务
         */
        public Worker(boolean isCore, boolean isTasked) {
            this.isCore = isCore;
            this.isTasked = isTasked;
        }

        @Override
        public void run() {
            Runnable task;
            OUTER:
            // 当前线程中断返回true，未被中断返回false
            while (!this.isInterrupted()) {
//                  wait()方法必须在同步代码块或同步方法中调用，否则会抛 java.lang.IllegalMonitorStateException
                synchronized (tasks) {
                    while (tasks.isEmpty() && this.isTasked) {
                        try {
//                          没任务 wait等待任务
                            tasks.wait();
                        } catch (InterruptedException e) {
                            break OUTER;
                        }
                    }
                    //取任务， pool内部有锁
                    task = tasks.poll();
                    //System.out.println("取任务的线程 = " + Thread.currentThread().getName());
                }
                if (Objects.nonNull(task)) {
//                   跑任务
                    task.run();
                    this.isTasked = true;
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
