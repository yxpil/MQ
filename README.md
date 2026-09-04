# MQ

Lightweight event-driven messaging framework built on Spring Boot 4 + JPA + H2 — a module system with lifecycle management, dependency-aware startup and a wildcard-capable event bus.

[![Release](https://img.shields.io/github/v/release/yxpil/MQ?style=flat-square)](https://github.com/yxpil/MQ/releases/latest) [![Downloads](https://img.shields.io/github/downloads/yxpil/MQ/total?style=flat-square)](https://github.com/yxpil/MQ/releases) [![License](https://img.shields.io/github/license/yxpil/MQ?style=flat-square)](https://github.com/yxpil/MQ/blob/main/LICENSE) [![CI](https://img.shields.io/github/actions/workflow/status/yxpil/MQ/ci.yml?style=flat-square&branch=main&label=CI)](https://github.com/yxpil/MQ/actions) [![Platform](https://img.shields.io/badge/platform-ubuntu%20%C2%B7%20macos%20%C2%B7%20windows-black?style=flat-square)](https://github.com/yxpil/MQ/actions) [![Java](https://img.shields.io/badge/Java-17-black?style=flat-square)](https://github.com/yxpil/MQ/blob/main/build.gradle)

---

## About

MQ is a small event-driven framework that demonstrates a clean module architecture on top of Spring Boot 4 (JPA + H2):

- Every feature lives in its own package as a `Module` (config / broker / topic / consumer).
- Modules are registered into a global `ModuleRegistry`, which topologically sorts them by declared dependencies, initializes and starts them in order, and tears them down in reverse.
- All inter-module communication goes through a singleton `EventBus` (synchronous publish/subscribe with `*` / `**` wildcard topics, priorities and per-handler error isolation).
- Module state (brokers, topics, consumers, config) is persisted via Spring Data JPA into an in-memory H2 database.

`MqApplication` boots the whole stack as a demo: it registers the four built-in modules, prints the dependency tree, starts them, creates sample topics/consumers, then shuts down gracefully. Run it with `./gradlew bootRun` and watch the lifecycle in the console.

## Features

- **EventBus** (`com.yxpil.mq.core.EventBus`)
  - Synchronous pub/sub: `on` / `once` / `off` / `offAll` / `emit`
  - Wildcard topics: `*` matches one segment (`user:*`), `**` matches many (`user:**`)
  - Handler priorities (lower runs first), deferred unsubscribe during `emit`, per-handler error isolation
- **Module lifecycle** (`com.yxpil.mq.core.Module`)
  - Hooks: `init()` → `start()` → `stop()` → `destroy()` with destroyed-state protection
  - Helpers: `require("module")` for dependencies, `dao(XxxDao.class)` for Spring beans, namespaced `emit`/`on` (topic = `module:event`), `onAny` for global patterns, `log`
- **ModuleRegistry** (`com.yxpil.mq.core.ModuleRegistry`)
  - Chained `register(Class)`, duplicate-name detection
  - Kahn topological sort with cycle detection; implicit dependencies must be registered explicitly (clear error otherwise)
  - `startAll()` / `stopAll()` / `destroyAll()`, `get(name)`, `printDependencyTree()`
- **Persistence (Spring Data JPA + H2)**
  - `mq_config`, `brokers`, `topics`, `consumers` tables auto-generated (`ddl-auto=create-drop`)
  - Derived queries (`findByName`, `findByStatus`, `findByHostAndPort`, …)
- **Built-in modules**
  - `config` — single-row configuration (broker host/port, max connections), auto-seeded on first boot
  - `broker` — creates or reuses a broker record and drives its status (`STARTING` → `RUNNING` → `STOPPED`), emits `broker:ready`
  - `topic` — topic create/delete/query with partitions, emits `topic:created`
  - `consumer` — consumer registration, group membership and `startConsuming`, listens to `topic:created`
- **Spring interop** — modules are plain objects, but can fetch any Spring bean through `SpringContextUtil`

## Architecture

| Module | Depends on | Responsibility | Key API |
|---------|------------|----------------|---------|
| `core` | — | EventBus, Module base class, ModuleRegistry, Spring bridge | `EventBus.getInstance()`, `ModuleRegistry.getInstance()` |
| `config` | — | Load/persist single-row MQ config | `updateConfig(host, port, maxConn)` |
| `broker` | `config` | Broker record lifecycle + persistence | `listRunningBrokers()`, `getCurrentBroker()` |
| `topic` | `config`, `broker` | Topic CRUD + persistence, emits `topic:created` | `createTopic(name, partitions)`, `deleteTopic(name)`, `findByName(name)` |
| `consumer` | `broker`, `topic` | Consumer registration/subscription | `register(id, topic, group)`, `startConsuming(id)` |

Startup order is derived automatically: `config → broker → topic → consumer`; shutdown runs in reverse.

## Quick start

Requirements: JDK 17+ (the Gradle toolchain provisions one automatically if missing).

```bash
git clone https://github.com/yxpil/MQ.git
cd MQ
./gradlew bootRun
```

You will see the real module lifecycle (abridged):

```
═══════════════════════════════════════════
  MQ 事件驱动框架启动 (Spring Boot 4.1)
═══════════════════════════════════════════

📊 模块依赖关系:
╔══ 模块依赖树 ══╗
║ config              (无依赖)
║ broker              →  config
║ topic               →  config, broker
║ consumer            →  broker, topic
╚══════════════════╝

🚀 启动所有模块...
[Registry] init  → config          (依赖: 无)
[Registry] init  → broker          (依赖: [config])
...
📝 创建测试 Topic...
[topic] 创建 Topic: order-events (partitions=3)
[consumer] 注册消费者: consumer-1 → order-events (group=order-group)
...
🛑 正在停止...
[broker] 代理已停止
```

The demo runs headless (no HTTP endpoints); it exits after the graceful shutdown. There are no REST controllers — the framework surface is the Java API below plus the `@SpringBootTest` (`./gradlew test`) that exercises the full lifecycle.

> Note: `application.properties` enables `spring.h2.console.enabled`, but the H2 web console only becomes reachable if you add a servlet web starter (e.g. `spring-boot-starter-web`). Without it, data lives in the in-memory H2 instance `jdbc:h2:mem:mqdb` for the duration of the process.

## Module integration

**Subscribe / publish on the EventBus directly:**

```java
EventBus bus = EventBus.getInstance();

bus.on("user:login", e -> System.out.println("payload = " + e.data()));
bus.on("user:*", 10, e -> System.out.println("topic = " + e.topic())); // lower priority runs later
bus.once("app:bootstrap", e -> System.out.println("only once"));

bus.emit("user:login", Map.of("uid", 42));
```

**Write your own module:**

```java
public class OrderModule extends Module {

    public static final String NAME = "order";

    public OrderModule() { super(NAME); }

    @Override
    public List<String> dependencies() { return List.of("config"); }

    @Override
    protected void init() {          // deps are ready here
        onAny("topic:created", e -> { /* react to global events */ });
    }

    @Override
    protected void start() {         // all modules are initialized
        emit("ready", null);         // publishes on topic "order:ready"
    }
}
```

**Register and drive it:**

```java
ModuleRegistry registry = ModuleRegistry.getInstance();

registry.register(ConfigModule.class)   // dependencies first (order is irrelevant,
        .register(OrderModule.class);   // the registry topologically sorts)

registry.startAll();                    // topo sort → bind → init → start
// ... business runs; EventBus topics "order:ready" etc. are live ...
registry.stopAll();                     // reverse order
registry.destroyAll();                  // release resources (irreversible)
```

**Access the database from a module** — declare a Spring Data JPA repository, then:

```java
MyDao dao = dao(MyDao.class);   // Module helper, delegates to SpringContextUtil
```

## Build

```bash
./gradlew test        # run tests (@SpringBootTest full lifecycle)
./gradlew build       # compile + test + jar
./gradlew bootRun     # run the framework demo
```

Artifacts land in `build/libs/` (`MQ-<version>.jar` boot jar, `MQ-<version>-plain.jar` library jar). CI runs `test` on ubuntu / macos / windows; releases attach the jar — see [Releases](https://github.com/yxpil/MQ/releases).

## License

[Apache-2.0](LICENSE)

---

MQ 是基于 Spring Boot 4 + JPA + H2 的轻量事件驱动消息框架 —— 提供模块生命周期管理、按依赖排序的启动流程，以及支持通配符的事件总线。

[![版本](https://img.shields.io/github/v/release/yxpil/MQ?style=flat-square&label=%E7%89%88%E6%9C%AC)](https://github.com/yxpil/MQ/releases/latest) [![下载](https://img.shields.io/github/downloads/yxpil/MQ/total?style=flat-square&label=%E4%B8%8B%E8%BD%BD)](https://github.com/yxpil/MQ/releases) [![许可证](https://img.shields.io/github/license/yxpil/MQ?style=flat-square&label=%E8%AE%B8%E5%8F%AF%E8%AF%81)](https://github.com/yxpil/MQ/blob/main/LICENSE) [![CI](https://img.shields.io/github/actions/workflow/status/yxpil/MQ/ci.yml?style=flat-square&branch=main&label=CI)](https://github.com/yxpil/MQ/actions)

## 简介

MQ 是一个在 Spring Boot 4（JPA + H2）之上展示清晰模块架构的小型事件驱动框架：

- 每个功能都以独立包内的 `Module` 形式存在（config / broker / topic / consumer）。
- 模块注册到全局单例 `ModuleRegistry`，注册中心按声明的依赖进行拓扑排序，按序 init → start，关闭时逆序 stop。
- 模块间通信全部经由单例 `EventBus`（同步发布/订阅，支持 `*` / `**` 通配 topic、优先级、单 handler 错误隔离）。
- 模块状态（broker、topic、consumer、config）通过 Spring Data JPA 持久化到 H2 内存数据库。

`MqApplication` 以演示形式启动整套框架：注册四个内置模块、打印依赖树、启动模块、创建示例 Topic/消费者，然后优雅停机。执行 `./gradlew bootRun` 即可在控制台观察完整生命周期。

## 功能

- **EventBus**（`com.yxpil.mq.core.EventBus`）
  - 同步发布/订阅：`on` / `once` / `off` / `offAll` / `emit`
  - 通配符 topic：`*` 匹配单段（`user:*`），`**` 匹配多段（`user:**`）
  - handler 优先级（越小越先执行）、`emit` 期间的延迟退订、单 handler 异常隔离
- **模块生命周期**（`com.yxpil.mq.core.Module`）
  - 钩子：`init()` → `start()` → `stop()` → `destroy()`，含已销毁状态保护
  - 便捷方法：`require("模块名")` 获取依赖、`dao(XxxDao.class)` 获取 Spring Bean、命名空间化的 `emit`/`on`（topic = `模块名:事件名`）、`onAny` 监听全局通配事件、`log`
- **ModuleRegistry**（`com.yxpil.mq.core.ModuleRegistry`）
  - 链式 `register(Class)`，模块重名检测
  - Kahn 拓扑排序 + 循环依赖检测；未注册的依赖会给出明确报错
  - `startAll()` / `stopAll()` / `destroyAll()`，`get(name)`，`printDependencyTree()`
- **持久化（Spring Data JPA + H2）**
  - `mq_config`、`brokers`、`topics`、`consumers` 四张表自动建表（`ddl-auto=create-drop`）
  - 派生查询（`findByName`、`findByStatus`、`findByHostAndPort` 等）
- **内置模块**
  - `config` — 单行配置（broker 主机/端口、最大连接数），首次启动自动写入默认值
  - `broker` — 创建或复用 Broker 记录并驱动状态（`STARTING` → `RUNNING` → `STOPPED`），发出 `broker:ready`
  - `topic` — Topic 创建/删除/查询（含分区数），发出 `topic:created`
  - `consumer` — 消费者注册、分组与 `startConsuming`，监听 `topic:created`
- **Spring 互通** — 模块本身不是 Spring Bean，但可通过 `SpringContextUtil` 获取任意 Spring Bean

## 架构

| 模块 | 依赖 | 职责 | 主要 API |
|------|------|------|----------|
| `core` | — | EventBus、Module 基类、ModuleRegistry、Spring 桥接 | `EventBus.getInstance()`、`ModuleRegistry.getInstance()` |
| `config` | — | 加载/持久化单行 MQ 配置 | `updateConfig(host, port, maxConn)` |
| `broker` | `config` | Broker 记录生命周期与持久化 | `listRunningBrokers()`、`getCurrentBroker()` |
| `topic` | `config`、`broker` | Topic 增删查与持久化，发出 `topic:created` | `createTopic(name, partitions)`、`deleteTopic(name)`、`findByName(name)` |
| `consumer` | `broker`、`topic` | 消费者注册/订阅 | `register(id, topic, group)`、`startConsuming(id)` |

启动顺序自动推导：`config → broker → topic → consumer`；关闭按逆序执行。

## 快速上手

环境要求：JDK 17+（缺失时 Gradle toolchain 会自动下载）。

```bash
git clone https://github.com/yxpil/MQ.git
cd MQ
./gradlew bootRun
```

可以看到真实的模块生命周期（节选）：

```
═══════════════════════════════════════════
  MQ 事件驱动框架启动 (Spring Boot 4.1)
═══════════════════════════════════════════

📊 模块依赖关系:
╔══ 模块依赖树 ══╗
║ config              (无依赖)
║ broker              →  config
║ topic               →  config, broker
║ consumer            →  broker, topic
╚══════════════════╝

🚀 启动所有模块...
[Registry] init  → config          (依赖: 无)
[Registry] init  → broker          (依赖: [config])
...
📝 创建测试 Topic...
[topic] 创建 Topic: order-events (partitions=3)
[consumer] 注册消费者: consumer-1 → order-events (group=order-group)
...
🛑 正在停止...
[broker] 代理已停止
```

演示为纯控制台运行（无 HTTP 端点），优雅停机后进程退出。项目没有 REST Controller —— 框架的对外面是下文的 Java API，以及覆盖完整生命周期的 `@SpringBootTest`（`./gradlew test`）。

> 说明：`application.properties` 开启了 `spring.h2.console.enabled`，但 H2 Web 控制台只有在加入 Servlet Web 启动器（如 `spring-boot-starter-web`）后才能访问。默认情况下，数据保存在进程内存中的 H2 实例 `jdbc:h2:mem:mqdb`，进程结束即释放。

## 模块化接入

**直接使用 EventBus 订阅/发布：**

```java
EventBus bus = EventBus.getInstance();

bus.on("user:login", e -> System.out.println("payload = " + e.data()));
bus.on("user:*", 10, e -> System.out.println("topic = " + e.topic())); // 优先级越小越后执行
bus.once("app:bootstrap", e -> System.out.println("only once"));

bus.emit("user:login", Map.of("uid", 42));
```

**编写自己的模块：**

```java
public class OrderModule extends Module {

    public static final String NAME = "order";

    public OrderModule() { super(NAME); }

    @Override
    public List<String> dependencies() { return List.of("config"); }

    @Override
    protected void init() {          // 此时依赖已就绪
        onAny("topic:created", e -> { /* 响应全局事件 */ });
    }

    @Override
    protected void start() {         // 所有模块均已 init
        emit("ready", null);         // 发布到 "order:ready"
    }
}
```

**注册并启动：**

```java
ModuleRegistry registry = ModuleRegistry.getInstance();

registry.register(ConfigModule.class)   // 依赖模块先注册（顺序随意，
        .register(OrderModule.class);   // 注册中心会做拓扑排序）

registry.startAll();                    // 拓扑排序 → 绑定 → init → start
// ... 业务运行，"order:ready" 等事件 topic 已生效 ...
registry.stopAll();                     // 逆序停止
registry.destroyAll();                  // 释放资源（不可逆）
```

**在模块中访问数据库** —— 声明一个 Spring Data JPA Repository，然后：

```java
MyDao dao = dao(MyDao.class);   // Module 便捷方法，内部走 SpringContextUtil
```

## 构建

```bash
./gradlew test        # 运行测试（@SpringBootTest 完整生命周期）
./gradlew build       # 编译 + 测试 + 打 jar
./gradlew bootRun     # 运行框架演示
```

产物位于 `build/libs/`（`MQ-<version>.jar` 为 boot jar，`MQ-<version>-plain.jar` 为普通 jar）。CI 在 ubuntu / macos / windows 上执行 `test`；release 会附加 jar 产物 —— 见 [Releases](https://github.com/yxpil/MQ/releases)。

## 许可证

[Apache-2.0](LICENSE)

---

Part of the BIT ecosystem — [github.com/yxpil/bit](https://github.com/yxpil/bit)
