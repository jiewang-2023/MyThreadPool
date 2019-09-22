package com.wj.test3;

/**
 * @author jie
 * @date 2019/9/20 22:36
 */
public interface ThreadPool {
    /**
     * 提交任务
     *
     * @param task
     */
    void submit(Runnable task);

    /**
     * 线程池是否关闭
     *
     * @return
     */
    boolean isShutdown();

    /**
     * 关闭线程池
     */
    void shutdown();



}
