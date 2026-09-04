package com.yxpil.mq.consumer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Consumer 实体 — 消费者注册信息。
 */
@Entity
@Table(name = "consumers")
public class ConsumerInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String consumerId;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String groupName = "default";

    @Column(name = "consumer_offset", nullable = false)
    private long offset = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsumerStatus status = ConsumerStatus.REGISTERED;

    @Column(nullable = false)
    private LocalDateTime registeredAt = LocalDateTime.now();

    // ──────────── 构造器 ────────────

    public ConsumerInfo() {}

    public ConsumerInfo(String consumerId, String topic, String groupName) {
        this.consumerId = consumerId;
        this.topic = topic;
        this.groupName = groupName;
    }

    // ──────────── 状态枚举 ────────────

    public enum ConsumerStatus {
        REGISTERED, SUBSCRIBED, CONSUMING, PAUSED, STOPPED
    }

    // ──────────── Getter / Setter ────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getConsumerId() { return consumerId; }
    public void setConsumerId(String consumerId) { this.consumerId = consumerId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public long getOffset() { return offset; }
    public void setOffset(long offset) { this.offset = offset; }

    public ConsumerStatus getStatus() { return status; }
    public void setStatus(ConsumerStatus status) { this.status = status; }

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }

    @Override
    public String toString() {
        return "ConsumerInfo{consumerId='" + consumerId + "', topic='" + topic + "', group='" + groupName + "', status=" + status + "}";
    }
}
