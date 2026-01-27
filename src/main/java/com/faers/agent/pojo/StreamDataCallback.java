package com.faers.agent.pojo;

import com.faers.agent.dto.responseDto.Response;

/**
 * 流式数据回调接口（子线程 -> 主线程）
 */
public interface StreamDataCallback {
    // 接收单条流式数据
    void onData(String chunk);

    void onData(Response chunk);

    // 可选：处理流结束事件
    default void onComplete() {}

    // 可选：处理异常
    default void onError(Throwable e) {}
}
