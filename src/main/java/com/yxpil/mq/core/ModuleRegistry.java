package com.yxpil.mq.core;

import com.yxpil.mq.config.ConfigModule;

import java.lang.reflect.Constructor;
import java.util.*;

/**
 * 模块注册中心 — 全局单例，管理所有模块的生命周期和依赖关系。
 *
 * <pre>
 * 使用示例:
 *   ModuleRegistry registry = ModuleRegistry.getInstance();
 *   registry.register(ConfigModule.class)
 *           .register(BrokerModule.class)
 *           .register(TopicModule.class);
 *   registry.startAll();   // 拓扑排序 → init → start
 *   // ... 业务运行 ...
 *   registry.stopAll();    // 逆序 stop
 *   registry.destroyAll(); // 逆序 destroy
 * </pre>
 *
 * 核心特性:
 *   - 拓扑排序保证依赖顺序 (Kahn 算法 + 循环检测)
 *   - 模块实例单例 (同一 name 只存在一个实例)
 *   - 未声明的隐式依赖自动补全
 *   - 依赖树可视化
 */
public final class ModuleRegistry {

    // ==================== 单例 ====================

    private static final ModuleRegistry INSTANCE = new ModuleRegistry();

    private ModuleRegistry() {}

    public static ModuleRegistry getInstance() {
        return INSTANCE;
    }


    // ==================== 状态 ====================

    private enum State { NEW, INITIALIZED, STARTED, STOPPED, DESTROYED }
    private State state = State.NEW;

    /** 已注册的模块类 (保持注册顺序) */
    private final LinkedHashMap<String, Class<? extends Module>> classMap = new LinkedHashMap<>();

    /** 已实例化的模块 (name → instance) */
    private final Map<String, Module> instances = new LinkedHashMap<>();

    /** 拓扑排序后的模块名列表 (init/start 顺序) */
    private List<String> sortedNames;

    // ==================== 注册 ====================

    /**
     * 注册一个模块类。返回 this 支持链式调用。
     */
    @SuppressWarnings("unchecked")
    public ModuleRegistry register(Class<? extends Module> clazz) {
        if (state != State.NEW) {
            throw new IllegalStateException("只能在 NEW 状态下注册模块，当前: " + state);
        }
        try {
            Constructor<? extends Module> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);
            Module instance = ctor.newInstance();
            String name = instance.name();

            if (classMap.containsKey(name)) {
                throw new IllegalArgumentException(
                    "模块名冲突: [" + name + "] 已被 " + classMap.get(name).getSimpleName() + " 使用");
            }
            classMap.put(name, clazz);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("无法实例化模块: " + clazz.getName(), e);
        }
        return this;
    }

    // ==================== 生命周期 ====================

    /**
     * 启动所有模块：拓扑排序 → init → start。
     */
    public ModuleRegistry startAll() {
        if (state != State.NEW) {
            throw new IllegalStateException("只能从 NEW 状态启动，当前: " + state);
        }
        // 1. 拓扑排序
        sortedNames = topoSort();
        // 2. 实例化
        for (String name : sortedNames) {
            instantiate(name);
        }
        // 绑定事件总线与注册中心 (模块 init 前必须完成绑定)
        for (String name : sortedNames) {
            instances.get(name)._bind(EventBus.getInstance(), this);
        }
        // 3. init 按依赖顺序
        state = State.INITIALIZED;
        for (String name : sortedNames) {
            Module m = instances.get(name);
            System.out.printf("[Registry] init  → %-15s (依赖: %s)%n",
                m.name(), m.dependencies().isEmpty() ? "无" : m.dependencies());
            m.init();
        }
        // 4. start 按依赖顺序
        state = State.STARTED;
        for (String name : sortedNames) {
            Module m = instances.get(name);
            System.out.printf("[Registry] start → %s%n", m.name());
            m.start();
        }
        return this;
    }

    /**
     * 停止所有模块：逆依赖顺序。
     */
    public ModuleRegistry stopAll() {
        if (state != State.STARTED) {
            throw new IllegalStateException("只能从 STARTED 状态停止，当前: " + state);
        }
        state = State.STOPPED;
        List<String> reversed = new ArrayList<>(sortedNames);
        Collections.reverse(reversed);
        for (String name : reversed) {
            Module m = instances.get(name);
            System.out.printf("[Registry] stop  → %s%n", m.name());
            m.stop();
        }
        return this;
    }

    /**
     * 销毁所有模块：逆依赖顺序，释放全部资源。不可逆。
     */
    public ModuleRegistry destroyAll() {
        if (state == State.DESTROYED) return this;
        if (state == State.STARTED) stopAll();
        state = State.DESTROYED;
        List<String> reversed = new ArrayList<>(sortedNames);
        Collections.reverse(reversed);
        for (String name : reversed) {
            Module m = instances.get(name);
            System.out.printf("[Registry] destroy → %s%n", m.name());
            m.destroy();
        }
        instances.clear();
        return this;
    }

    // ==================== 查询 ====================

    /** 获取模块实例 (单例) */
    @SuppressWarnings("unchecked")
    public <T extends Module> T get(String name) {
        return (T) instances.get(name);
    }

    /** 所有已注册模块名 */
    public Set<String> moduleNames() {
        return Collections.unmodifiableSet(classMap.keySet());
    }

    /** 当前状态 */
    public State state() { return state; }

    // ==================== 拓扑排序 (Kahn 算法) ====================

    private List<String> topoSort() {
        // 构建邻接表: 模块 → 它依赖谁
        Map<String, Set<String>> deps = new LinkedHashMap<>(); // name → 它直接依赖
        Map<String, Set<String>> revDeps = new LinkedHashMap<>(); // name → 谁依赖它

        for (String name : classMap.keySet()) {
            deps.put(name, new LinkedHashSet<>());
            revDeps.put(name, new LinkedHashSet<>());
        }

        for (String name : classMap.keySet()) {
            List<String> depList = getDependencies(name);
            for (String dep : depList) {
                // 隐式依赖自动补全: 如果 dep 未显式注册，自动注册一个空壳模块
                if (!classMap.containsKey(dep)) {
                    throw new IllegalArgumentException(
                        "模块 [" + name + "] 依赖 [" + dep + "]，但 [" + dep + "] 未注册。" +
                        "请用 register() 注册所有依赖模块。");
                }
                deps.get(name).add(dep);
                revDeps.get(dep).add(name);
            }
        }

        // Kahn 算法
        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Set<String>> e : deps.entrySet()) {
            if (e.getValue().isEmpty()) queue.add(e.getKey());
        }

        List<String> result = new ArrayList<>();
        Map<String, Set<String>> remaining = new LinkedHashMap<>();
        deps.forEach((k, v) -> remaining.put(k, new LinkedHashSet<>(v)));

        while (!queue.isEmpty()) {
            String cur = queue.poll();
            result.add(cur);
            for (String dependent : revDeps.getOrDefault(cur, Set.of())) {
                Set<String> rd = remaining.get(dependent);
                if (rd != null) {
                    rd.remove(cur);
                    if (rd.isEmpty()) queue.add(dependent);
                }
            }
        }

        if (result.size() != classMap.size()) {
            Set<String> cycle = new LinkedHashSet<>(classMap.keySet());
            cycle.removeAll(result);
            throw new IllegalStateException(
                "检测到循环依赖! 涉及模块: " + cycle +
                "\n" + printDependencyTreeInternal());
        }

        return result;
    }

    private List<String> getDependencies(String name) {
        // 用预实例化的临时对象获取依赖列表
        try {
            Constructor<? extends Module> ctor = classMap.get(name).getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance().dependencies();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("获取依赖失败: " + name, e);
        }
    }

    private void instantiate(String name) {
        if (instances.containsKey(name)) return;
        try {
            Constructor<? extends Module> ctor = classMap.get(name).getDeclaredConstructor();
            ctor.setAccessible(true);
            instances.put(name, ctor.newInstance());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("实例化失败: " + name, e);
        }
    }

    // ==================== 可视化 ====================

    /** 打印依赖树 */
    public void printDependencyTree() {
        System.out.println(printDependencyTreeInternal());
    }

    private String printDependencyTreeInternal() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══ 模块依赖树 ══╗\n");

        if (sortedNames == null) {
            // 还没排序，用注册顺序
            sortedNames = new ArrayList<>(classMap.keySet());
        }

        for (String name : sortedNames) {
            List<String> deps = new ArrayList<>();
            try {
                deps = getDependencies(name);
            } catch (Exception ignored) {}
            sb.append(String.format("║ %-20s", name));
            if (deps.isEmpty()) {
                sb.append("(无依赖)");
            } else {
                sb.append("→  ");
                sb.append(String.join(", ", deps));
            }
            sb.append("\n");
        }
        sb.append("╚══════════════════╝\n");
        return sb.toString();
    }
}
