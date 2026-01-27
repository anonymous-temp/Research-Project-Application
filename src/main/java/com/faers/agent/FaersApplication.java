package com.faers.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 项目申报书系统入口类
 */
@SpringBootApplication
@EnableScheduling
@EnableFeignClients
@EnableAsync  // 启用异步支持，用于DialogRouter的@Async方法
//@EnableFeignClients(basePackages = "com.faers.agent.feign")
public class FaersApplication {
    public static void main(String[] args) {
        SpringApplication.run(FaersApplication.class, args);
    }
}