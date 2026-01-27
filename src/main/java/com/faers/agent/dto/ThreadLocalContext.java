package com.faers.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 线程本地上下文：存储同一线程内需要传递的核心数据
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThreadLocalContext {
    // 初始获取的4个核心字段
    private String parentId;
    private String senderId;
    private String targetClientId;
    // 可选：添加traceId等辅助字段（方便追踪）
    private String id;
}