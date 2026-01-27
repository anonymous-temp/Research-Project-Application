package com.faers.agent.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContextUtil implements ApplicationContextAware {
    private static ApplicationContext applicationContext;

    // 私有化构造函数，防止外部实例化
    private SpringContextUtil() {}

    @Override
    public synchronized void setApplicationContext(ApplicationContext context) throws BeansException {
        // 确保只初始化一次
        if (applicationContext == null) {
            SpringContextUtil.applicationContext = context;
        }
    }

    /**
     * 获取ApplicationContext（确保非空）
     */
    public static ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("Spring上下文尚未初始化！");
        }
        return applicationContext;
    }

    /**
     * 获取Bean（带类型检查）
     */
    public static <T> T getBean(Class<T> clazz) {
        try {
            return getApplicationContext().getBean(clazz);
        } catch (BeansException e) {
            throw new RuntimeException("获取Bean失败：" + clazz.getName(), e);
        }
    }

    /**
     * 安全获取Bean（允许null）
     */
    public static <T> T getBeanSafe(Class<T> clazz) {
        try {
            return getApplicationContext().getBean(clazz);
        } catch (BeansException e) {
            return null;
        }
    }
}
