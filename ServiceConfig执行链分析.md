# ServiceConfig原理分析

###ServiceConfig

需注意两个内部属性：

```java
private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

private static final ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
```

> 由于Protocol接口和ProxyFactory接口没有具体的Adaptive实现类，所以getAdaptiveExtension()会为这两个接口创建Adaptive类，因为Adaptive类不好调试，所以我把生成的Adaptive类源代码手工创建一个类，这样被dubbo自动扫描到就好调试了



执行流程：export()---->doExport()---->doExportUrls()---->doExportUrlsFor1Protocol(protocolConfig, registryURLs)

- 重点关注doExportUrls()执行流程

```java
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doExportUrls() {
        //把registries信息转换成URL对象数组
        List<URL> registryURLs = loadRegistries(true);
        for (ProtocolConfig protocolConfig : protocols) {
            doExportUrlsFor1Protocol(protocolConfig, registryURLs);
        }
    }
```

loadRegistries(true)方法将配置好的registry信息转换成一个个URL对象，URL的大致内容如下：

> registry://192.168.1.120:2181/com.alibaba.dubbo.registry.RegistryService?application=server&client=zkclient&dubbo=2.6.2&pid=11880&qos.accept.foreign.ip=false&qos.enable=true&qos.port=33333&registry=zookeeper&timestamp=1552484682709
>



接下来doExportUrlsFor1Protocol方法主要干了两件事：

exportLocal(url)暴露本地服务

暴露远程服务（暴露过程中需要注册到registry）

```java
if (!Constants.SCOPE_REMOTE.toString().equalsIgnoreCase(scope)) {
    exportLocal(url);//暴露本地服务
}
// export to remote if the config is not local (export to local only when config is local)
if (!Constants.SCOPE_LOCAL.toString().equalsIgnoreCase(scope)) {
    if (logger.isInfoEnabled()) {
        logger.info("Export dubbo service " + interfaceClass.getName() + " to url " + url);
    }
    if (registryURLs != null && !registryURLs.isEmpty()) {
        for (URL registryURL : registryURLs) {
            url = url.addParameterIfAbsent(Constants.DYNAMIC_KEY, registryURL.getParameter(Constants.DYNAMIC_KEY));
            URL monitorUrl = loadMonitor(registryURL);
            if (monitorUrl != null) {
                url = url.addParameterAndEncoded(Constants.MONITOR_KEY, monitorUrl.toFullString());
            }
            if (logger.isInfoEnabled()) {
                logger.info("Register dubbo service " + interfaceClass.getName() + " url " + url + " to registry " + registryURL);
            }
            Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(Constants.EXPORT_KEY, url.toFullString()));
            DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);

            Exporter<?> exporter = protocol.export(wrapperInvoker);
            exporters.add(exporter);
        }
    } else {
        Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, url);
        DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);

        Exporter<?> exporter = protocol.export(wrapperInvoker);
        exporters.add(exporter);
    }
}
```

