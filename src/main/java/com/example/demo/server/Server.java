package com.example.demo.server;

import java.io.IOException;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.config.ServiceConfig;
import com.example.demo.common.HelloService;
import com.example.demo.common.impl.HelloServiceImpl;

public class Server {

	public static void main(String[] args) throws IOException {
		HelloService helloService = new HelloServiceImpl();
		ApplicationConfig application = new ApplicationConfig();
		application.setName("server");
		application.setQosAcceptForeignIp(false);
		application.setQosEnable(true);
		application.setQosPort(33333);
		
		RegistryConfig registry = new RegistryConfig();
		registry.setId("registry");
		registry.setAddress("zookeeper://192.168.1.120:2181");
		registry.setClient("zkclient");
		
		// 服务提供者协议配置
		ProtocolConfig dubboProtocol = new ProtocolConfig();
		dubboProtocol.setName("dubbo");
		dubboProtocol.setServer("netty4");
		dubboProtocol.setPort(20890);
		dubboProtocol.setThreads(4);
		
		ProtocolConfig http = new ProtocolConfig();
		http.setName("http");
		http.setServer("netty4");
		http.setPort(8080);
		http.setThreads(4);
		 
		// 注意：ServiceConfig为重对象，内部封装了与注册中心的连接，以及开启服务端口
		 
		// 服务提供者暴露服务配置
		ServiceConfig<HelloService> service = new ServiceConfig<HelloService>(); // 此实例很重，封装了与注册中心的连接，请自行缓存，否则可能造成内存和连接泄漏
		service.setId("service");
		service.setApplication(application);
		service.setRegistry(registry); // 多个注册中心可以用setRegistries()
		service.setProtocol(dubboProtocol); // 多个协议可以用setProtocols()
		service.setInterface(HelloService.class);
		service.setRef(helloService);
		service.setVersion("1.0.0");
		 
		// 暴露及注册服务
		service.export();
		System.in.read();
	}

}
