package com.yxpil.mq.core;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文工具 — 让非 Spring 管理的类也能获取 Bean (如 Module 手动实例).
 * <p>
 * 在 Module 中通过 {@code dao(BrokerDao.class)} 获取 DAO 的 Spring Bean.
 */
@Component
public class SpringContextUtil implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringContextUtil.context = applicationContext;
    }

    /** 按类型获取 Spring Bean */
    public static <T> T getBean(Class<T> clazz) {
        if (context == null) {
            throw new IllegalStateException("Spring 容器尚未初始化, 无法获取 Bean: " + clazz.getName());
        }
        return context.getBean(clazz);
    }

    /** 按名称获取 Spring Bean */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        if (context == null) {
            throw new IllegalStateException("Spring 容器尚未初始化, 无法获取 Bean: " + name);
        }
        return (T) context.getBean(name);
    }
}
