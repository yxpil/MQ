package com.yxpil.mq.topic;

import com.yxpil.mq.core.Module;
import com.yxpil.mq.topic.dao.TopicDao;
import com.yxpil.mq.topic.entity.TopicInfo;

import java.util.*;

/**
 * Topic 管理模块 — 依赖 Broker + Config。
 * <p>
 * 管理 Topic 的创建/删除/查询，数据持久化到数据库。
 */
public class TopicModule extends Module {

    public static final String NAME = "topic";

    /** 内存缓存 (启动时从 DB 加载) */
    private final Set<String> topics = new LinkedHashSet<>();

    public TopicModule() {
        super(NAME);
    }

    @Override
    public List<String> dependencies() {
        return List.of("config", "broker");
    }

    @Override
    public void init() {
        // 监听跨模块事件
        onAny("broker:ready", e -> log("检测到 Broker 就绪 → 开始加载 Topic 列表"));
        onAny("*:created", e -> log("全局事件捕获: %s", e.topic()));
    }

    @Override
    public void start() {
        // 从数据库加载已有 Topic
        TopicDao dao = dao(TopicDao.class);
        List<TopicInfo> all = dao.findAll();
        for (TopicInfo t : all) {
            topics.add(t.getName());
        }
        log("从数据库加载 %d 个 Topic: %s", all.size(), topics);
        emit("ready", topics.size());
    }

    /** 创建 Topic (内存 + 数据库) */
    public TopicInfo createTopic(String name, int partitions) {
        if (topics.contains(name)) {
            log("Topic 已存在: %s", name);
            return null;
        }
        TopicDao dao = dao(TopicDao.class);
        TopicInfo topic = new TopicInfo(name, partitions);
        dao.save(topic);
        topics.add(name);

        log("创建 Topic: %s (partitions=%d)", name, partitions);
        emit("created", topic);
        emitGlobal("topic:created", topic);
        return topic;
    }

    /** 删除 Topic */
    public void deleteTopic(String name) {
        TopicDao dao = dao(TopicDao.class);
        dao.findByName(name).ifPresent(t -> {
            dao.delete(t);
            topics.remove(name);
            log("删除 Topic: %s", name);
            emit("deleted", name);
        });
    }

    /** 查找 Topic */
    public Optional<TopicInfo> findByName(String name) {
        return dao(TopicDao.class).findByName(name);
    }

    public Set<String> listTopics() { return Collections.unmodifiableSet(topics); }

    public int topicCount() { return topics.size(); }
}
