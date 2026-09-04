package com.yxpil.mq.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * 事件总线 — 发布/订阅核心，支持通配符、优先级、错误隔离。
 *
 * <pre>
 * 使用示例:
 *   EventBus bus = EventBus.getInstance();
 *   bus.on("user:login", e -> log("用户登录: " + e.data()));
 *   bus.on("user:*", 10, e -> log("用户事件: " + e.topic()));     // 低优先级通配
 *   bus.emit("user:login", Map.of("uid", 42));
 * </pre>
 *
 * 通配符规则:
 *   *  匹配单段  (如 user:* 匹配 user:login 但不匹配 user:login:success)
 *   ** 匹配多段  (如 user:** 匹配 user:login:success)
 */
public final class EventBus {

    // ==================== 单例 ====================

    private static final EventBus INSTANCE = new EventBus();

    private EventBus() {}

    public static EventBus getInstance() {
        return INSTANCE;
    }

    // ==================== 数据结构 ====================

    /** topic → 有序监听器列表 (按 priority ASC) */
    private final Map<String, List<Listener>> listeners = new ConcurrentHashMap<>();

    /** 编译后的通配符 → Pattern */
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    /** 正在 emit 时执行的延迟 off 操作 */
    private final ThreadLocal<Deque<Runnable>> pendingRemovals = ThreadLocal.withInitial(ArrayDeque::new);

    /** 防止 emit 重入时并发修改 */
    private final ThreadLocal<Integer> emitDepth = ThreadLocal.withInitial(() -> 0);

    // ==================== 事件对象 ====================

    public record Event(
        String topic,
        Object payload,
        long timestamp
    ) {
        /** 便捷方法: 泛型获取 payload */
        @SuppressWarnings("unchecked")
        public <T> T data() { return (T) payload; }
    }

    // ==================== 公共 API ====================

    /**
     * 订阅事件，默认优先级 50。
     */
    public EventBus on(String topic, Consumer<Event> handler) {
        return on(topic, 50, handler);
    }

    /**
     * 订阅事件，指定优先级 (越小越先执行)。
     */
    public EventBus on(String topic, int priority, Consumer<Event> handler) {
        listeners.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
                 .add(new Listener(priority, handler));
        sort(topic);
        return this;
    }

    /**
     * 一次性订阅：触发一次后自动取消。
     */
    public EventBus once(String topic, Consumer<Event> handler) {
        Consumer<Event> wrapper = new Consumer<>() {
            @Override
            public void accept(Event e) {
                off(topic, this);
                handler.accept(e);
            }
        };
        return on(topic, wrapper);
    }

    /**
     * 取消某个 topic 上的某个 handler。
     */
    public EventBus off(String topic, Consumer<Event> handler) {
        if (emitDepth.get() > 0) {
            // 正在 emit 中，延迟到 emit 结束后执行
            pendingRemovals.get().push(() -> removeNow(topic, handler));
        } else {
            removeNow(topic, handler);
        }
        return this;
    }

    /**
     * 取消某个 topic 上的所有 handler。
     */
    public EventBus offAll(String topic) {
        listeners.remove(topic);
        return this;
    }

    /**
     * 发布同步事件（阻塞直到所有匹配的 handler 执行完毕）。
     */
    public EventBus emit(String topic, Object data) {
        Event event = new Event(topic, data, System.currentTimeMillis());
        emitDepth.set(emitDepth.get() + 1);
        try {
            // 精确匹配
            List<Listener> exact = listeners.get(topic);
            if (exact != null) dispatch(event, exact);

            // 通配符匹配
            for (Map.Entry<String, List<Listener>> entry : listeners.entrySet()) {
                if (entry.getKey().equals(topic)) continue;
                if (matches(topic, entry.getKey())) {
                    dispatch(event, entry.getValue());
                }
            }
        } finally {
            int depth = emitDepth.get() - 1;
            emitDepth.set(depth);
            if (depth == 0) {
                // 执行延迟的 off
                Deque<Runnable> pending = pendingRemovals.get();
                while (!pending.isEmpty()) {
                    pending.pop().run();
                }
            }
        }
        return this;
    }

    /**
     * 发布事件（无数据负载）。
     */
    public EventBus emit(String topic) {
        return emit(topic, null);
    }

    /** 清空所有监听器。 */
    public void clear() {
        listeners.clear();
        patternCache.clear();
    }

    /** 当前 topic 数量。 */
    public int topicCount() {
        return listeners.size();
    }

    // ==================== 内部方法 ====================

    private void dispatch(Event event, List<Listener> list) {
        for (Listener l : list) {
            try {
                l.handler.accept(event);
            } catch (Exception ex) {
                // 错误隔离：单个 handler 异常不影响其他
                System.err.printf("[EventBus] 监听器异常 topic=%s priority=%d: %s%n",
                    event.topic(), l.priority, ex.getMessage());
            }
        }
    }

    private void removeNow(String topic, Consumer<Event> handler) {
        List<Listener> list = listeners.get(topic);
        if (list != null) {
            list.removeIf(l -> l.handler.equals(handler));
            if (list.isEmpty()) listeners.remove(topic);
        }
    }

    /** 检查 topic 是否匹配通配符 pattern */
    private boolean matches(String topic, String pattern) {
        if (!pattern.contains("*")) return false;  // 精确匹配在上游已处理
        Pattern p = patternCache.computeIfAbsent(pattern, EventBus::compile);
        return p.matcher(topic).matches();
    }

    /** 将通配符模式编译为正则 */
    private static Pattern compile(String raw) {
        StringBuilder sb = new StringBuilder("^");
        for (String seg : raw.split(":")) {
            sb.append(":");
            if ("**".equals(seg)) {
                sb.append(".*");
            } else if ("*".equals(seg)) {
                sb.append("[^:]+");
            } else {
                sb.append(Pattern.quote(seg));
            }
        }
        // 去掉开头的 :
        if (sb.length() > 1 && sb.charAt(1) == ':') sb.deleteCharAt(1);
        sb.append("$");
        return Pattern.compile(sb.toString());
    }

    private void sort(String topic) {
        List<Listener> list = listeners.get(topic);
        if (list != null) list.sort(Comparator.comparingInt(l -> l.priority));
    }

    // ==================== 内部类 ====================

    private record Listener(int priority, Consumer<Event> handler) {}
}
