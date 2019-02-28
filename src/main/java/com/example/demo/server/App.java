package com.example.demo.server;

import java.io.IOException;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.config.ServiceConfig;
import com.example.demo.common.HelloService;

public class App {

	public static void main(String[] args) throws IOException {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:server.xml");
        context.start();
        ApplicationConfig server = context.getBean(ApplicationConfig.class);
        RegistryConfig registry = context.getBean(RegistryConfig.class);
        ProtocolConfig protocol = context.getBean(ProtocolConfig.class);
        ServiceConfig<HelloService> service = context.getBean(ServiceConfig.class);
        System.in.read(); // 按任意键退出
	}
}
