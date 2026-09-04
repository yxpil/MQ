package com.yxpil.mq.config;

import com.yxpil.mq.config.dao.ConfigDao;
import com.yxpil.mq.config.entity.MqConfig;
import com.yxpil.mq.core.Module;

/**
 * 配置模块 — 从数据库加载 MQ 配置，无依赖 (叶子节点)。
 * <p>
 * 首次启动时自动插入默认配置行。
 */
public class ConfigModule extends Module {

    public static final String NAME = "config";

    private String brokerHost = "localhost";
    private int brokerPort = 9092;
    private int maxConnections = 100;

    public ConfigModule() {
        super(NAME);
    }

    @Override
    public void init() {
        ConfigDao dao = dao(ConfigDao.class);
        MqConfig cfg = dao.findFirst();

        if (cfg == null) {
            // 首次启动: 写入默认配置
            cfg = new MqConfig(brokerHost, brokerPort, maxConnections);
            dao.save(cfg);
            log("首次初始化 → 写入默认配置到数据库");
        } else {
            brokerHost = cfg.getBrokerHost();
            brokerPort = cfg.getBrokerPort();
            maxConnections = cfg.getMaxConnections();
            log("加载数据库配置: %s", cfg);
        }
    }

    @Override
    public void start() {
        emit("ready", this);
        log("配置模块就绪");
    }

    /** 更新配置并持久化 */
    public void updateConfig(String host, int port, int maxConn) {
        ConfigDao dao = dao(ConfigDao.class);
        MqConfig cfg = dao.findFirst();
        if (cfg != null) {
            cfg.setBrokerHost(host);
            cfg.setBrokerPort(port);
            cfg.setMaxConnections(maxConn);
            dao.save(cfg);
        }
        this.brokerHost = host;
        this.brokerPort = port;
        this.maxConnections = maxConn;
        emit("updated", this);
        log("配置已更新 → %s:%d (maxConn=%d)", host, port, maxConn);
    }

    // ──────────── Getter ────────────

    public String getBrokerHost() { return brokerHost; }
    public int getBrokerPort() { return brokerPort; }
    public int getMaxConnections() { return maxConnections; }
}
