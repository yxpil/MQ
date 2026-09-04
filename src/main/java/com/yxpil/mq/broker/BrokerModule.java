package com.yxpil.mq.broker;

import com.yxpil.mq.broker.dao.BrokerDao;
import com.yxpil.mq.broker.entity.BrokerInfo;
import com.yxpil.mq.config.ConfigModule;
import com.yxpil.mq.core.Module;
import java.util.List;

/**
 * 消息代理模块 — MQ 核心，依赖 ConfigModule。
 * <p>
 * 从配置模块获取连接信息，启动 Broker 并持久化到数据库。
 */
public class BrokerModule extends Module {

    public static final String NAME = "broker";

    private BrokerInfo currentBroker;

    public BrokerModule() {
        super(NAME);
    }

    @Override
    public List<String> dependencies() {
        return List.of(ConfigModule.NAME);
    }

    @Override
    public void init() {
        ConfigModule config = require(ConfigModule.NAME);
        BrokerDao dao = dao(BrokerDao.class);

        // 检查是否已有同 host:port 的 Broker
        List<BrokerInfo> existing = dao.findByHostAndPort(
                config.getBrokerHost(), config.getBrokerPort());

        if (existing.isEmpty()) {
            currentBroker = new BrokerInfo(
                    "broker-" + System.currentTimeMillis() % 10000,
                    config.getBrokerHost(),
                    config.getBrokerPort());
            dao.save(currentBroker);
            log("新建 Broker 记录: %s", currentBroker);
        } else {
            currentBroker = existing.get(0);
            currentBroker.setStatus(BrokerInfo.BrokerStatus.STARTING);
            dao.save(currentBroker);
            log("复用已有 Broker: %s", currentBroker);
        }
    }

    @Override
    public void start() {
        BrokerDao dao = dao(BrokerDao.class);
        currentBroker.setStatus(BrokerInfo.BrokerStatus.RUNNING);
        dao.save(currentBroker);

        log("代理已启动 → %s:%d [%s]",
                currentBroker.getHost(), currentBroker.getPort(), currentBroker.getStatus());
        emit("ready", currentBroker);
    }

    @Override
    public void stop() {
        BrokerDao dao = dao(BrokerDao.class);
        currentBroker.setStatus(BrokerInfo.BrokerStatus.STOPPED);
        dao.save(currentBroker);
        log("代理已停止");
    }

    /** 获取所有运行中的 Broker */
    public List<BrokerInfo> listRunningBrokers() {
        return dao(BrokerDao.class).findByStatus(BrokerInfo.BrokerStatus.RUNNING);
    }

    public BrokerInfo getCurrentBroker() { return currentBroker; }
}
