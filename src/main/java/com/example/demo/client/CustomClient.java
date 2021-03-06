package com.example.demo.client;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.example.demo.common.HelloService;
import com.example.demo.common.User;

import java.io.IOException;

public class CustomClient {

	public static void main(String[] args) throws IOException {
		// 当前应用配置
		ApplicationConfig application = new ApplicationConfig();
		application.setName("client");
		application.setQosAcceptForeignIp(false);
		application.setQosEnable(true);
		application.setQosPort(22222);
		 
		// 连接注册中心配置
		RegistryConfig registry = new RegistryConfig();
		registry.setId("registry");
		registry.setAddress("nacos://127.0.0.1:8848");
		 
		// 注意：ReferenceConfig为重对象，内部封装了与注册中心的连接，以及与服务提供方的连接
		 
		// 引用远程服务
		ReferenceConfig<HelloService> reference = new ReferenceConfig<HelloService>(); // 此实例很重，封装了与注册中心的连接以及与提供者的连接，请自行缓存，否则可能造成内存和连接泄漏
		reference.setApplication(application);
		reference.setRegistry(registry); // 多个注册中心可以用setRegistries()
		reference.setInterface(HelloService.class);
		reference.setVersion("1.0.0");
		reference.setClient("netty4");
		 
		// 和本地bean一样使用xxxService
		HelloService helloService = reference.get(); // 注意：此代理对象内部封装了所有通讯细节，对象较重，请缓存复用
		User user = helloService.getUser("Tom");
		System.out.println(user);
	}

}
