package com.yxpil.mq.consumer;

import com.yxpil.mq.consumer.dao.ConsumerDao;
import com.yxpil.mq.consumer.entity.ConsumerInfo;
import com.yxpil.mq.core.Module;
import com.yxpil.mq.topic.entity.TopicInfo;

import java.util.*;

/**
 * 消费者管理模块 — 依赖 Broker + Topic，演示事件驱动。
 * <p>
 * 管理消费者注册/订阅，数据持久化到数据库。
 */
public class ConsumerModule extends Module {

    public static final String NAME = "consumer";

    /** consumerId → subscribed topics */
    private final Map<String, Set<String>> subscriptions = new HashMap<>();

    public ConsumerModule() {
        super(NAME);
    }

    @Override
    public List<String> dependencies() {
        return List.of("broker", "topic");
    }

    @Override
    public void init() {
        // 从数据库加载已有消费者
        ConsumerDao dao = dao(ConsumerDao.class);
        List<ConsumerInfo> all = dao.findAll();
        for (ConsumerInfo c : all) {
            subscriptions.computeIfAbsent(c.getConsumerId(), k -> new LinkedHashSet<>())
                    .add(c.getTopic());
        }
        log("从数据库加载 %d 个消费者记录", all.size());

        // 监听 Topic 创建事件 → 通知所有订阅者
        onAny("topic:created", e -> {
            if (e.data() instanceof TopicInfo t) {
                log("新 Topic 上线: %s", t.getName());
            }
        });
    }

    @Override
    public void start() {
        emit("ready", subscriptions.size());
        log("消费者模块就绪, %d 个订阅者", subscriptions.size());
    }

    /** 注册消费者并持久化 */
    public ConsumerInfo register(String consumerId, String topic, String groupName) {
        ConsumerDao dao = dao(ConsumerDao.class);

        // 防止重复注册
        Optional<ConsumerInfo> existing = dao.findByConsumerId(consumerId);
        if (existing.isPresent()) {
            log("消费者已存在: %s", consumerId);
            return existing.get();
        }

        ConsumerInfo consumer = new ConsumerInfo(consumerId, topic, groupName);
        dao.save(consumer);

        subscriptions.computeIfAbsent(consumerId, k -> new LinkedHashSet<>()).add(topic);

        log("注册消费者: %s → %s (group=%s)", consumerId, topic, groupName);
        emit("registered", consumer);
        return consumer;
    }

    /** 消费者开始消费 */
    public void startConsuming(String consumerId) {
        ConsumerDao dao = dao(ConsumerDao.class);
        dao.findByConsumerId(consumerId).ifPresent(c -> {
            c.setStatus(ConsumerInfo.ConsumerStatus.CONSUMING);
            dao.save(c);
            log("消费者 %s 开始消费 Topic: %s", consumerId, c.getTopic());
            emit("consuming", c);
        });
    }

    /** 获取消费者订阅的 Topic 列表 */
    public Set<String> getSubscriptions(String consumerId) {
        return subscriptions.getOrDefault(consumerId, Collections.emptySet());
    }

    public Map<String, Set<String>> allSubscriptions() {
        return Collections.unmodifiableMap(subscriptions);
    }

    public int consumerCount() { return subscriptions.size(); }
}
