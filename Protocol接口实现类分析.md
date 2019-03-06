# ExtensionLoader源码分析

### Protocol接口源码分析
```java
@SPI("dubbo")//SPI注解里面有默认名字
public interface Protocol {
    int getDefaultPort();

    /**
     * 暴露服务给远程执行: <br>
     * 1. 协议应当记录请求源地址: RpcContext.getContext().setRemoteAddress();
     * 2. export()应当幂等，对同一个URL进行暴露服务，无论执行多少次export()都应该是幂等的<br>
     * 3. 调用程序实例由框架传入，协议不需要关心 <br>
     */
    @Adaptive
    <T> Exporter<T> export(Invoker<T> invoker) throws RpcException;

    /**
     * Refer a remote service: <br>
     * 1. When user calls `invoke()` method of `Invoker` object which's returned from `refer()` call, the protocol
     * needs to correspondingly execute `invoke()` method of `Invoker` object <br>
     * 2. It's protocol's responsibility to implement `Invoker` which's returned from `refer()`. Generally speaking,
     * protocol sends remote request in the `Invoker` implementation. <br>
     * 3. When there's check=false set in URL, the implementation must not throw exception but try to recover when
     * connection fails.
     *
     * @param <T>  Service type
     * @param type Service class
     * @param url  URL address for the remote service
     * @return invoker service's local proxy
     * @throws RpcException when there's any error while connecting to the service provider
     */
    @Adaptive
    <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException;

    /**
     * Destroy protocol: <br>
     * 1. Cancel all services this protocol exports and refers <br>
     * 2. Release all occupied resources, for example: connection, port, etc. <br>
     * 3. Protocol can continue to export and refer new service even after it's destroyed.
     */
    void destroy();

}
```

### Protocol接口具体实现：

- filter=com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper
- listener=com.alibaba.dubbo.rpc.protocol.ProtocolListenerWrapper
- mock=com.alibaba.dubbo.rpc.support.MockProtocol
- dubbo=com.alibaba.dubbo.rpc.protocol.dubbo.DubboProtocol
- injvm=com.alibaba.dubbo.rpc.protocol.injvm.InjvmProtocol
- rmi=com.alibaba.dubbo.rpc.protocol.rmi.RmiProtocol
- hessian=com.alibaba.dubbo.rpc.protocol.hessian.HessianProtocol
- com.alibaba.dubbo.rpc.protocol.http.HttpProtocol
- com.alibaba.dubbo.rpc.protocol.webservice.WebServiceProtocol
- thrift=com.alibaba.dubbo.rpc.protocol.thrift.ThriftProtocol
- memcached=com.alibaba.dubbo.rpc.protocol.memcached.MemcachedProtocol
- redis=com.alibaba.dubbo.rpc.protocol.redis.RedisProtocol
- rest=com.alibaba.dubbo.rpc.protocol.rest.RestProtocol
- registry=com.alibaba.dubbo.registry.integration.RegistryProtocol
- qos=com.alibaba.dubbo.qos.protocol.QosProtocolWrapper

这些实现类里面没有任何一个类有@Adaptive注解，所以最终ExtensionLoader会自动创建$Adaptive类，并且会自动创建包装类对象

其中包装类有如下几个：

> - com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper
>
>   ProtocolFilterWrapper会自动创建Filter执行链
>
> - com.alibaba.dubbo.rpc.protocol.ProtocolListenerWrapper
>
>   ProtocolListenerWrapper底层也是调用了内部嵌套对象的同样方法
>
> - com.alibaba.dubbo.rpc.protocol.dubbo.DubboProtocol
>
>   DubboProtocol才是创建server并建立端口监听的地方
>
> - com.alibaba.dubbo.rpc.protocol.injvm.InjvmProtocol
>
>   本地服务，无需建立端口监听
>
> - com.alibaba.dubbo.registry.integration.RegistryProtocol
>
>   RegistryProtocol将服务注册到注册中心
>
> - com.alibaba.dubbo.qos.protocol.QosProtocolWrapper
>
>   Qos在线运维相关