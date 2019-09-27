package com.wj.test3;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * @author jie
 * @date 2019/9/21 12:28
 */
public class TestMyThreadPool {
    public static void main(String[] args) throws InterruptedException {
        MyThreadPool myThreadPool =
                new MyThreadPool(5, 10, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>(16));

        IntStream.range(0, 48).forEach(i ->
                myThreadPool.execute(() -> {
                    try {
                        System.out.println(Thread.currentThread().getId() + "线程 开始工作...");
                        Thread.sleep(3000);
                        System.out.println(Thread.currentThread().getId() + "线程 结束工作......");
                    } catch (InterruptedException e) {
                    }
                })
        );
         myThreadPool.shutdown();  //关闭线程池
    }


}
