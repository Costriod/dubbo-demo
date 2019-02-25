package com.example.demo.common.impl;

import com.example.demo.common.HelloService;
import com.example.demo.common.User;

public class HelloServiceImpl implements HelloService{

	@Override
	public User getUser(String name) {
		return new User(name);
	}

}
