package com.yxpil.mq.core;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 模块基类 — 所有业务模块继承此类。
 *
 * <pre>
 * 使用示例:
 *   public class UserModule extends Module {
 *       static final String NAME = "user";
 *       public UserModule() { super(NAME); }
 *       &#64;Override public List&lt;String&gt; dependencies() { return List.of("db"); }
 *       &#64;Override public void init()     { dao(UserDao.class); }
 *       &#64;Override public void start()    { emit("ready"); }
 *       &#64;Override public void stop()     { log("已停止"); }
 *   }
 * </pre>
 */
public abstract class Module {

    protected final String name;
    private EventBus bus;
    private ModuleRegistry registry;
    private boolean destroyed = false;

    protected Module(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("模块名不能为空");
        this.name = name;
    }

    // ──────────── 子类覆盖 ────────────

    /** 依赖的模块名列表 (默认无依赖) */
    public List<String> dependencies() { return Collections.emptyList(); }

    /** 初始化: 加载配置/注册监听器 (依赖已就绪) */
    protected void init() {}

    /** 启动: 开始业务逻辑 (所有模块 init 之后) */
    protected void start() {}

    /** 停止: 暂停业务 (可恢复), 默认空实现 */
    protected void stop() {}

    /** 销毁: 释放资源 (不可逆), 默认空实现 */
    protected void destroy() {}

    // ──────────── 便捷方法 ────────────

    /** 获取依赖模块 */
    @SuppressWarnings("unchecked")
    protected <T extends Module> T require(String moduleName) {
        Module m = registry.get(moduleName);
        if (m == null) throw new IllegalStateException("依赖模块未找到: " + moduleName);
        return (T) m;
    }

    /** 获取 Spring 管理的 DAO Bean */
    protected <T> T dao(Class<T> daoClass) {
        return SpringContextUtil.getBean(daoClass);
    }

    /** 在本模块命名空间下发事件 (自动加前缀 name:) */
    protected void emit(String event, Object data) {
        bus.emit(name + ":" + event, data);
    }

    /** 在本模块命名空间下监听事件 */
    protected void on(String event, Consumer<EventBus.Event> handler) {
        bus.on(name + ":" + event, Integer.MAX_VALUE, handler);
    }

    /** 监听全局事件 (通配符) */
    protected void onAny(String pattern, Consumer<EventBus.Event> handler) {
        bus.on(pattern, Integer.MAX_VALUE, handler);
    }

    /** 全局发送事件 */
    protected void emitGlobal(String topic, Object data) {
        bus.emit(topic, data);
    }

    /** 带优先级的监听 */
    protected void on(String event, int priority, Consumer<EventBus.Event> handler) {
        bus.on(name + ":" + event, priority, handler);
    }

    // ──────────── 内部用 (Registry 调用) ────────────

    void _bind(EventBus bus, ModuleRegistry registry) {
        this.bus = bus;
        this.registry = registry;
    }

    void _init() {
        if (destroyed) throw new IllegalStateException("模块 " + name + " 已销毁, 无法 init");
        init();
    }

    void _start() {
        if (destroyed) throw new IllegalStateException("模块 " + name + " 已销毁, 无法 start");
        start();
    }

    void _stop() { stop(); }

    void _destroy() {
        if (!destroyed) {
            destroy();
            destroyed = true;
        }
    }

    // ──────────── 通用 ────────────

    protected void log(String fmt, Object... args) {
        Object[] all = new Object[args.length + 1];
        all[0] = name;
        System.arraycopy(args, 0, all, 1, args.length);
        System.out.printf("[%s] " + fmt + "%n", all);
    }

    public String name() { return name; }
    public boolean isDestroyed() { return destroyed; }

    @Override
    public String toString() { return "Module{" + name + "}"; }
}
