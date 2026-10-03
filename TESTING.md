# MQ 测试说明
- 测试完成：是（2026-10-04）
- 测试日期：2026-10-04
- 测试内容：单元/集成覆盖 `core.EventBus`（发布订阅、优先级触发顺序、单段/多段通配路由、once 一次性、emit 中 off 延迟移除）与 `core.Module`（on/emit 命名空间钩子、构造边界）；注入测试针对订阅 pattern 被编译为正则的不可信输入（正则元字符字面化、`../` 路径穿越不跨 `:` 命名空间）；钩子测试覆盖注册→触发顺序、参数传递、单钩子异常失败隔离、未注册/已注销钩子安全 no-op。
- 运行命令：`./gradlew test`（离线核心验证：javac + JUnit Console Launcher，见下文）
- 测试框架：JUnit 5 (Jupiter)
- 模型：豆包（Doubao）生成

---

本目录新增的测试位于 `src/test/java/com/yxpil/mq/core/`，针对 **事件驱动核心 `EventBus`** 与 **模块钩子基类 `Module`**（不依赖 Spring 容器，可独立运行）。

## 测了什么

### 单元测试（核心类主路径 / 边界 / 错误路径）
- `EventBusTest`
  - 基础收发：`on`/`emit` 正确投递 topic 与 payload（含泛型 `data()` 还原）。
  - 空触发：对无监听的 topic `emit` 不抛异常。
  - 优先级：多监听器按 priority 升序执行（越小越先）。
  - `once`：一次性钩子触发一次后自动注销。
  - `off` / `offAll`：注销后不再触发；对未注册 handler 调用 `off` 是安全 no-op。
- `ModuleHookTest`
  - 模块名空白/null 构造抛 `IllegalArgumentException`。
  - `name()` 暴露正确。
  - `emitGlobal` 不自动加命名空间前缀。

### 集成测试（多组件协作）
- 错误隔离：某监听器抛异常不影响后续监听器，异常不向外抛。
- emit 过程中 `off` 的延迟移除：dispatch 不中断，emit 结束后才真正移除。
- 通配符路由：`user:*` 单段、`user:**` 多段；精确匹配组先于通配组派发。
- 模块命名空间钩子：`Module.on/emit` 自动加 `name:` 前缀并经真实 EventBus 投递 payload。

### 注入测试（不可信输入）
本仓库无 HTTP 路由 / shell 执行 / SQL 拼接面（DAO 为 Spring Data 方法派生查询），唯一的不可信输入面是**订阅 pattern 被编译为正则**。针对该面补注入测试：
- `injection_regexMetacharactersInPattern_areTreatedLiterally`：pattern 中的 `. + ( )` 等正则元字符必须被 `Pattern.quote` 当字面量，不得引发正则注入 / 过度匹配 / ReDoS。
- `injection_pathTraversalTopic_isLiteral`：`../`、`%2F` 等路径穿越字符只是普通文本，不被特殊解释。
- `injection_wildcardAbuse_doesNotEscapeNamespace`：路由以 `:` 分段，`../` 不能跨前缀逃逸到其它命名空间（如 `admin:*`）。

### 钩子测试（事件/回调机制）
覆盖：注册→触发顺序（优先级）、参数传递（payload/topic）、失败隔离（单钩子异常不影响他人）、一次性钩子自动注销、未注册/已注销钩子被安全拒绝（no-op，不影响他人）、emit 中注销的延迟生效。

## 如何运行

### 方式一：Gradle（推荐，需 JDK17 toolchain + 网络）
```
./gradlew test
```
该命令会同时运行既有的 `@SpringBootTest contextLoads` 与本目录新增的纯 JUnit 核心测试。

### 方式二：离线仅跑核心测试（本机验证所用）
本机仅装有 JDK 26 而 Gradle 配置的是 JDK 17 toolchain，故核心测试用 `javac` + JUnit Platform Console Launcher 直接验证：
```
# 1. 下载 junit-platform-console-standalone 到某 lib 目录
# 2. 编译并运行（classpath 用分号分隔）
javac -cp <junit-standalone.jar> -d out/main \
      src/main/java/com/yxpil/mq/core/EventBus.java \
      src/main/java/com/yxpil/mq/core/Module.java
javac -cp out/main;<junit-standalone.jar> -d out/test \
      src/test/java/com/yxpil/mq/core/EventBusTest.java \
      src/test/java/com/yxpil/mq/core/ModuleHookTest.java
java -cp out/main;out/test;<junit-standalone.jar> \
     org.junit.platform.console.ConsoleLauncher --scan-classpath=out/test
```

## 预期结果
- 新增核心测试：**20 个用例全部通过，0 失败**。
- 既有 `MqApplicationTests.contextLoads` 需完整 Spring + H2 容器，在配好 JDK17 的 Gradle 环境下运行；本机离线验证未执行该用例。
