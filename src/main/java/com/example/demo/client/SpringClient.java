package com.example.demo.client;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.example.demo.common.HelloService;
import com.example.demo.common.User;

public class SpringClient {

	public static void main(String[] args) {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:client.xml");
        context.start();
        for (String item : context.getBeanDefinitionNames()) {
			System.out.println(context.getBean(item).getClass().getCanonicalName());
		}
        HelloService helloService = (HelloService)context.getBean("helloService"); // 获取远程服务代理
        User user = helloService.getUser("Tom"); // 执行远程方法
        System.out.println(user);
        context.close();
	}

}
