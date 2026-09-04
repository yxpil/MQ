package com.yxpil.mq;

import com.yxpil.mq.config.ConfigModule;
import com.yxpil.mq.broker.BrokerModule;
import com.yxpil.mq.topic.TopicModule;
import com.yxpil.mq.consumer.ConsumerModule;
import com.yxpil.mq.core.ModuleRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;

/**
 * MQ 应用入口 — Spring Boot + 事件驱动模块框架。
 * <p>
 * 启动流程: Spring 初始化 → 注册模块 → init → start → 运行
 */
@SpringBootApplication
public class MqApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(MqApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("  MQ 事件驱动框架启动 (Spring Boot 4.1)");
        System.out.println("═══════════════════════════════════════════");

        // 创建注册中心单例
        ModuleRegistry registry = ModuleRegistry.getInstance();

        // 注册全部模块 (每个模块一个独立包)
        registry.register(ConfigModule.class)
                .register(BrokerModule.class)
                .register(TopicModule.class)
                .register(ConsumerModule.class);

        // 打印依赖关系图
        System.out.println("\n📊 模块依赖关系:");
        registry.printDependencyTree();

        // 启动所有模块 (拓扑排序 → init → start)
        System.out.println("\n🚀 启动所有模块...");
        registry.startAll();

        TopicModule topicModule = registry.<TopicModule>get(TopicModule.NAME);
        System.out.println("\n📝 创建测试 Topic...");
        topicModule.createTopic("order-events", 3);
        topicModule.createTopic("user-events", 2);

        // 演示: 注册消费者
        ConsumerModule consumerModule = registry.<ConsumerModule>get(ConsumerModule.NAME);
        consumerModule.register("consumer-1", "order-events", "order-group");
        consumerModule.register("consumer-2", "user-events", "user-group");
        consumerModule.startConsuming("consumer-1");

        // 打印当前状态
        System.out.println("\n📊 当前状态:");
        System.out.printf("  Topics: %d 个 %s%n", topicModule.topicCount(), topicModule.listTopics());
        System.out.printf("  Consumers: %d 个%n", consumerModule.consumerCount());
        System.out.printf("  Broker: %s%n", registry.get(BrokerModule.NAME));
        System.out.printf("  Config: %s:%d%n",
                registry.<ConfigModule>get(ConfigModule.NAME).getBrokerHost(),
                registry.<ConfigModule>get(ConfigModule.NAME).getBrokerPort());

        // 优雅关闭
        System.out.println("\n🛑 正在停止...");
        registry.stopAll();

        System.out.println("═══════════════════════════════════════════");
        System.out.println("  MQ 框架演示完成 ✅");
    }
}
