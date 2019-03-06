# $Adaptive类分析

每一个用SPI注解标注的接口都会有唯一的Adaptive实现类，如果没有Adaptive实现类，则ExtensionLoader里面会自动编译生成一个$Adaptive类

### Adaptive类有以下几种情况

- 如果接口的子类实现上面有@Adaptive注解，则这个类就是接口唯一的Adaptive类
- 如果接口的子类实现上面没有@Adaptive注解，则ExtensionLoader执行getAdaptiveExtension()会创建一个$Adaptive类，前提条件：接口内部方法上面有@Adaptive；而且@Adaptive标注的方法参数里面必须有URL或参数类型必须有getUrl()方法

### ExtensionLoader创建的适配类格式如下：

- 接口没有@Adaptive注解修饰的方法，在适配类内部的实现都是直接抛异常，如下所示：

```java
//以下是接口原生没有@Adaptive注解修饰的方法
void destroy();

//以下是自动生成的适配类创建的方法
public void destroy() {
	throw new UnsupportedOperationException(
				"method public abstract void com.alibaba.dubbo.rpc.Protocol.destroy() of interface com.alibaba.dubbo.rpc.Protocol is not adaptive method!");
}
```

- 接口有@Adaptive注解修饰的方法，在适配类内部的实现都是直接抛异常，如下所示：
```java
//以下是接口原生带有@Adaptive注解修饰的方法
@Adaptive
<T> Exporter<T> export(Invoker<T> invoker) throws RpcException;

//以下是自动生成的适配类创建的方法，参数带getUrl()方法的情况
public Exporter export(Invoker arg0) throws RpcException {
	if (arg0 == null)
		throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
	if (arg0.getUrl() == null)
		throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");
	URL url = arg0.getUrl();//参数必须有getUrl()方法
	String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
	if (extName == null)
		throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url("
				+ url.toString() + ") use keys([protocol])");
	Protocol extension = (Protocol) ExtensionLoader
    .getExtensionLoader(Protocol.class).getExtension(extName);
	return extension.export(arg0);
}

//以下是自动生成的适配类创建的方法，有URL类型参数的情况
public Invoker refer(Class arg0, URL arg1) throws RpcException {
	if (arg1 == null)
		throw new IllegalArgumentException("url == null");
	URL url = arg1;
	String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
	if (extName == null)
		throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url("
				+ url.toString() + ") use keys([protocol])");
	Protocol extension = (Protocol) ExtensionLoader
	.getExtensionLoader(Protocol.class).getExtension(extName);
	return extension.refer(arg0, arg1);
}
```

> - 上述自动生成的$Adaptive类都有一个共同点，都会执行ExtensionLoader.getExtensionLoader(Type).getExtension(extName)方法，通过这个方法获取扩展类对象，并且自动执行对应的同名方法。
> - 而getExtension(extName)会自动获取扩展类对象，如果对象不存在则创建，如果接口的子类实现里面有包装类，则会自动创建嵌套对象，详情参考ExtensionLoader.createExtension(name)方法

接口的包装类格式如下：

```java
public XxxClass implements XxxInterface {//必须实现接口
    private XxxInterface xxxInterface;
    public XxxClass(XxxInterface xxxInterface){//必须有一个参数的构造函数，并且类型为接口类型
        
    }
}
```

