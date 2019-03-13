# ExtensionLoader源码分析
```java
/**
 * Load dubbo extensions
 * <ul>
 * <li>auto inject dependency extension </li>
 * <li>auto wrap extension in wrapper </li>
 * <li>default extension is an adaptive instance</li>
 * </ul>
 *
 * 1.接口上面用SPI注解标注一下，如果SPI有value，cachedDefaultName = SPI.value()
 * 2.接口实现类会被拆分成3个部分：Adaptive适配类/Wrapper类/其他
 * 2.1 Adaptive适配类：Adaptive注解标注的子类，或者动态生成的Adaptive类（因为每一个SPI注解标注的接口都必须有一个Adaptive类，如果没有则动态生成一个）
 * 2.2 Wrapper类：接口实现类如果有构造函数，并且构造函数的参数为其接口类型，如public Wrapper(Type type), 其中Wrapper实现Type接口，这种类会单独丢到一个set里面
 * 2.3 其他类：除上面2种类型的类都会单独放入cachedNames这个Map
 * 
 * 3.injectExtension方法会自动给扩展类实例执行setXxx方法，通过这种方式注入
 * 4.getAdaptiveExtension()获取接口全局唯一的Adaptive类对象，如果没有则创建，创建之后会执行injectExtension方法注入
 * 5.getExtension()获取接口实现类的实例，如果没有则创建，创建之后会执行injectExtension方法注入，但是如果接口有Wrapper类，
 * 则会循环执行Wrapper构造函数创建一个嵌套对象，嵌套对象也会执行injectExtension方法注入（Wrapper嵌套顺序随机）
 * 
 * 6.动态生成的Adaptive类有几个前提条件
 * 6.1 接口没有Adaptive注解修饰的实现类
 * 6.2 接口有Adaptive注解修饰的方法
 * 6.3 生成的Adaptive方法里面必须有URL参数，或者参数类型必须有getUrl方法
 * 
 * 生成的动态方法如下所示：
 * public com.alibaba.dubbo.rpc.Exporter export(com.alibaba.dubbo.rpc.Invoker arg0) throws com.alibaba.dubbo.rpc.RpcException {
 *      if (arg0 == null)
 *          throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
 *      if (arg0.getUrl() == null)
 *          throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");
 *      com.alibaba.dubbo.common.URL url = arg0.getUrl();
 *      String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
 *      if (extName == null)
 *          throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url(" + url.toString() + ") use keys([protocol])");
 *      com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
 *      return extension.export(arg0);
 *  }
 *
 *  public com.alibaba.dubbo.rpc.Invoker refer(java.lang.Class arg0, com.alibaba.dubbo.common.URL arg1) throws com.alibaba.dubbo.rpc.RpcException {
 *      if (arg1 == null)
 *          throw new IllegalArgumentException("url == null");
 *      com.alibaba.dubbo.common.URL url = arg1;
 *      String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
 *      if (extName == null)
 *          throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url(" + url.toString() + ") use keys([protocol])");
 *      com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
 *      return extension.refer(arg0, arg1);
 *  }
 */
public class ExtensionLoader<T> {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final String SERVICES_DIRECTORY = "META-INF/services/";

    private static final String DUBBO_DIRECTORY = "META-INF/dubbo/";

    private static final String DUBBO_INTERNAL_DIRECTORY = DUBBO_DIRECTORY + "internal/";
    /**
     * 正则表达式匹配","，当然","左右两边的空格也会匹配到
     */
    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[,]+\\s*");

    /**
     * 保存扩展类与ExtensionLoader关系
     */
    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<Class<?>, ExtensionLoader<?>>();
    /**
     * 保存扩展类与扩展类对象实例的关系
     */
    private static final ConcurrentMap<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<Class<?>, Object>();

    // ==============================

    private final Class<?> type;

    private final ExtensionFactory objectFactory;
    /**
     * 保存扩展类与名称的映射，除了Wrapper类和带有Adaptive注解的类
     */
    private final ConcurrentMap<Class<?>, String> cachedNames = new ConcurrentHashMap<Class<?>, String>();

    /**
     * 保存扩展类别名与类对象的映射（Wrapper类和带有Adaptive注解的类不包括在内），如：
     * spring=com.alibaba.dubbo.config.spring.status.SpringStatusChecker
     * 则会保存spring（别名）---> com.alibaba.dubbo.config.spring.status.SpringStatusChecker的映射关系
     */
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<Map<String, Class<?>>>();
    /**
     * 如果某类有Activate注解，则cachedActivates保留别名和cachedActivates注解的映射
     */
    private final Map<String, Activate> cachedActivates = new ConcurrentHashMap<String, Activate>();
    /**
     * 类别名与类对象实例的映射
     */
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<String, Holder<Object>>();
    /**
     * 唯一的Adaptive类的一个对象，每个SPI接口只有唯一一个Adaptive对象
     */
    private final Holder<Object> cachedAdaptiveInstance = new Holder<Object>();
    /**
     * type接口的实现类里面唯一一个带Adaptive注解的类（不可能有多个）
     */
    private volatile Class<?> cachedAdaptiveClass = null;
    /**
     * SPI注解里面默认的value，为空则cachedDefaultName为null
     */
    private String cachedDefaultName;
    private volatile Throwable createAdaptiveInstanceError;
    /**
     * type接口实现类里面，有一个参数的构造函数，并且参数是type类型，则会添加到cachedWrapperClasses里面
     */
    private Set<Class<?>> cachedWrapperClasses;

    private Map<String, IllegalStateException> exceptions = new ConcurrentHashMap<String, IllegalStateException>();
    /**
     * 构造函数，如果type是ExtensionFactory，则objectFactory为null
     */
    private ExtensionLoader(Class<?> type) {
        this.type = type;
        objectFactory = (type == ExtensionFactory.class ? null : ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension());
    }
    /**
     * 判断type上面有没有SPI注解
     */
    private static <T> boolean withExtensionAnnotation(Class<T> type) {
        return type.isAnnotationPresent(SPI.class);
    }

    /**
     * 从EXTENSION_LOADERS获取type类的ExtensionLoader对象，如果没有就创建一个并put到EXTENSION_LOADERS
     * 1.type不能为null
     * 2.type必须是interface
     * 3.type必须有SPI注解
     */
    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null)
            throw new IllegalArgumentException("Extension type == null");
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type(" + type + ") is not interface!");
        }
        if (!withExtensionAnnotation(type)) {
            throw new IllegalArgumentException("Extension type(" + type +
                    ") is not extension, because WITHOUT @" + SPI.class.getSimpleName() + " Annotation!");
        }

        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }

    /**
     * 找出ClassLoader，一般是ClassLoader.getSystemClassLoader()
     */
    private static ClassLoader findClassLoader() {
        return ExtensionLoader.class.getClassLoader();
    }
    /**
     * 找出扩展类名称，从cachedNames找
     */
    public String getExtensionName(T extensionInstance) {
        return getExtensionName(extensionInstance.getClass());
    }
    /**
     * 找出扩展类名称，从cachedNames找
     */
    public String getExtensionName(Class<?> extensionClass) {
        return cachedNames.get(extensionClass);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, key, null)}
     *
     * @param url url
     * @param key url parameter key which used to get extension point names
     * @return extension list which are activated.
     * @see #getActivateExtension(com.alibaba.dubbo.common.URL, String, String)
     */
    public List<T> getActivateExtension(URL url, String key) {
        return getActivateExtension(url, key, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, values, null)}
     *
     * @param url    url
     * @param values extension point names
     * @return extension list which are activated
     * @see #getActivateExtension(com.alibaba.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String[] values) {
        return getActivateExtension(url, values, null);
    }

    /**
     * 返回标有Activate注解的类实例 {@code getActivateExtension(url, url.getParameter(key).split(","), null)}
     *
     * @param url   url
     * @param key   url parameter key which used to get extension point names
     * @param group group
     * @return extension list which are activated.
     * @see #getActivateExtension(com.alibaba.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String key, String group) {
        String value = url.getParameter(key);
        return getActivateExtension(url, value == null || value.length() == 0 ? null : Constants.COMMA_SPLIT_PATTERN.split(value), group);
    }

    /**
     * 返回标有Activate注解的类实例
     *
     * @param url    url
     * @param values extension point names
     * @param group  group
     * @return extension list which are activated
     * @see com.alibaba.dubbo.common.extension.Activate
     */
    public List<T> getActivateExtension(URL url, String[] values, String group) {
        List<T> exts = new ArrayList<T>();
        List<String> names = values == null ? new ArrayList<String>(0) : Arrays.asList(values);
        //如果names不包含"-default"
        if (!names.contains(Constants.REMOVE_VALUE_PREFIX + Constants.DEFAULT_KEY)) {
            getExtensionClasses();//如果cachedActivates未初始化，执行载入实现类操作，并初始化cachedActivates
            for (Map.Entry<String, Activate> entry : cachedActivates.entrySet()) {
                String name = entry.getKey();
                Activate activate = entry.getValue();
                if (isMatchGroup(group, activate.group())) {//group在groups里面
                    T ext = getExtension(name);//返回扩展类实例，如果没有，则创建新实例并返回
                    if (!names.contains(name)
                            && !names.contains(Constants.REMOVE_VALUE_PREFIX + name)
                            && isActive(activate, url)) {
                        exts.add(ext);
                    }
                }
            }
            Collections.sort(exts, ActivateComparator.COMPARATOR);
        }
        List<T> usrs = new ArrayList<T>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (!name.startsWith(Constants.REMOVE_VALUE_PREFIX)
                    && !names.contains(Constants.REMOVE_VALUE_PREFIX + name)) {
                if (Constants.DEFAULT_KEY.equals(name)) {
                    if (!usrs.isEmpty()) {
                        exts.addAll(0, usrs);
                        usrs.clear();
                    }
                } else {
                    T ext = getExtension(name);
                    usrs.add(ext);
                }
            }
        }
        if (!usrs.isEmpty()) {
            exts.addAll(usrs);
        }
        return exts;
    }

    /**
     * 判断group是否在groups里面
     * 1.如果group为空则返回true
     * 2.如果groups包括group则返回true
     * 3.否则返回false
     */
    private boolean isMatchGroup(String group, String[] groups) {
        if (group == null || group.length() == 0) {
            return true;
        }
        if (groups != null && groups.length > 0) {
            for (String g : groups) {
                if (group.equals(g)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isActive(Activate activate, URL url) {
        String[] keys = activate.value();
        if (keys.length == 0) {
            return true;
        }
        for (String key : keys) {
            for (Map.Entry<String, String> entry : url.getParameters().entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if ((k.equals(key) || k.endsWith("." + key))
                        && ConfigUtils.isNotEmpty(v)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 从cachedInstances根据别名获取实例，如果没有则返回一个null
     *
     * @see #getExtension(String)
     */
    @SuppressWarnings("unchecked")
    public T getLoadedExtension(String name) {
        if (name == null || name.length() == 0)
            throw new IllegalArgumentException("Extension name == null");
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<Object>());
            holder = cachedInstances.get(name);
        }
        return (T) holder.get();
    }

    /**
     * Return the list of extensions which are already loaded.
     * <p>
     * Usually {@link #getSupportedExtensions()} should be called in order to get all extensions.
     *
     * @see #getSupportedExtensions()
     */
    public Set<String> getLoadedExtensions() {
        return Collections.unmodifiableSet(new TreeSet<String>(cachedInstances.keySet()));
    }

    /**
     * 找出name别名对应的实现类，如果name为null或空字符则抛出IllegalArgumentException
     * 如果cachedInstances缓存没有对象，则创建一个新对象，如果name名称为true则返回null
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        if (name == null || name.length() == 0)
            throw new IllegalArgumentException("Extension name == null");
        if ("true".equals(name)) {
            return getDefaultExtension();
        }
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {//如果为空则创建一个新对象
            cachedInstances.putIfAbsent(name, new Holder<Object>());
            holder = cachedInstances.get(name);
        }
        Object instance = holder.get();
        if (instance == null) {//如果instance为空
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    instance = createExtension(name);
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * 返回默认实例，如果没有配置cachedDefaultName则返回null
     */
    public T getDefaultExtension() {
        getExtensionClasses();
        if (null == cachedDefaultName || cachedDefaultName.length() == 0
                || "true".equals(cachedDefaultName)) {
            return null;
        }
        return getExtension(cachedDefaultName);
    }

    /**
     * 判断这个别名是否有扩展类
     */
    public boolean hasExtension(String name) {
        if (name == null || name.length() == 0)
            throw new IllegalArgumentException("Extension name == null");
        try {
            this.getExtensionClass(name);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 返回已经实现的类别名集和
     */
    public Set<String> getSupportedExtensions() {
        Map<String, Class<?>> clazzes = getExtensionClasses();
        return Collections.unmodifiableSet(new TreeSet<String>(clazzes.keySet()));
    }

    /**
     * 返回cachedDefaultName
     */
    public String getDefaultExtensionName() {
        getExtensionClasses();
        return cachedDefaultName;
    }

    /**
     * 添加一个新的扩展类以及别名，如果类还有Adaptive注解，则cachedAdaptiveClass会被覆盖
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension with the same name has already been registered.
     */
    public void addExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + "not implement Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + "can not be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " already existed(Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
        } else {
            if (cachedAdaptiveClass != null) {
                throw new IllegalStateException("Adaptive Extension already existed(Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
        }
    }

    /**
     * 替换以存在的扩展类
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension to be placed doesn't exist
     * @deprecated not recommended any longer, and use only when test
     */
    @Deprecated
    public void replaceExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + "not implement Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + "can not be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (!cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " not existed(Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
            cachedInstances.remove(name);
        } else {
            if (cachedAdaptiveClass == null) {
                throw new IllegalStateException("Adaptive Extension not existed(Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
            cachedAdaptiveInstance.set(null);
        }
    }

    /**
     * 返回type全局唯一的Adaptive类的一个对象
     */
    @SuppressWarnings("unchecked")
    public T getAdaptiveExtension() {
        Object instance = cachedAdaptiveInstance.get();
        if (instance == null) {
            if (createAdaptiveInstanceError == null) {
                synchronized (cachedAdaptiveInstance) {
                    instance = cachedAdaptiveInstance.get();
                    if (instance == null) {
                        try {
                            instance = createAdaptiveExtension();
                            cachedAdaptiveInstance.set(instance);
                        } catch (Throwable t) {
                            createAdaptiveInstanceError = t;
                            throw new IllegalStateException("fail to create adaptive instance: " + t.toString(), t);
                        }
                    }
                }
            } else {
                throw new IllegalStateException("fail to create adaptive instance: " + createAdaptiveInstanceError.toString(), createAdaptiveInstanceError);
            }
        }

        return (T) instance;
    }

    private IllegalStateException findException(String name) {
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (entry.getKey().toLowerCase().contains(name.toLowerCase())) {
                return entry.getValue();
            }
        }
        StringBuilder buf = new StringBuilder("No such extension " + type.getName() + " by name " + name);


        int i = 1;
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (i == 1) {
                buf.append(", possible causes: ");
            }

            buf.append("\r\n(");
            buf.append(i++);
            buf.append(") ");
            buf.append(entry.getKey());
            buf.append(":\r\n");
            buf.append(StringUtils.toString(entry.getValue()));
        }
        return new IllegalStateException(buf.toString());
    }

    /**
     * 通过别名找出当前type对应的实现类，创建类对象
     * 创建对象之后，判断当前type的实现类里面哪些类有一个参数的构造方法（构造方法参数也是type类型），然后嵌套执行构造函数
     */
    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw findException(name);
        }
        try {
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            injectExtension(instance);
            Set<Class<?>> wrapperClasses = cachedWrapperClasses;
            if (wrapperClasses != null && !wrapperClasses.isEmpty()) {
                for (Class<?> wrapperClass : wrapperClasses) {
                	//嵌套执行wrapper类的构造方法，并且把当前instance作为构造函数的参数传进去，这样就有可能创建嵌套的对象
                    instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));
                }
            }
            return instance;
        } catch (Throwable t) {
            throw new IllegalStateException("Extension instance(name: " + name + ", class: " +
                    type + ")  could not be instantiated: " + t.getMessage(), t);
        }
    }

    /**
     * 给对象的内部属性赋值，仅会对Adaptive类的对象
     * 如适配类有setXxx(Type param)方法，则从objectFactory找出Type类型别名为xxx的适配对象，并执行setXxx方法
     * @param instance
     * @return
     */
    private T injectExtension(T instance) {
        try {
            if (objectFactory != null) {
                for (Method method : instance.getClass().getMethods()) {//遍历适配类的方法（不包括父类方法）
                    if (method.getName().startsWith("set")
                            && method.getParameterTypes().length == 1
                            && Modifier.isPublic(method.getModifiers())) {//方法以set开头，只包含一个参数，方法为public
                        Class<?> pt = method.getParameterTypes()[0];//第一个参数类型
                        try {
                            String property = method.getName().length() > 3 ? method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4) : "";//类内部属性名
                            Object object = objectFactory.getExtension(pt, property);//获取该type全局唯一Adaptive类的对象，这个对象是会缓存起来
                            if (object != null) {
                                method.invoke(instance, object);//执行set方法，给属性赋值
                            }
                        } catch (Exception e) {
                            logger.error("fail to inject via method " + method.getName()
                                    + " of interface " + type.getName() + ": " + e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return instance;
    }

    private Class<?> getExtensionClass(String name) {
        if (type == null)
            throw new IllegalArgumentException("Extension type == null");
        if (name == null)
            throw new IllegalArgumentException("Extension name == null");
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null)
            throw new IllegalStateException("No such extension \"" + name + "\" for " + type.getName() + "!");
        return clazz;
    }

    /**
     * 返回cachedClasses对象，如果cachedClasses还没初始化，则从这几个路径
     * 载入类{"META-INF/services/", "META-INF/dubbo/internal/", "META-INF/dubbo/"}并返回
     */
    private Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = cachedClasses.get();
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    classes = loadExtensionClasses();
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    /**
     * synchronized in getExtensionClasses
     * 从这几个路径载入类{"META-INF/services/", "META-INF/dubbo/internal/", "META-INF/dubbo/"}
     * 1. 如果name为null或者为空字符
     * 1.1 如果类有Extension注解，则extensionClasses会put(Extension.value(), clazz)
     * 1.2 如果类没有Extension注解，如果clazz以type名结尾，则extensionClasses会put(prefix小写, clazz)
     * 1.3 如果类没有Extension注解，如果clazz不以type名结尾，则extensionClasses会put(clazzName小写, clazz)
     * 
     * 2.如果name不为空
     * 2.1 如果name里面有","，则将name分割成数组，extensionClasses会put(每一个元素, clazz)
     * 2.1 如果name里面没有","，则extensionClasses会put(name, clazz)
     */
    private Map<String, Class<?>> loadExtensionClasses() {
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);//获取type类SPI注解
        if (defaultAnnotation != null) {
            String value = defaultAnnotation.value();//获取type类SPI注解value
            if ((value = value.trim()).length() > 0) {//如果value除去首尾空格后长度大于0
                String[] names = NAME_SEPARATOR.split(value);//分割","（包括","左右两边空格），将value拆分成数组
                if (names.length > 1) {
                    throw new IllegalStateException("more than 1 default extension name on extension " + type.getName()
                            + ": " + Arrays.toString(names));
                }
                if (names.length == 1) cachedDefaultName = names[0];//如果names只有一个元素，则设置cachedDefaultName
            }
        }

        Map<String, Class<?>> extensionClasses = new HashMap<String, Class<?>>();
        //从这几个路径载入类{"META-INF/services/", "META-INF/dubbo/internal/", "META-INF/dubbo/"}
        loadDirectory(extensionClasses, DUBBO_INTERNAL_DIRECTORY);
        loadDirectory(extensionClasses, DUBBO_DIRECTORY);
        loadDirectory(extensionClasses, SERVICES_DIRECTORY);
        return extensionClasses;
    }

    /**
     * 从dir路径载入实现类，写入到extensionClasses这个map里面
     * 1. 如果name为null或者为空字符
     * 1.1 如果类有Extension注解，则extensionClasses会put(Extension.value(), clazz)
     * 1.2 如果类没有Extension注解，如果clazz以type名结尾，则extensionClasses会put(prefix小写, clazz)
     * 1.3 如果类没有Extension注解，如果clazz不以type名结尾，则extensionClasses会put(clazzName小写, clazz)
     * 
     * 2.如果name不为空
     * 2.1 如果name里面有","，则将name分割成数组，extensionClasses会put(每一个元素, clazz)
     * 2.1 如果name里面没有","，则extensionClasses会put(name, clazz)
     */
    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir) {
        String fileName = dir + type.getName();//路径+类全称即是文件路径
        try {
            Enumeration<java.net.URL> urls;
            ClassLoader classLoader = findClassLoader();
            if (classLoader != null) {
                urls = classLoader.getResources(fileName);
            } else {
                urls = ClassLoader.getSystemResources(fileName);
            }
            if (urls != null) {//一般urls都只有一条记录，如果fileName是目录的话在有可能有多条记录
                while (urls.hasMoreElements()) {
                    java.net.URL resourceURL = urls.nextElement();
                    loadResource(extensionClasses, classLoader, resourceURL);
                }
            }
        } catch (Throwable t) {
            logger.error("Exception when load extension class(interface: " +
                    type + ", description file: " + fileName + ").", t);
        }
    }

    /**
     * 载入一个文件里面的扩展类，并给内部属性设置值，一部分内容也会写入extensionClasses里面
     * 1. 如果name为null或者为空字符
     * 1.1 如果类有Extension注解，则extensionClasses会put(Extension.value(), clazz)
     * 1.2 如果类没有Extension注解，如果clazz以type名结尾，则extensionClasses会put(prefix小写, clazz)
     * 1.3 如果类没有Extension注解，如果clazz不以type名结尾，则extensionClasses会put(clazzName小写, clazz)
     * 
     * 2.如果name不为空
     * 2.1 如果name里面有","，则将name分割成数组，extensionClasses会put(每一个元素, clazz)
     * 2.1 如果name里面没有","，则extensionClasses会put(name, clazz)
     */
    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader, java.net.URL resourceURL) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), "utf-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    final int ci = line.indexOf('#');//#作为注释
                    if (ci >= 0) line = line.substring(0, ci);//取#前面的文本，这部分文本不是注释
                    line = line.trim();
                    if (line.length() > 0) {
                        try {
                            String name = null;
                            int i = line.indexOf('=');
                            if (i > 0) {
                                name = line.substring(0, i).trim();
                                line = line.substring(i + 1).trim();
                            }
                            if (line.length() > 0) {
                                loadClass(extensionClasses, resourceURL, Class.forName(line, true, classLoader), name);
                            }
                        } catch (Throwable t) {
                            IllegalStateException e = new IllegalStateException("Failed to load extension class(interface: " + type + ", class line: " + line + ") in " + resourceURL + ", cause: " + t.getMessage(), t);
                            exceptions.put(line, e);
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Throwable t) {
            logger.error("Exception when load extension class(interface: " +
                    type + ", class file: " + resourceURL + ") in " + resourceURL, t);
        }
    }

    /**
     * 1. 如果name为null或者为空字符
     * 1.1 如果类有Extension注解，则extensionClasses会put(Extension.value(), clazz)
     * 1.2 如果类没有Extension注解，如果clazz以type名结尾，则extensionClasses会put(prefix小写, clazz)
     * 1.3 如果类没有Extension注解，如果clazz不以type名结尾，则extensionClasses会put(clazzName小写, clazz)
     * 
     * 2.如果name不为空
     * 2.1 如果name里面有","，则将name分割成数组，extensionClasses会put(每一个元素, clazz)
     * 2.1 如果name里面没有","，则extensionClasses会put(name, clazz)
     * @param extensionClasses map对象
     * @param resourceURL 资源路径
     * @param clazz 类名
     * @param name 类别名
     */
    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name) throws NoSuchMethodException {
        if (!type.isAssignableFrom(clazz)) {//type.isAssignableFrom(clazz)等价于type==clazz或type是clazz的父类
            throw new IllegalStateException("Error when load extension class(interface: " +
                    type + ", class line: " + clazz.getName() + "), class "
                    + clazz.getName() + "is not subtype of interface.");
        }
        if (clazz.isAnnotationPresent(Adaptive.class)) {//如果类有Adaptive注解
            if (cachedAdaptiveClass == null) {
                cachedAdaptiveClass = clazz;//设置cachedAdaptiveClass为clazz
            } else if (!cachedAdaptiveClass.equals(clazz)) {//某个接口只允许有一个Adaptive注解修饰的实现类，超过一个就抛异常
                throw new IllegalStateException("More than 1 adaptive class found: "
                        + cachedAdaptiveClass.getClass().getName()
                        + ", " + clazz.getClass().getName());
            }
        } else if (isWrapperClass(clazz)) {//clazz有一个参数的构造函数，并且参数类型为type
            Set<Class<?>> wrappers = cachedWrapperClasses;
            if (wrappers == null) {
                cachedWrapperClasses = new ConcurrentHashSet<Class<?>>();
                wrappers = cachedWrapperClasses;
            }
            wrappers.add(clazz);//cachedWrapperClasses添加该类
        } else {
            clazz.getConstructor();//获取无参构造函数
            if (name == null || name.length() == 0) {
                name = findAnnotationName(clazz);
                if (name == null || name.length() == 0) {
                    if (clazz.getSimpleName().length() > type.getSimpleName().length()
                            && clazz.getSimpleName().endsWith(type.getSimpleName())) {
                        name = clazz.getSimpleName().substring(0, clazz.getSimpleName().length() - type.getSimpleName().length()).toLowerCase();
                    } else {
                        throw new IllegalStateException("No such extension name for the class " + clazz.getName() + " in the config " + resourceURL);
                    }
                }
            }
            String[] names = NAME_SEPARATOR.split(name);//通过","（左右两边允许有任意空格）分割字符串
            if (names != null && names.length > 0) {
                Activate activate = clazz.getAnnotation(Activate.class);//获取类上面的Activate注解
                if (activate != null) {//cachedActivates添加类别名和Activate注解
                    cachedActivates.put(names[0], activate);
                }
                for (String n : names) {
                    if (!cachedNames.containsKey(clazz)) {
                        cachedNames.put(clazz, n);
                    }
                    Class<?> c = extensionClasses.get(n);
                    if (c == null) {
                        extensionClasses.put(n, clazz);
                    } else if (c != clazz) {
                        throw new IllegalStateException("Duplicate extension " + type.getName() + " name " + n + " on " + c.getName() + " and " + clazz.getName());
                    }
                }
            }
        }
    }

    /**
     * 类内部有一个参数的构造函数则返回true（并且构造函数的参数为type类型，clazz是type类或type类的子类），其他情况返回false
     */
    private boolean isWrapperClass(Class<?> clazz) {
        try {
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * 返回类里面Extension注解的value；
     * 如果没有Extension注解；接着判断class是否以type名结尾；如果是则返回class类前缀名小写，否则返回class类全称小写
     */
    @SuppressWarnings("deprecation")
    private String findAnnotationName(Class<?> clazz) {
        com.alibaba.dubbo.common.Extension extension = clazz.getAnnotation(com.alibaba.dubbo.common.Extension.class);
        if (extension == null) {//如果类里面没有Extension注解
            String name = clazz.getSimpleName();//返回类名，如com.xxx.PrefixApp.class.getSimpleName()返回PrefixApp
            if (name.endsWith(type.getSimpleName())) {//如果类名以type名结束，如clazz是PrefixApp，type是App
                name = name.substring(0, name.length() - type.getSimpleName().length());//name属性就是剔除结尾的前缀
            }
            return name.toLowerCase();//转小写
        }
        return extension.value();
    }

    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() {
        try {
            return injectExtension((T) getAdaptiveExtensionClass().newInstance());
        } catch (Exception e) {
            throw new IllegalStateException("Can not create adaptive extension " + type + ", cause: " + e.getMessage(), e);
        }
    }

    /**
     * 1.判断类有没有带Adaptive注解，如果有则立即返回
     * 2.如果没有则创建一个$Adaptive类，并编译返回（需要注意）
     */
    private Class<?> getAdaptiveExtensionClass() {
        getExtensionClasses();
        if (cachedAdaptiveClass != null) {
            return cachedAdaptiveClass;
        }
        return cachedAdaptiveClass = createAdaptiveExtensionClass();
    }

    /**
     * 创建一个$Adaptive的java代码，并编译成class，type没有唯一的Adaptive类，则这里为type接口创建一个Adaptive类，但是仅对Adaptive注解修饰的方法创建适配方法，其他方法都会抛异常
     */
    private Class<?> createAdaptiveExtensionClass() {
        String code = createAdaptiveExtensionClassCode();
        ClassLoader classLoader = findClassLoader();
        //默认是javassist Compiler
        com.alibaba.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        return compiler.compile(code, classLoader);
    }

    /**
     * 为接口创建一个$Adaptive类，需要注意的是仅仅会对接口里面Adaptive注解修饰的方法创建适配方法，其他方法都是直接抛异常
     * @return
     */
    private String createAdaptiveExtensionClassCode() {
        StringBuilder codeBuilder = new StringBuilder();
        Method[] methods = type.getMethods();
        boolean hasAdaptiveAnnotation = false;
        for (Method m : methods) {
            if (m.isAnnotationPresent(Adaptive.class)) {
                hasAdaptiveAnnotation = true;
                break;
            }
        }
        // 如果没有Adaptive标注的方法则无需创建$Adaptive类
        if (!hasAdaptiveAnnotation)
            throw new IllegalStateException("No adaptive method on extension " + type.getName() + ", refuse to create the adaptive class!");

        codeBuilder.append("package ").append(type.getPackage().getName()).append(";");
        codeBuilder.append("\nimport ").append(ExtensionLoader.class.getName()).append(";");
        codeBuilder.append("\npublic class ").append(type.getSimpleName()).append("$Adaptive").append(" implements ").append(type.getCanonicalName()).append(" {");

        for (Method method : methods) {
            Class<?> rt = method.getReturnType();//返回类型
            Class<?>[] pts = method.getParameterTypes();//参数类型数组
            Class<?>[] ets = method.getExceptionTypes();//可能抛出的异常类型数组

            Adaptive adaptiveAnnotation = method.getAnnotation(Adaptive.class);
            StringBuilder code = new StringBuilder(512);
            if (adaptiveAnnotation == null) {//如果方法没有Adaptive注解
                code.append("throw new UnsupportedOperationException(\"method ")
                        .append(method.toString()).append(" of interface ")
                        .append(type.getName()).append(" is not adaptive method!\");");
            } else {
                int urlTypeIndex = -1;
                for (int i = 0; i < pts.length; ++i) {
                    if (pts[i].equals(URL.class)) {
                        urlTypeIndex = i;
                        break;
                    }
                }
                // found parameter in URL type
                if (urlTypeIndex != -1) {
                    // Null Point check
                    String s = String.format("\nif (arg%d == null) throw new IllegalArgumentException(\"url == null\");",
                            urlTypeIndex);
                    code.append(s);

                    s = String.format("\n%s url = arg%d;", URL.class.getName(), urlTypeIndex);
                    code.append(s);
                }
                // did not find parameter in URL type
                else {
                    String attribMethod = null;

                    // find URL getter method
                    LBL_PTS:
                    for (int i = 0; i < pts.length; ++i) {
                        Method[] ms = pts[i].getMethods();
                        for (Method m : ms) {
                            String name = m.getName();
                            if ((name.startsWith("get") || name.length() > 3)
                                    && Modifier.isPublic(m.getModifiers())
                                    && !Modifier.isStatic(m.getModifiers())
                                    && m.getParameterTypes().length == 0
                                    && m.getReturnType() == URL.class) {
                                urlTypeIndex = i;
                                attribMethod = name;
                                break LBL_PTS;
                            }
                        }
                    }
                    if (attribMethod == null) {
                        throw new IllegalStateException("fail to create adaptive class for interface " + type.getName()
                                + ": not found url parameter or url attribute in parameters of method " + method.getName());
                    }

                    // Null point check
                    String s = String.format("\nif (arg%d == null) throw new IllegalArgumentException(\"%s argument == null\");",
                            urlTypeIndex, pts[urlTypeIndex].getName());
                    code.append(s);
                    s = String.format("\nif (arg%d.%s() == null) throw new IllegalArgumentException(\"%s argument %s() == null\");",
                            urlTypeIndex, attribMethod, pts[urlTypeIndex].getName(), attribMethod);
                    code.append(s);

                    s = String.format("%s url = arg%d.%s();", URL.class.getName(), urlTypeIndex, attribMethod);
                    code.append(s);
                }

                String[] value = adaptiveAnnotation.value();
                // value is not set, use the value generated from class name as the key
                if (value.length == 0) {
                    char[] charArray = type.getSimpleName().toCharArray();
                    StringBuilder sb = new StringBuilder(128);
                    for (int i = 0; i < charArray.length; i++) {
                        if (Character.isUpperCase(charArray[i])) {
                            if (i != 0) {
                                sb.append(".");
                            }
                            sb.append(Character.toLowerCase(charArray[i]));
                        } else {
                            sb.append(charArray[i]);
                        }
                    }
                    value = new String[]{sb.toString()};
                }

                boolean hasInvocation = false;
                for (int i = 0; i < pts.length; ++i) {
                    if (pts[i].getName().equals("com.alibaba.dubbo.rpc.Invocation")) {
                        // Null Point check
                        String s = String.format("\nif (arg%d == null) throw new IllegalArgumentException(\"invocation == null\");", i);
                        code.append(s);
                        s = String.format("\nString methodName = arg%d.getMethodName();", i);
                        code.append(s);
                        hasInvocation = true;
                        break;
                    }
                }

                String defaultExtName = cachedDefaultName;
                String getNameCode = null;
                for (int i = value.length - 1; i >= 0; --i) {
                    if (i == value.length - 1) {
                        if (null != defaultExtName) {
                            if (!"protocol".equals(value[i]))
                                if (hasInvocation)
                                    getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                                else
                                    getNameCode = String.format("url.getParameter(\"%s\", \"%s\")", value[i], defaultExtName);
                            else
                                getNameCode = String.format("( url.getProtocol() == null ? \"%s\" : url.getProtocol() )", defaultExtName);
                        } else {
                            if (!"protocol".equals(value[i]))
                                if (hasInvocation)
                                    getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                                else
                                    getNameCode = String.format("url.getParameter(\"%s\")", value[i]);
                            else
                                getNameCode = "url.getProtocol()";
                        }
                    } else {
                        if (!"protocol".equals(value[i]))
                            if (hasInvocation)
                                getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                            else
                                getNameCode = String.format("url.getParameter(\"%s\", %s)", value[i], getNameCode);
                        else
                            getNameCode = String.format("url.getProtocol() == null ? (%s) : url.getProtocol()", getNameCode);
                    }
                }
                code.append("\nString extName = ").append(getNameCode).append(";");
                // check extName == null?
                String s = String.format("\nif(extName == null) " +
                                "throw new IllegalStateException(\"Fail to get extension(%s) name from url(\" + url.toString() + \") use keys(%s)\");",
                        type.getName(), Arrays.toString(value));
                code.append(s);

                s = String.format("\n%s extension = (%<s)%s.getExtensionLoader(%s.class).getExtension(extName);",
                        type.getName(), ExtensionLoader.class.getSimpleName(), type.getName());
                code.append(s);

                // return statement
                if (!rt.equals(void.class)) {
                    code.append("\nreturn ");
                }

                s = String.format("extension.%s(", method.getName());
                code.append(s);
                for (int i = 0; i < pts.length; i++) {
                    if (i != 0)
                        code.append(", ");
                    code.append("arg").append(i);
                }
                code.append(");");
            }

            codeBuilder.append("\npublic ").append(rt.getCanonicalName()).append(" ").append(method.getName()).append("(");
            for (int i = 0; i < pts.length; i++) {
                if (i > 0) {
                    codeBuilder.append(", ");
                }
                codeBuilder.append(pts[i].getCanonicalName());
                codeBuilder.append(" ");
                codeBuilder.append("arg").append(i);
            }
            codeBuilder.append(")");
            if (ets.length > 0) {
                codeBuilder.append(" throws ");
                for (int i = 0; i < ets.length; i++) {
                    if (i > 0) {
                        codeBuilder.append(", ");
                    }
                    codeBuilder.append(ets[i].getCanonicalName());
                }
            }
            codeBuilder.append(" {");
            codeBuilder.append(code.toString());
            codeBuilder.append("\n}");
        }
        codeBuilder.append("\n}");
        if (logger.isDebugEnabled()) {
            logger.debug(codeBuilder.toString());
        }
        return codeBuilder.toString();
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[" + type.getName() + "]";
    }

}
```