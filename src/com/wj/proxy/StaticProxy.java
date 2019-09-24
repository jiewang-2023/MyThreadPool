package com.wj.proxy;

/**
 * 共同的接口
 */
interface IUserService {
    void register();
}

/**
 * 被代理类
 */
class UserServiceImpl2 implements IUserService {

    @Override
    public void register() {
        System.out.println("用户注册");
    }
}

/**
 * 代理类
 */
class UserServiceStaticProxy implements IUserService {

    //目标对象
    private IUserService userService;

    /**
     * 通过构造将目标对象传进来
     * @param userService
     */
    public UserServiceStaticProxy(IUserService userService) {
        this.userService = userService;
    }

    @Override
    public void register() {
        System.out.println("日志记录开始...");
        userService.register();
        System.out.println("日志记录结束...");
    }
}

/**
 * @author jie
 * @date 2019/9/24 15:29
 * 静态代理：
 *      实例和代理类都必须实现相同的接口，不管传递什么实例进代理类中都能调用方法，不需要在每个新生的实例中重复写某一个方法
 *      由程序员创建或工具生成代理类的源码，再编译代理类。所谓静态也就是在程序运行前就已经存在代理类的字节码文件，代理类和委托类的关系在运行前就确定了。
 *
 * 优点：被代理对象只要和代理类实现了同一接口即可，代理类无须知道被代理对象具体是什么类、怎么做的，而客户端只需知道代理即可，实现了类之间的解耦合。
 * 缺点：代理类中出现的被代理类越来越多，内部会显得非常臃肿，不利于阅读
 * 应用场景 开闭原则 方法增强  不修改源码的情况下，增强一些方法，比如添加日志等。
 *
 */
public class StaticProxy {
    public static void main(String[] args) {
        UserServiceImpl2 us2 = new UserServiceImpl2();
//        us2.register();
        UserServiceStaticProxy staticProxy = new UserServiceStaticProxy(us2);
        staticProxy.register();
    }
}
