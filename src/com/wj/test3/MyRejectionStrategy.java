package com.wj.test3;

/**
 * @author jie
 * @date 2019/9/21 10:31
 * 拒绝策略接口
 */
public interface MyRejectionStrategy {

    void rejectedExecution(Runnable task,MyThreadPool pool);
}
