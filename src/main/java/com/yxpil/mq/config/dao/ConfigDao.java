package com.yxpil.mq.config.dao;

import com.yxpil.mq.config.entity.MqConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 配置 DAO — 基于 Spring Data JPA。
 */
@Repository
public interface ConfigDao extends JpaRepository<MqConfig, Long> {

    /** 查找单条配置 (始终取第一条) */
    default MqConfig findFirst() {
        return findAll().stream().findFirst().orElse(null);
    }
}
