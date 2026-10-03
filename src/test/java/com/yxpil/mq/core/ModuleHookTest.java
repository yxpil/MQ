package com.yxpil.mq.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Module 钩子便捷方法测试：验证模块通过 on()/emit() 在自身命名空间（name:）下收发事件。
 */
class ModuleHookTest {

    /** 测试用最小模块，名称固定为 "mod"。 */
    static class TestModule extends Module {
        TestModule() { super("mod"); }
    }

    private EventBus bus;
    private TestModule module;

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clear();
        module = new TestModule();
        module._bind(bus, null); // registry 传 null：本用例不触发 require()/dao()
    }

    @AfterEach
    void tearDown() {
        bus.clear();
    }

    @Test
    void moduleEmit_isNamespacedUnderModuleName() {
        List<String> topics = new ArrayList<>();
        module.on("ready", e -> topics.add(e.topic()));

        module.emit("ready", "payload-1");

        assertEquals(List.of("mod:ready"), topics, "模块内 emit 应自动加前缀 mod:");
    }

    @Test
    void moduleOn_receivesPayload() {
        AtomicReference<Object> seen = new AtomicReference<>();
        module.on("data", e -> seen.set(e.payload()));

        var payload = java.util.Map.of("k", "v");
        module.emit("data", payload);

        assertSame(payload, seen.get());
    }

    @Test
    void moduleGlobalEmit_reachesExactTopic() {
        List<String> topics = new ArrayList<>();
        bus.on("global:ping", e -> topics.add(e.topic()));

        module.emitGlobal("global:ping", null);
        assertEquals(List.of("global:ping"), topics, "emitGlobal 不自动加前缀");
    }

    @Test
    void constructor_rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new Module(" ") { });
        assertThrows(IllegalArgumentException.class, () -> new Module(null) { });
    }

    @Test
    void moduleName_exposed() {
        assertEquals("mod", module.name());
    }
}
