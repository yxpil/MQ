package com.yxpil.mq.config.entity;

import jakarta.persistence.*;

/**
 * MQ 配置实体 — 单行配置表。
 */
@Entity
@Table(name = "mq_config")
public class MqConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String brokerHost = "localhost";

    @Column(nullable = false)
    private int brokerPort = 9092;

    @Column(nullable = false)
    private int maxConnections = 100;

    // ──────────── 构造器 ────────────

    public MqConfig() {}

    public MqConfig(String brokerHost, int brokerPort, int maxConnections) {
        this.brokerHost = brokerHost;
        this.brokerPort = brokerPort;
        this.maxConnections = maxConnections;
    }

    // ──────────── Getter / Setter ────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBrokerHost() { return brokerHost; }
    public void setBrokerHost(String brokerHost) { this.brokerHost = brokerHost; }

    public int getBrokerPort() { return brokerPort; }
    public void setBrokerPort(int brokerPort) { this.brokerPort = brokerPort; }

    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }

    @Override
    public String toString() {
        return "MqConfig{brokerHost='" + brokerHost + "', brokerPort=" + brokerPort + ", maxConnections=" + maxConnections + "}";
    }
}
