package com.example.demo.server;

import java.io.IOException;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class App {

	public static void main(String[] args) throws IOException {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:server.xml");
        context.start();
        System.in.read(); // 按任意键退出
	}
}
