package com.wj.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

interface UserService {
    int deleteUser();

    void registerUser();
}

class UserServiceImpl implements UserService {
    @Override
    public int deleteUser() {
      //  System.out.println("事务开始");
        System.out.println("删除用户");
      //  System.out.println("事务结束");
        return 6;
    }

    @Override
    public void registerUser() {
        //System.out.println("事务开始");
        System.out.println("注册用户");
      //  System.out.println("事务结束");
    }

    void updateUser() {
        System.out.println("更新用户信息");
    }

    void updateUse2() {
        System.out.println("...");
    }
}


/**@author WJ
 * 动态代理概述
 *          在程序运行时运用反射机制动态创建而成。
 * 		1.代理：本来应该自己做的事情，请了别人来做，被请的人就是代理对象。
 * 		举例：春节回家买票让人代买
 *
 * 		2.在Java中java.lang.reflect包下提供了一个Proxy类和一个InvocationHandler接口
 * 		3.通过使用这个类和接口就可以生成动态代理对象。
 *
 * 		4.JDK提供的代理只能针对接口做代理。
 *
 * 		5.我们有更强大的代理cglib
 * 		6.Proxy类中的方法创建动态代理类对象
 * 			Proxy 通过 newProxyInstance(loader,interfaces,h)创建代理对象
 * 			类名是 $ProxyN  N 是逐一递增的数字
 * 		    InvocationHandler的invoke(proxy,method, args)方法会拦截方法的调用
 */
public class DynamicProxy {

    public static void main(String[] args) {
        UserServiceImpl userService = new UserServiceImpl();
//        userService.registerUser();
//        userService.deleteUser();

  /*      UserService proxyInstance = (UserService) Proxy.newProxyInstance(userService.getClass().getClassLoader(),
                userService.getClass().getInterfaces(),
                (proxy, method, args1) -> {
                    System.out.println("事务开始");
//                    returnObj 方法返回值，         被代理的对象，  参数
                    Object returnObj = method.invoke(userService, args1);
                    System.out.println("事务结束");
                    return returnObj;
                });

        int i = proxyInstance.deleteUser();
        System.out.println("i = " + i);
        proxyInstance.registerUser();*/

        UserService o = (UserService) Proxy.newProxyInstance(userService.getClass().getClassLoader(), userService.getClass().getInterfaces(), new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
               // method.invoke(userService,args);
                return null;
            }
        });

//        System.out.println("o = " + o.getClass());
        o.registerUser();

    }

}
