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

        IntStream.range(0, 64).forEach(i ->
                myThreadPool.submit(() -> {
                    System.out.printf("[线程] - [%s] 开始工作...\n", Thread.currentThread().getName());
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException e) {
                        //  e.printStackTrace();
                    }
                    System.out.printf("[线程] - [%s] 工作完毕...\n", Thread.currentThread().getName());
                    System.out.println("Main ：" + myThreadPool.toString());
                })
        );
        // myThreadPool.shutdown();
    }


}
