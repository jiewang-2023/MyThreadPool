package com.wj.test3;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.lang.Thread.sleep;

/**
 * @author jie
 * @date 2019/9/20 23:06
 */
public class MyThreadPool implements ThreadPool {

    //最大接收任务
    private static final int TASK_MAX_SIZE = 16;
    //任务队列容器
    private final BlockingQueue<Runnable> tasks;
    /**
     * 线程集合
     * Collections.synchronizedList()
     * 返回由指定列表支持的同步（线程安全）列表。 为了保证串行访问，重要的是通过返回的列表完成对后台列表的所有访问。
     * 在迭代时，用户必须在返回的列表上手动同步
     */
    private final List<Worker> workers;
    //线程池是否销毁
    private volatile boolean destory = false;
    //最大线程
    private int maxSize;
    //核心线程数
    private volatile int corePoolSize;
    //活跃线程
    private volatile int activeSize;
    //线程空闲时间
    private long keepAliveTime;
    //维护线程池的线程
    private Thread thread;
    //策略模式
    private MyRejectionStrategy handler = new MyAbortPolicy();


    public MyThreadPool() {
        //偷个懒...
        this(5, 10, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>(64));
    }

    public MyThreadPool(int corePoolSize, int maxSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> tasks) {
        if (corePoolSize < 1 || maxSize < corePoolSize || keepAliveTime < 0) {
            throw new IllegalArgumentException("请输入正确的参数");
        }
        Objects.requireNonNull(tasks);

        this.corePoolSize = corePoolSize;
        this.maxSize = maxSize;
        this.keepAliveTime = unit.toMillis(keepAliveTime);
        this.tasks = tasks;
        workers = Collections.synchronizedList(new ArrayList<>(maxSize));
        initThreadPool();
    }

    /**
     * 调用给定任务的拒绝执行处理程序
     *
     * @param task
     */
    final void reject(Runnable task) {
        handler.rejectedExecution(task, this);
    }

    /**
     * 初始化线程池
     */
    private void initThreadPool() {
        IntStream.range(0, corePoolSize).forEach(a -> {
            this.createWorker(true);
        });
        this.activeSize = this.corePoolSize;
        thread = new Thread(this::run);
        thread.start();
        Object[] coreIds = this.workers.stream().map(Worker::getId).toArray();
        System.out.println("线程池初始化完毕...  初始化核心线程 = " + Arrays.toString(coreIds));
    }

    /**
     * 创建工作线程
     */
    private void createWorker(boolean isCore) {
        Worker worker = new Worker(isCore);
        workers.add(worker);
        worker.startWorker();
        this.activeSize++;
    }

    /**
     * 减少非核心线程
     */
    private void reduceThread() {
        //   防止线程在submit的时候，其他线程获取到锁
        synchronized (workers) {
            int reSize = activeSize - corePoolSize;
            Iterator<Worker> iter = workers.iterator();
//          ArrayList 不能在迭代中移除元素，必须使用迭代器的remove()
            while (iter.hasNext()) {
                if (reSize <= 0) {
                    break;
                }
                Worker next = iter.next();
                if (!next.isCore) {
                    next.stopWorker();
                    iter.remove();
                    reSize--;
                    activeSize--;
                    System.out.println("被回收的线程id : " + next.getId());
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
        while (!destory) {
            try {
                sleep(5000L);
//                扩容线程
                if (activeSize < maxSize && (tasks.size() > activeSize || tasks.size() > maxSize)) {
                    for (int i = activeSize; i < maxSize; i++) {
                        createWorker(false);
                    }
                    this.activeSize = maxSize;
                    System.err.println("扩容了..." + this);
//                    没任务，活跃线程大于5  减少线程
                } else if (tasks.size() == 0 && activeSize > 5) {
                    System.err.println("线程空闲ing...");
                    sleep(this.keepAliveTime);
                    reduceThread();
                }
            } catch (InterruptedException e) {
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
    public void submit(Runnable task) {
        if (destory) {
            throw new IllegalStateException("线程池已关闭...");
        }
//      共用tasks这把锁，添加和取任务不能同时进行
        synchronized (tasks) {
            // System.out.println("加任务 = " + Thread.currentThread().getName()+Thread.currentThread().getState());
//            wait把锁让给worker去执行任务
            if (tasks.size() == TASK_MAX_SIZE) {
                try {
                    sleep(6000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
//            线程池满了，任务超了执行相应的策略
            if (tasks.size() > TASK_MAX_SIZE) {
                System.err.println("超了..." + "当前活跃线程数：" + activeSize + "  当前任务数" + tasks.size());
                reject(task); //
            } else {
                //添加任务
                tasks.offer(task);
            }
//            唤醒等待的线程执行任务
            tasks.notifyAll();
        }
    }

    @Override
    public boolean isShutdown() {
        return destory;
    }

    @Override
    public void shutdown() {
//        还有任务，睡一会...
        while (!tasks.isEmpty()) {
            try {
                sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        workers.forEach(Worker::stopWorker);
        this.destory = true;
        tasks.clear();
        thread.interrupt();
        System.out.println("关闭线程池...");
        System.exit(0);
    }

    @Override
    public String toString() {
        return "MyThreadPool{" +
                " taskSize=" + tasks.size() +
                ", maxSize=" + maxSize +
                ", corePoolSize=" + corePoolSize +
                ", activeSize=" + activeSize +
                '}';
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
                pool.tasks.poll();
                pool.submit(task);
            }
        }
    }

    /**
     * 工作线程
     */
    private final class Worker extends Thread {
        //        标记是否是核心线程，核心线程不销毁
        private boolean isCore = false;

        public Worker(boolean isCore) {
            this.isCore = isCore;
        }

        @Override
        public void run() {
            Runnable task;
            OUTER:
            while (!this.isInterrupted()) {
//                  共用tasks这把锁，添加和取任务不能同时进行
                synchronized (tasks) {
                    // System.out.println("取任务 = " + getName()+getState());
                    while (tasks.isEmpty()) {
                        try {
//                          没任务 wait等待任务
                            tasks.wait();

//                           如果被打断，说明当前线程执行了 interrupt()方法，清除中断状态
                        } catch (InterruptedException e) {
                            break OUTER;
                        }
                    }
                    //取任务， pool内部有锁
                    task = tasks.poll();
                }
                if (Objects.nonNull(task)) {
//                       跑任务
                    task.run();
                }
            }
        }

        void startWorker() {
            this.start();
        }

        void stopWorker() {
            // System.out.println("this.getState() = " + this.getState());
            this.interrupt();
        }
    }
}
