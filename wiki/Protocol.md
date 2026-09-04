# MQ Protocol — Events & Persistence / 事件与持久化协议

## Event topic naming / 事件 topic 命名

Topics are colon-separated segments, e.g. `user:login` or `topic:created`.

Topic 由冒号分隔的多段组成，例如 `user:login`、`topic:created`。

| Pattern | Meaning / 含义 |
|---------|----------------|
| `user:login` | exact match / 精确匹配 |
| `user:*` | `*` matches exactly one segment / `*` 匹配单段 |
| `user:**` | `**` matches one or more trailing segments / `**` 匹配多段 |

Rules / 规则:

1. Handlers on the same topic run in ascending priority (default `50`; `Module.on()` registers at `Integer.MAX_VALUE`, i.e. last). / 同一 topic 的 handler 按优先级升序执行（默认 `50`；`Module.on()` 注册为 `Integer.MAX_VALUE`，最后执行）。
2. A handler exception is caught and logged (`[EventBus] 监听器异常 ...`); other handlers still run. / 单个 handler 异常被捕获并记录日志，不影响其他 handler。
3. `off()` called during `emit()` is deferred until the emit completes. / `emit()` 期间的 `off()` 会延迟到本次派发结束后执行。

## Namespacing for modules / 模块命名空间

Inside a `Module`, `emit(event, data)` / `on(event, handler)` operate on the module-namespaced topic `name:event`:

在 `Module` 内部，`emit(event, data)` / `on(event, handler)` 自动使用 `模块名:事件名` 前缀：

| Module helper | Actual topic / 实际 topic |
|---------------|---------------------------|
| `config` module `emit("ready", ...)` | `config:ready` |
| `broker` module `emit("ready", ...)` | `broker:ready` |
| `topic` module `emit("created", topic)` | `topic:created` |
| `topic` module `emit("deleted", name)` | `topic:deleted` |
| `consumer` module `emit("registered", c)` | `consumer:registered` |
| `consumer` module `emit("consuming", c)` | `consumer:consuming` |

Global broadcasts use `emitGlobal` directly (`topic:created` is also emitted globally so other modules can subscribe via `onAny`).

全局广播使用 `emitGlobal`（`topic:created` 同时全局发出，供其他模块 `onAny` 订阅）。

## Lifecycle / 生命周期

`ModuleRegistry.startAll()`:

1. Topological sort (Kahn) of registered modules by `dependencies()`; cycles and unregistered dependencies fail fast. / 按 `dependencies()` 做 Kahn 拓扑排序；循环依赖与未注册依赖立即报错。
2. Instantiate modules, bind `EventBus` + registry. / 实例化模块并绑定 EventBus 与注册中心。
3. `init()` in dependency order. / 按依赖顺序 `init()`。
4. `start()` in dependency order. / 按依赖顺序 `start()`。

`stopAll()` / `destroyAll()` run in reverse order. Registry state machine: `NEW → INITIALIZED → STARTED → STOPPED → DESTROYED`.

`stopAll()` / `destroyAll()` 按逆序执行。注册中心状态机：`NEW → INITIALIZED → STARTED → STOPPED → DESTROYED`。

## Database tables / 数据库表

In-memory H2 `jdbc:h2:mem:mqdb`, schema auto-generated (`ddl-auto=create-drop`).

内存 H2 `jdbc:h2:mem:mqdb`，schema 自动生成（`ddl-auto=create-drop`）。

| Table | Entity | Columns | Notes / 说明 |
|-------|--------|---------|--------------|
| `mq_config` | `MqConfig` | `id`, `broker_host`, `broker_port`, `max_connections` | single row / 单行配置 |
| `brokers` | `BrokerInfo` | `id`, `name` (unique), `host`, `port`, `status`, `created_at` | status: `STARTING/RUNNING/STOPPING/STOPPED/ERROR` |
| `topics` | `TopicInfo` | `id`, `name` (unique), `partitions`, `created_at` | |
| `consumers` | `ConsumerInfo` | `id`, `consumer_id`, `topic`, `group_name`, `consumer_offset`, `status`, `registered_at` | status: `REGISTERED/SUBSCRIBED/CONSUMING/PAUSED/STOPPED`; column `consumer_offset` avoids the reserved word `offset` / 列名 `consumer_offset` 规避保留字 `offset` |
