package com.yxpil.mq.consumer.dao;

import com.yxpil.mq.consumer.entity.ConsumerInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Consumer DAO — 消费者数据访问。
 */
@Repository
public interface ConsumerDao extends JpaRepository<ConsumerInfo, Long> {

    Optional<ConsumerInfo> findByConsumerId(String consumerId);

    List<ConsumerInfo> findByTopic(String topic);

    List<ConsumerInfo> findByGroupName(String groupName);

    List<ConsumerInfo> findByStatus(ConsumerInfo.ConsumerStatus status);

    List<ConsumerInfo> findByTopicAndStatus(String topic, ConsumerInfo.ConsumerStatus status);
}
