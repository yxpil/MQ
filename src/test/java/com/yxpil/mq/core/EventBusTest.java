package com.yxpil.mq.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EventBus 单元/钩子测试。
 *
 * <p>覆盖：订阅→触发顺序、参数传递、优先级、once 一次性、错误隔离、
 * emit 过程中 off 的延迟移除、通配符语义，以及不可信 topic/pattern 的注入鲁棒性。
 *
 * <p>本类不依赖 Spring，可在无 Spring 容器下独立运行（gradle test 阶段同样会执行）。
 */
class EventBusTest {

    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clear();
    }

    @AfterEach
    void tearDown() {
        bus.clear();
    }

    // ─────────────── 基础：注册→触发、参数传递 ───────────────

    @Test
    void onAndEmt_deliversTopicAndPayload() {
        List<String> topics = new ArrayList<>();
        List<Object> payloads = new ArrayList<>();

        bus.on("user:login", e -> {
            topics.add(e.topic());
            payloads.add(e.payload());
        });

        var data = java.util.Map.of("uid", 42);
        bus.emit("user:login", data);

        assertEquals(List.of("user:login"), topics);
        assertEquals(1, payloads.size());
        assertSame(data, payloads.get(0));

        // Event.data() 泛型还原
        AtomicReference<Object> seen = new AtomicReference<>();
        bus.on("t1", e -> seen.set(e.<java.util.Map<String, Integer>>data().get("uid")));
        bus.emit("t1", java.util.Map.of("uid", 7));
        assertEquals(7, seen.get());
    }

    @Test
    void emitWithoutListeners_isNoOp() {
        // 未注册任何钩子的 topic 被触发：不应抛异常
        assertDoesNotThrow(() -> bus.emit("no:such:topic", "x"));
        assertDoesNotThrow(() -> bus.emit("no:such:topic"));
    }

    // ─────────────── 钩子：触发顺序（优先级，越小越先） ───────────────

    @Test
    void handlers_executeInPriorityOrder() {
        List<Integer> order = new ArrayList<>();
        bus.on("evt", 50, e -> order.add(50));
        bus.on("evt", 1, e -> order.add(1));
        bus.on("evt", 30, e -> order.add(30));
        bus.on("evt", 10, e -> order.add(10));

        bus.emit("evt", null);
        assertEquals(List.of(1, 10, 30, 50), order);
    }

    // ─────────────── 钩子：失败隔离 ───────────────

    @Test
    void handlerException_doesNotBreakSubsequentHandlers() {
        List<String> reached = new ArrayList<>();

        bus.on("evt", 1, e -> { throw new RuntimeException("boom"); });
        bus.on("evt", 2, e -> reached.add("second"));
        bus.on("evt", 3, e -> reached.add("third"));

        // 异常被 EventBus 吞掉并隔离，不向外抛
        assertDoesNotThrow(() -> bus.emit("evt", null));
        assertEquals(List.of("second", "third"), reached);
    }

    // ─────────────── 钩子：once 一次性 ───────────────

    @Test
    void onceHandler_firesOnlyOnce() {
        AtomicInteger count = new AtomicInteger();
        bus.once("evt", e -> count.incrementAndGet());

        bus.emit("evt", null);
        bus.emit("evt", null);
        bus.emit("evt", null);
        assertEquals(1, count.get(), "once 钩子应只触发一次后自动注销");
    }

    // ─────────────── 钩子：off 注销 / 未注册钩子被隔离 ───────────────

    @Test
    void off_unregistersHandler() {
        AtomicInteger count = new AtomicInteger();
        java.util.function.Consumer<EventBus.Event> h = e -> count.incrementAndGet();
        bus.on("evt", h);

        bus.emit("evt", null);
        bus.off("evt", h);
        bus.emit("evt", null);
        assertEquals(1, count.get());
    }

    @Test
    void off_unregisteredHandler_isNoOp() {
        AtomicInteger count = new AtomicInteger();
        bus.on("evt", e -> count.incrementAndGet());
        java.util.function.Consumer<EventBus.Event> stranger = e -> { };

        // 对从未注册的 handler 调用 off：不应抛异常，也不应影响已注册钩子
        assertDoesNotThrow(() -> bus.off("evt", stranger));
        bus.emit("evt", null);
        assertEquals(1, count.get());
    }

    @Test
    void offAll_removesEveryHandlerOnTopic() {
        bus.on("evt", e -> { });
        bus.on("evt", e -> { });
        bus.emit("evt", null);
        bus.offAll("evt");
        assertEquals(0, bus.topicCount());
    }

    // ─────────────── 钩子：emit 进行中 off 的延迟移除（失败/并发隔离） ───────────────

    @Test
    void offDuringEmit_isDeferredUntilDispatchCompletes() {
        List<String> order = new ArrayList<>();
        java.util.function.Consumer<EventBus.Event> selfRemoving = new java.util.function.Consumer<>() {
            @Override
            public void accept(EventBus.Event e) {
                order.add("self");
                bus.off("evt", this); // emit 中注销自己
            }
        };
        bus.on("evt", 1, selfRemoving);
        bus.on("evt", 2, e -> order.add("after"));

        bus.emit("evt", null);
        assertEquals(List.of("self", "after"), order, "emit 中注销不应中断后续钩子");

        // emit 结束后延迟 off 已生效：self 不再触发，但 after 仍在
        order.clear();
        bus.emit("evt", null);
        assertEquals(List.of("after"), order, "延迟 off 后 self 应被移除，after 不受影响");
    }

    // ─────────────── 通配符语义 ───────────────

    @Test
    void wildcard_singleSegment_matchesOneLevelOnly() {
        AtomicInteger hits = new AtomicInteger();
        bus.on("user:*", e -> hits.incrementAndGet());

        bus.emit("user:login", null);
        assertEquals(1, hits.get(), "user:* 应匹配 user:login");

        bus.emit("user:login:success", null);
        assertEquals(1, hits.get(), "user:* 不应匹配多段 user:login:success");
    }

    @Test
    void wildcard_multiSegment_matchesNestedLevels() {
        AtomicInteger hits = new AtomicInteger();
        bus.on("user:**", e -> hits.incrementAndGet());

        bus.emit("user:login:success:deep", null);
        assertEquals(1, hits.get());

        bus.emit("other:x", null);
        assertEquals(1, hits.get(), "user:** 不应跨前缀匹配");
    }

    @Test
    void exactMatchAndWildcard_coexist() {
        List<String> order = new ArrayList<>();
        bus.on("user:login", 10, e -> order.add("exact"));
        bus.on("user:*", 1, e -> order.add("wildcard"));

        bus.emit("user:login", null);
        // EventBus 先派发精确匹配组，再派发通配匹配组（组间与优先级无关）
        assertEquals(List.of("exact", "wildcard"), order);
    }

    // ─────────────── 注入鲁棒性：不可信 topic / pattern ───────────────

    /**
     * 注入面：订阅 pattern 会被编译为正则。攻击者控制的 pattern 若含正则元字符，
     * 必须被当字面量处理（仅 * 和 ** 特殊），不能引发正则注入/过度匹配/ReDoS。
     */
    @Test
    void injection_regexMetacharactersInPattern_areTreatedLiterally() {
        AtomicInteger hits = new AtomicInteger();
        // 含正则元字符：若被当正则，"a.b+c" 会匹配 "aXbYc" 等
        bus.on("a.b+c", e -> hits.incrementAndGet());

        bus.emit("a.b+c", null);
        assertEquals(1, hits.get(), "字面 pattern 应精确匹配自身");

        bus.emit("aXbYc", null);
        assertEquals(1, hits.get(), "正则元字符被引用后不得过度匹配（防正则注入）");

        bus.emit("abbbc", null);
        assertEquals(1, hits.get(), "'+' 不得被解释为重复量词");
    }

    @Test
    void injection_pathTraversalTopic_isLiteral() {
        // EventBus 以 ':' 分段路由，'../' 之类路径穿越字符只是普通文本，不得被特殊解释
        AtomicInteger hits = new AtomicInteger();
        bus.on("topic:../secret", e -> hits.incrementAndGet());

        bus.emit("topic:../secret", null);
        assertEquals(1, hits.get());

        // 构造穿越串不应命中正常 topic
        bus.emit("topic:..%2F..%2Fetc", null);
        assertEquals(1, hits.get());
    }

    @Test
    void injection_wildcardAbuse_doesNotEscapeNamespace() {
        AtomicInteger hits = new AtomicInteger();
        bus.on("order:*", e -> hits.incrementAndGet());

        // 同前缀、单段（含 ../ 穿越字符）：':' 才是分段符，'../' 只是字面文本，仍命中 order:*
        bus.emit("order:../secret", null);
        assertEquals(1, hits.get(), "order:* 匹配同前缀单段，'../' 不被特殊解释");

        // 跨前缀：试图用穿越串越权到 admin 命名空间，必须失败（路由按 ':' 前缀隔离）
        bus.emit("admin:order", null);
        assertEquals(1, hits.get(), "通配不得跨前缀逃逸到 admin 命名空间");

        // 多段：单段通配不得匹配 order:a:b
        bus.emit("order:a:b", null);
        assertEquals(1, hits.get());

        bus.emit("order:view", null);
        assertEquals(2, hits.get());
    }
}
