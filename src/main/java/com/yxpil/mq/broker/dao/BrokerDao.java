package com.yxpil.mq.broker.dao;

import com.yxpil.mq.broker.entity.BrokerInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Broker DAO — 消息代理数据访问。
 */
@Repository
public interface BrokerDao extends JpaRepository<BrokerInfo, Long> {

    Optional<BrokerInfo> findByName(String name);

    List<BrokerInfo> findByStatus(BrokerInfo.BrokerStatus status);

    List<BrokerInfo> findByHostAndPort(String host, int port);
}
