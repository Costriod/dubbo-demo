package com.example.demo.client;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.example.demo.common.HelloService;
import com.example.demo.common.User;

public class App {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:client.xml");
        context.start();
        HelloService helloService = (HelloService)context.getBean("helloService"); // 获取远程服务代理
        User user = helloService.getUser("zhugd"); // 执行远程方法
        System.out.println(user); // 显示调用结果
        context.close();
	}

}
