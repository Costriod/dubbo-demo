package com.example.demo.autogen;

import com.alibaba.dubbo.common.extension.Adaptive;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
/**
 * ProxyFactory接口没有Adaptive类，dubbo会自动为该类生成一个$Adaptive类，但是这种动态生成的类不好调试，
 * 所以手工把ExtensionLoader.createAdaptiveExtensionClassCode()创建的类代码单独弄出来创建一个类以便于调试，两者都是等价的
 */
@Adaptive
public class ProxyFactoryAdaptive implements com.alibaba.dubbo.rpc.ProxyFactory {
	public com.alibaba.dubbo.rpc.Invoker getInvoker(java.lang.Object arg0, java.lang.Class arg1,
			com.alibaba.dubbo.common.URL arg2) throws com.alibaba.dubbo.rpc.RpcException {
		if (arg2 == null)
			throw new IllegalArgumentException("url == null");
		com.alibaba.dubbo.common.URL url = arg2;
		String extName = url.getParameter("proxy", "javassist");
		if (extName == null)
			throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.ProxyFactory) name from url("
					+ url.toString() + ") use keys([proxy])");
		com.alibaba.dubbo.rpc.ProxyFactory extension = (com.alibaba.dubbo.rpc.ProxyFactory) ExtensionLoader
				.getExtensionLoader(com.alibaba.dubbo.rpc.ProxyFactory.class).getExtension(extName);
		//返回的extension是StubProxyFactoryWrapper，内嵌了一个JavassistProxyFactory以及一个Protocol接口的Adaptive类对象
		return extension.getInvoker(arg0, arg1, arg2);
	}

	public java.lang.Object getProxy(com.alibaba.dubbo.rpc.Invoker arg0) throws com.alibaba.dubbo.rpc.RpcException {
		if (arg0 == null)
			throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
		if (arg0.getUrl() == null)
			throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");
		com.alibaba.dubbo.common.URL url = arg0.getUrl();
		String extName = url.getParameter("proxy", "javassist");
		if (extName == null)
			throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.ProxyFactory) name from url("
					+ url.toString() + ") use keys([proxy])");
		com.alibaba.dubbo.rpc.ProxyFactory extension = (com.alibaba.dubbo.rpc.ProxyFactory) ExtensionLoader
				.getExtensionLoader(com.alibaba.dubbo.rpc.ProxyFactory.class).getExtension(extName);
		return extension.getProxy(arg0);
	}
}