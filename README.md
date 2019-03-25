# dubbo-demo
基于dubbo2.6.2源码分析



1.由于有一些接口没有Adaptive类，所以最终ExtensionLoader会自动为这些接口动态生成Adaptive类，前提条件是接口必须有SPI注解，同时某些方法上面必须有@Adaptive注解，因为没有@Adaptive注解的方法在动态类里面都会直接抛异常

2.由于Adaptive类调试不便，所以我自己在com.example.demo.autogen包下面把ExtensionLoader的createAdaptiveExtensionClassCode方法动态生成的类代码给导出来了，单独生成了一个类文件，并添加@Adaptive注解，最后放到META-INF下面让dubbo启动时自动扫描到，这样就有利于调试，反正一个接口只有一个Adaptive类，多了会抛异常

3.经实际测试，默认是failover策略，server端执行超时会导致客户端发送最多3次RPC请求，这里需要注意一下幂等性
