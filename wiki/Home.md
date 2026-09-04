# MQ Wiki / MQ 维基

**EN**: [MQ](https://github.com/yxpil/MQ) is a lightweight event-driven messaging framework built on Spring Boot 4 + JPA + H2. Features live in modules (config / broker / topic / consumer) that are registered into a `ModuleRegistry`, ordered by declared dependencies (topological sort), and communicate through a singleton `EventBus` with `*` / `**` wildcard topics, priorities and per-handler error isolation. State is persisted with Spring Data JPA into an in-memory H2 database.

**中文**：[MQ](https://github.com/yxpil/MQ) 是基于 Spring Boot 4 + JPA + H2 的轻量事件驱动消息框架。功能以模块（config / broker / topic / consumer）形式组织，注册到 `ModuleRegistry` 后按声明的依赖做拓扑排序启动，模块间通过单例 `EventBus` 通信（支持 `*` / `**` 通配 topic、优先级、单 handler 错误隔离），状态经 Spring Data JPA 持久化到 H2 内存数据库。

## Build & Run / 构建与运行

```bash
git clone https://github.com/yxpil/MQ.git
cd MQ
./gradlew test        # run tests / 运行测试
./gradlew build       # build jars / 构建产物
./gradlew bootRun     # run the framework demo / 运行框架演示
```

Requires JDK 17+ (Gradle toolchain auto-provisions). / 需要 JDK 17+（Gradle toolchain 自动下载）。

## Quick integration / 快速接入

```java
ModuleRegistry registry = ModuleRegistry.getInstance();
registry.register(ConfigModule.class)
        .register(OrderModule.class);   // extends Module, dependencies() -> List.of("config")
registry.startAll();                    // topo sort → bind → init → start
registry.stopAll();                     // reverse order / 逆序停止

EventBus bus = EventBus.getInstance();
bus.on("user:login", e -> System.out.println(e.data()));
bus.emit("user:login", Map.of("uid", 42));
```

See [Protocol](Protocol.md) for the event topic naming rules, lifecycle events and database tables / 事件 topic 命名规则、生命周期事件与数据库表见 [Protocol](Protocol.md)。

## FAQ

**Q: Is there an HTTP API? / 有 HTTP API 吗？**
A: No. The framework surface is the Java API; the demo (`bootRun`) runs headless and exits after a graceful shutdown. / 没有。框架对外是 Java API；演示（`bootRun`）为纯控制台运行，优雅停机后退出。

**Q: Why can't I open the H2 web console at `localhost:8080/h2-console`? / 为什么打不开 H2 控制台？**
A: The project does not include a servlet web starter, so no HTTP server runs. Add `spring-boot-starter-web` if you want the console. / 项目未引入 Servlet Web 启动器，因此没有 HTTP 服务器；需要控制台请自行添加 `spring-boot-starter-web`。

**Q: Where is the data stored? / 数据存在哪里？**
A: In-memory H2 (`jdbc:h2:mem:mqdb`), dropped when the process exits. / 存于内存 H2（`jdbc:h2:mem:mqdb`），进程结束即释放。

**Q: What Java version? / 需要什么 Java 版本？**
A: Java 17+ (Gradle toolchain). / Java 17+（Gradle toolchain）。
