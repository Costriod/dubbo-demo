# Filter接口源码分析

```java
@SPI
public interface Filter {
    /**
     * do invoke filter.
     * <p>
     * <code>
     * // before filter
     * Result result = invoker.invoke(invocation);
     * // after filter
     * return result;
     * </code>
     *
     * @param invoker    service
     * @param invocation invocation.
     * @return invoke result.
     * @throws RpcException
     * @see com.alibaba.dubbo.rpc.Invoker#invoke(Invocation)
     */
    Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException;
}
```

### Filter接口实现类

- cache=com.alibaba.dubbo.cache.filter.CacheFilter
- validation=com.alibaba.dubbo.validation.filter.ValidationFilter
- echo=com.alibaba.dubbo.rpc.filter.EchoFilter
- generic=com.alibaba.dubbo.rpc.filter.GenericFilter
- genericimpl=com.alibaba.dubbo.rpc.filter.GenericImplFilter
- token=com.alibaba.dubbo.rpc.filter.TokenFilter
- accesslog=com.alibaba.dubbo.rpc.filter.AccessLogFilter
- activelimit=com.alibaba.dubbo.rpc.filter.ActiveLimitFilter
- classloader=com.alibaba.dubbo.rpc.filter.ClassLoaderFilter
- context=com.alibaba.dubbo.rpc.filter.ContextFilter
- consumercontext=com.alibaba.dubbo.rpc.filter.ConsumerContextFilter
- exception=com.alibaba.dubbo.rpc.filter.ExceptionFilter
- executelimit=com.alibaba.dubbo.rpc.filter.ExecuteLimitFilter
- deprecated=com.alibaba.dubbo.rpc.filter.DeprecatedFilter
- compatible=com.alibaba.dubbo.rpc.filter.CompatibleFilter
- timeout=com.alibaba.dubbo.rpc.filter.TimeoutFilter
- trace=com.alibaba.dubbo.rpc.protocol.dubbo.filter.TraceFilter
- future=com.alibaba.dubbo.rpc.protocol.dubbo.filter.FutureFilter
- monitor=com.alibaba.dubbo.monitor.support.MonitorFilter



### CacheFilter代码实现

核心就是在invoke阶段会从缓存工厂中获取缓存对象，如果存在则返回，否则执行invoker.invoke(invocation)

```java
@Activate(group = {Constants.CONSUMER, Constants.PROVIDER}, value = Constants.CACHE_KEY)
public class CacheFilter implements Filter {
    private CacheFactory cacheFactory;
    /**
     * 在ExtensionLoader的injectExtension方法执行的过程中，会自动执行接口子类对象的一些set语句，具体参考getExtension方法
     */
    public void setCacheFactory(CacheFactory cacheFactory) {
        this.cacheFactory = cacheFactory;
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        if (cacheFactory != null && ConfigUtils.isNotEmpty(invoker.getUrl().getMethodParameter(invocation.getMethodName(), Constants.CACHE_KEY))) {
            Cache cache = cacheFactory.getCache(invoker.getUrl(), invocation);
            if (cache != null) {
                String key = StringUtils.toArgumentString(invocation.getArguments());
                Object value = cache.get(key);
                if (value != null) {
                    return new RpcResult(value);
                }
                Result result = invoker.invoke(invocation);
                if (!result.hasException()) {
                    cache.put(key, result.getValue());
                }
                return result;
            }
        }
        return invoker.invoke(invocation);
    }
}
```

###CacheFilter代码实现

