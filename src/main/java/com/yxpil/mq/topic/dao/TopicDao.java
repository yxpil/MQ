package com.yxpil.mq.topic.dao;

import com.yxpil.mq.topic.entity.TopicInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Topic DAO — 主题数据访问。
 */
@Repository
public interface TopicDao extends JpaRepository<TopicInfo, Long> {

    Optional<TopicInfo> findByName(String name);

    List<TopicInfo> findByNameContaining(String keyword);

    long countByName(String name);
}
