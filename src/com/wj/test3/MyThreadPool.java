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
    /**
     * 拒绝策略
     * AbortPolicy 抛异常，不再添加任务
     * ((task, pool) -> { throw new RejectedExecutionException("Task " + task + " rejected from " + pool); });
     *
     * DiscardPolicy 直接丢任务什么都不做
     * ((task,pool)->{});
     *
     * DiscardOldestPolicy  移除队头元素后在尝试入队
     */
    private MyRejectionStrategy myRejection;
    //线程池是否销毁
    private volatile boolean destory = false;

    private int maxSize; //最大线程
    private volatile int corePoolSize;  //核心线程数
    private volatile int activeSize; //活跃线程
    //维护线程池的线程
    private Thread thread;
    /**
     * 线程空闲时间
     */
    private long keepAliveTime;
    /**
     * 任务队列容器
     */
    private BlockingQueue<Runnable> tasks;
    /**
     * 线程集合
     * Collections.synchronizedList()
     * 返回由指定列表支持的同步（线程安全）列表。 为了保证串行访问，重要的是通过返回的列表完成对后台列表的所有访问。
     * 在迭代时，用户必须在返回的列表上手动同步
     */
    private final List<Worker> workers;

    public MyThreadPool() {
        //偷个懒...
        this(5,10,1,TimeUnit.SECONDS,new LinkedBlockingQueue<>(64));
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
//        替换策略
        this.myRejection =  ((task,pool) ->{
            if (!this.destory){
                synchronized (tasks) {
                    pool.tasks.poll();
                    pool.submit(task);
                }
            }
        });
        initPool();
    }

    /**
     * 维护线程池
     */
    private void run() {
        while (!destory) {
            try {
                sleep(3000L);
//                扩容线程
                if (activeSize < maxSize && (tasks.size() > activeSize || tasks.size() > maxSize)) {
                    for (int i = activeSize; i <= maxSize; i++) {
                        createWorker();
                    }
                    this.activeSize = maxSize;
                    System.err.println("扩容了..." + this);
                } else if (tasks.size() == 0 && activeSize > 5) {
                    System.err.println("线程空闲ing...");
                    sleep(this.keepAliveTime);
//                    防止线程在submit的时候，其他线程获取到锁
                    synchronized (workers) {
                        int reSize = activeSize - corePoolSize;
                        Iterator<Worker> iter = workers.iterator();
//                        ArrayList 不能在迭代中移除元素，必须使用迭代器的remove()
                        while (iter.hasNext()) {
                            if (reSize <= 0) {
                                break;
                            }
                            Worker next = iter.next();
                            next.stopWorker();
                            iter.remove();
                            reSize--;
                            activeSize--;
                        }
                        System.err.println("回收线程：" + this);
                    }

                }
            } catch (InterruptedException e) {
                System.out.println("释放资源...");
            }
        }
    }

    /**
     * 初始化线程池
     */
    private void initPool() {
        for (int i = 0; i < this.corePoolSize; i++) {
            this.createWorker();
        }
        this.activeSize = this.corePoolSize;
        thread = new Thread(this::run);
        thread.start();
    }

    /**
     * 创建工作线程
     */
    private void createWorker() {
        Worker worker = new Worker();
        workers.add(worker);
        worker.startWorker();
        this.activeSize++;
    }

    /**主线程，上来就加任务，
     * @param task
     */
    @Override
    public void submit(Runnable task) {
        if (destory) {
            throw new IllegalStateException("线程池已关闭...");
        }
//     offer和poll操作共用同一把锁，不能同时进行
        synchronized (tasks) {
           // System.out.println("加任务 = " + Thread.currentThread().getName()+Thread.currentThread().getState());

//            给3秒时间去扩容线程，保证线程池已满
            if (tasks.size() == TASK_MAX_SIZE){
                try {
                    sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
//            线程池满了，任务超了执行相应的策略
            if (tasks.size() == TASK_MAX_SIZE) {
//              todo  使用替换策略的话就换等于  如果是大于的话移除队头，再添加队尾还是超过的...
                myRejection.rejectedExecution(task,this);
                System.err.println("超了..."+"当前活跃线程数："+activeSize+"  当前任务数"+tasks.size());
            }
            tasks.offer(task);
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
        Iterator<Worker> iterator = workers.iterator();
        iterator.next();
        iterator.remove();
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
     * 工作线程
     */
    private final class Worker extends Thread {
        @Override
        public void run() {
            Runnable task;
            OUTER:
            while (!this.isInterrupted()) {
//                 offer和poll操作共用同一把锁，不能同时进行
                synchronized (tasks) {
                   // System.out.println("取任务 = " + getName()+getState());
                    while (tasks.isEmpty()) {
                        try {
//                          任务执行完了wait
                            tasks.wait();
//                             如果被打断，说明当前线程执行shutdown方法
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
                    // help gc
                    task = null;
                }
            }
        }

        /**
         * 启动线程
         */
        void startWorker() {
            this.start();
        }

        /**
         * 关闭线程
         */
        void stopWorker() {
            System.out.println("this.getState() = " + this.getState());
            this.interrupt();
        }
    }


}
