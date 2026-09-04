package com.yxpil.mq.topic.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Topic 实体 — MQ 主题/队列。
 */
@Entity
@Table(name = "topics")
public class TopicInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private int partitions = 1;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ──────────── 构造器 ────────────

    public TopicInfo() {}

    public TopicInfo(String name, int partitions) {
        this.name = name;
        this.partitions = partitions;
    }

    // ──────────── Getter / Setter ────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getPartitions() { return partitions; }
    public void setPartitions(int partitions) { this.partitions = partitions; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "TopicInfo{name='" + name + "', partitions=" + partitions + "}";
    }
}
