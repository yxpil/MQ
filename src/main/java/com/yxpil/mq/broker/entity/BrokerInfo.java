package com.yxpil.mq.broker.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 消息代理实体 — 表示一个 Broker 节点。
 */
@Entity
@Table(name = "brokers")
public class BrokerInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BrokerStatus status = BrokerStatus.STOPPED;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // ──────────── 构造器 ────────────

    public BrokerInfo() {}

    public BrokerInfo(String name, String host, int port) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.status = BrokerStatus.STARTING;
    }

    // ──────────── 状态枚举 ────────────

    public enum BrokerStatus {
        STARTING, RUNNING, STOPPING, STOPPED, ERROR
    }

    // ──────────── Getter / Setter ────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public BrokerStatus getStatus() { return status; }
    public void setStatus(BrokerStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "BrokerInfo{name='" + name + "', host='" + host + "', port=" + port + ", status=" + status + "}";
    }
}
