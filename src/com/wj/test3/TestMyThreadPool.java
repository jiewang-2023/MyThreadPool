package com.wj.test3;

import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * @author jie
 * @date 2019/9/21 12:28
 */
public class TestMyThreadPool {
    public static void main(String[] args) throws InterruptedException {
        MyThreadPool myThreadPool =
                new MyThreadPool(5, 10, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>(32));

        IntStream.range(0, 48).forEach(i ->
                myThreadPool.submit(() -> {
                    try {
                    System.out.printf("[线程] - [%s] 开始工作...\n", Thread.currentThread().getId());
                        Thread.sleep(3000);
                    System.out.printf("[线程] - [%s] 工作完毕...\n", Thread.currentThread().getId());
                    } catch (InterruptedException e) {}
                })
        );
        myThreadPool.shutdown();
    }


}
