package com.faers.agent.service;

import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.pojo.StreamDataCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import com.faers.agent.exception.LLMInvocationException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * LLM调用服务
 * 专门封装阿里云DashScope API调用，提供重试、超时、API Key轮询和冷却等功能
 */
@Service
public class LLMInvocationService {

    private static final Logger log = LoggerFactory.getLogger(LLMInvocationService.class);
    
    // API Key配置 - 从环境变量加载
    private static final List<String> API_KEYS = loadApiKeysFromEnv();

    /**
     * 从环境变量加载API密钥
     * 支持 LLM_API_KEY_1 到 LLM_API_KEY_10 或单个 DASHSCOPE_API_KEY
     */
    private static List<String> loadApiKeysFromEnv() {
        List<String> keys = new ArrayList<>();

        // 尝试加载多个API Key (LLM_API_KEY_1 到 LLM_API_KEY_10)
        for (int i = 1; i <= 10; i++) {
            String key = System.getenv("LLM_API_KEY_" + i);
            if (key != null && !key.isEmpty() && !key.startsWith("${")) {
                keys.add(key);
            }
        }

        // 如果没有找到多个Key，尝试加载单个DASHSCOPE_API_KEY
        if (keys.isEmpty()) {
            String singleKey = System.getenv("DASHSCOPE_API_KEY");
            if (singleKey != null && !singleKey.isEmpty() && !singleKey.startsWith("${")) {
                keys.add(singleKey);
            }
        }

        // 如果仍然没有Key，记录警告
        if (keys.isEmpty()) {
            LoggerFactory.getLogger(LLMInvocationService.class)
                .warn("未配置API Key，请设置环境变量 LLM_API_KEY_1-10 或 DASHSCOPE_API_KEY");
        }

        return keys;
    }
    
    // API Key状态管理
    private static final class ApiKeyStatus {
        private String apiKey;
        private boolean isCooling;
        private long coolingEndTime;
        private int consecutiveFailures;
        
        private ApiKeyStatus(String apiKey) {
            this.apiKey = apiKey;
            this.isCooling = false;
            this.coolingEndTime = 0;
            this.consecutiveFailures = 0;
        }
    }
    
    private final List<ApiKeyStatus> apiKeyStatusList = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler;
    private final Semaphore apiKeyLock = new Semaphore(1);
    
    // 配置常量
    private static final int MAX_RETRIES_PER_KEY = 3;
    private static final int COOLING_TIME = 30000; // 30秒
    private static final int RETRY_INTERVAL = 3000; // 3秒
    
    private final ChatClient chatClient;
    
    @Autowired
    public LLMInvocationService(ChatClient chatClient) {
        this.chatClient = chatClient;
        
        // 初始化API Key状态
        for (String apiKey : API_KEYS) {
            apiKeyStatusList.add(new ApiKeyStatus(apiKey));
        }
        
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "LLM-Invocation-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // 启动冷却检查任务
        scheduler.scheduleAtFixedRate(this::checkCoolingKeys, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * 检查冷却中的API Key，将已过冷却时间的Key恢复可用
     */
    private void checkCoolingKeys() {
        long currentTime = System.currentTimeMillis();
        for (ApiKeyStatus status : apiKeyStatusList) {
            if (status.isCooling && currentTime >= status.coolingEndTime) {
                status.isCooling = false;
                status.consecutiveFailures = 0;
                log.info("API Key {} 冷却时间已过，恢复可用", maskApiKey(status.apiKey));
            }
        }
    }
    
    /**
     * 获取下一个可用的API Key
     * @return 可用的API Key，如果所有Key都不可用则返回null
     */
    private String getNextAvailableApiKey() {
        long currentTime = System.currentTimeMillis();
        
        // 先找主Key（第一个），如果可用则优先使用
        ApiKeyStatus mainKey = apiKeyStatusList.get(0);
        if (!mainKey.isCooling && mainKey.consecutiveFailures < MAX_RETRIES_PER_KEY) {
            return mainKey.apiKey;
        }
        
        // 如果主Key不可用，轮询其他Key
        for (int i = 1; i < apiKeyStatusList.size(); i++) {
            ApiKeyStatus status = apiKeyStatusList.get(i);
            if (!status.isCooling && status.consecutiveFailures < MAX_RETRIES_PER_KEY) {
                return status.apiKey;
            }
        }
        
        return null;
    }
    
    /**
     * 标记API Key调用失败
     * @param apiKey 失败的API Key
     * @return 是否需要将该Key加入冷却
     */
    private boolean markApiKeyFailure(String apiKey) {
        for (ApiKeyStatus status : apiKeyStatusList) {
            if (status.apiKey.equals(apiKey)) {
                status.consecutiveFailures++;
                log.info("API Key {} 连续失败次数: {}", maskApiKey(apiKey), status.consecutiveFailures);
                
                if (status.consecutiveFailures >= MAX_RETRIES_PER_KEY) {
                    // 加入冷却
                    status.isCooling = true;
                    status.coolingEndTime = System.currentTimeMillis() + COOLING_TIME;
                    log.warn("API Key {} 连续失败{}次，进入冷却时间{}秒", 
                            maskApiKey(apiKey), MAX_RETRIES_PER_KEY, COOLING_TIME / 1000);
                    return true;
                }
                break;
            }
        }
        return false;
    }
    
    /**
     * 标记API Key调用成功
     * @param apiKey 成功的API Key
     */
    private void markApiKeySuccess(String apiKey) {
        for (ApiKeyStatus status : apiKeyStatusList) {
            if (status.apiKey.equals(apiKey)) {
                status.consecutiveFailures = 0;
                break;
            }
        }
    }
    
    /**
     * 掩码API Key，只显示前4位和后4位
     * @param apiKey 原始API Key
     * @return 掩码后的API Key
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return apiKey;
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
    
    /**
     * 封装流式调用，提供重试、超时、API Key轮询和冷却等功能
     *
     * @param prompt            提示词
     * @param streamDataCallback 流式数据回调
     * @param traceId           追踪ID
     * @return 完整响应内容
     */
    public String invokeStream(String prompt, StreamDataCallback streamDataCallback, String traceId) {
        long startTime = System.currentTimeMillis();
        List<String> usedKeys = new ArrayList<>();
        
        try {
            // 遍历所有可用的API Key
            while (true) {
                String apiKey = getNextAvailableApiKey();
                
                if (apiKey == null) {
                    // 所有API Key都不可用
                    String errorMsg = "所有API Key都暂时不可用，请稍后再试";
                    log.error("LLM_INVOCATION_ERROR | traceId={} | 所有API Key都不可用", traceId);
                    Response errorResponse = Response.ResponseContentChatBuilder(errorMsg, "agent");
                    streamDataCallback.onData(errorResponse);
                    throw new LLMInvocationException(errorMsg, new RuntimeException("所有API Key都不可用"), 0, 0, false);
                }
                
                // 避免重复使用同一Key
                if (usedKeys.contains(apiKey)) {
                    continue;
                }
                usedKeys.add(apiKey);
                
                log.info("LLM_INVOCATION_START | traceId={} | 使用API Key: {}", traceId, maskApiKey(apiKey));
                
                // 对当前API Key进行重试
                for (int retry = 0; retry < MAX_RETRIES_PER_KEY; retry++) {
                    try {
                        // 执行流式调用
                        String result = executeStreamCall(prompt, streamDataCallback, traceId, apiKey);
                        
                        // 调用成功，重置该Key的失败计数
                        markApiKeySuccess(apiKey);
                        
                        long totalTime = System.currentTimeMillis() - startTime;
                        log.info("LLM_INVOCATION_SUCCESS | traceId={} | API Key: {} | 耗时: {}ms", 
                                traceId, maskApiKey(apiKey), totalTime);
                        
                        return result;
                    } catch (Exception e) {
                        // 调用失败，记录失败次数
                        log.warn("LLM_INVOCATION_FAILED | traceId={} | API Key: {} | 重试: {}/{} | 错误: {}", 
                                traceId, maskApiKey(apiKey), retry + 1, MAX_RETRIES_PER_KEY, e.getMessage());
                        
                        // 如果是最后一次重试，标记Key失败
                        if (retry == MAX_RETRIES_PER_KEY - 1) {
                            markApiKeyFailure(apiKey);
                        }
                        
                        // 发送重试通知给前端
                        if (retry < MAX_RETRIES_PER_KEY - 1) {
                            String retryMsg = String.format("请求失败，%d秒后自动重试（%d/%d）...",
                                    RETRY_INTERVAL / 1000, retry + 2, MAX_RETRIES_PER_KEY);
                            Response retryResponse = Response.ResponseContentChatBuilder(retryMsg, "agent");
                            streamDataCallback.onData(retryResponse);
                            
                            // 等待重试间隔
                            Thread.sleep(RETRY_INTERVAL);
                        }
                    }
                }
            }
        } catch (LLMInvocationException e) {
            // LLM调用异常，直接抛出
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String errorMsg = "请求被中断";
            Response errorResponse = Response.ResponseContentChatBuilder(errorMsg, "agent");
            streamDataCallback.onData(errorResponse);
            throw new LLMInvocationException(errorMsg, e, 0, 0, false);
        } catch (Exception e) {
            // 其他异常
            log.error("LLM_INVOCATION_ERROR | traceId={} | error={}", traceId, e.getMessage(), e);
            String errorMsg = getFriendlyErrorMessage(e);
            Response errorResponse = Response.ResponseContentChatBuilder(errorMsg, "agent");
            streamDataCallback.onData(errorResponse);
            throw new LLMInvocationException(errorMsg, e, 0, 0, false);
        }
    }

    /**
     * 执行流式调用（使用Spring AI ChatClient）
     */
    private String executeStreamCall(String prompt, StreamDataCallback streamDataCallback,
                                     String traceId, String apiKey) throws Exception {
        StringBuilder stringBuilder = new StringBuilder();
        AtomicBoolean firstChunkReceived = new AtomicBoolean(false);
        AtomicLong lastChunkTime = new AtomicLong(System.currentTimeMillis());
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean stalled = new AtomicBoolean(false);
        
        // 配置超时（30秒）
        final long TOTAL_TIMEOUT = 30000;
        final long STALL_TIMEOUT = 10000;
        
        // 创建CompletableFuture用于超时控制
        CompletableFuture<String> future = new CompletableFuture<>();
        
        // 设置总超时
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            if (!completed.get()) {
                log.warn("LLM_STREAM_TOTAL_TIMEOUT | traceId={} | timeout={}ms", traceId, TOTAL_TIMEOUT);
                future.completeExceptionally(new TimeoutException("总超时：" + TOTAL_TIMEOUT + "ms"));
            }
        }, TOTAL_TIMEOUT, TimeUnit.MILLISECONDS);
        
        // 设置响应卡死检测（仅在收到第一个chunk后检测）
        ScheduledFuture<?> stallTask = scheduler.scheduleAtFixedRate(() -> {
            if (!completed.get() && firstChunkReceived.get()) {
                long timeSinceLastChunk = System.currentTimeMillis() - lastChunkTime.get();
                if (timeSinceLastChunk > STALL_TIMEOUT) {
                    stalled.set(true);
                    log.warn("LLM_STREAM_STALL_DETECTED | traceId={} | stallTimeout={}ms | timeSinceLastChunk={}ms",
                            traceId, STALL_TIMEOUT, timeSinceLastChunk);
                    future.completeExceptionally(new TimeoutException("响应卡死：" + STALL_TIMEOUT + "ms无输出"));
                }
            }
        }, STALL_TIMEOUT, 1000, TimeUnit.MILLISECONDS); // 每秒检查一次
        
        try {
            // 执行流式调用（使用Spring AI ChatClient）
            Flux<String> flux = chatClient.prompt(prompt).stream().content();


            flux.doOnNext(content -> {
                        // 标记已收到第一个chunk
                        if (!firstChunkReceived.get()) {
                            firstChunkReceived.set(true);
                        }
                        lastChunkTime.set(System.currentTimeMillis());
                        stringBuilder.append(content);

                        // 发送流式数据
                        Response response = Response.ResponseContentChatBuilder(stringBuilder.toString(), "agent");
                        streamDataCallback.onData(response);
                    })
                    .doOnError(error -> {
                        log.error("LLM_STREAM_ERROR | traceId={} | error={}", traceId, error.getMessage(), error);
                        future.completeExceptionally(error);
                    })
                    .doOnComplete(() -> {
                        completed.set(true);
                        String fullContent = stringBuilder.toString();
                        future.complete(fullContent);
                    })
                    .blockLast(); // 阻塞等待完成
//            // 异步订阅流，不阻塞主线程
//            flux.subscribe(
//                content -> {
//                    // 标记已收到第一个chunk
//                    if (!firstChunkReceived.get()) {
//                        firstChunkReceived.set(true);
//                    }
//                    lastChunkTime.set(System.currentTimeMillis());
//                    stringBuilder.append(content);
//
//                    // 发送流式数据
//                    Response response = Response.ResponseContentChatBuilder(stringBuilder.toString(), "agent");
//                    streamDataCallback.onData(response);
//                },
//                error -> {
//                    log.error("LLM_STREAM_ERROR | traceId={} | error={}", traceId, error.getMessage(), error);
//                    future.completeExceptionally(error);
//                },
//                () -> {
//                    completed.set(true);
//                    String fullContent = stringBuilder.toString();
//                    future.complete(fullContent);
//                }
//            );
            
            // 等待调用完成或超时
            String result = future.get();
            
            // 取消定时任务
            timeoutTask.cancel(false);
            stallTask.cancel(false);
            
            return result;
            
        } catch (Exception e) {
            // 取消定时任务
            timeoutTask.cancel(false);
            stallTask.cancel(false);
            
            // 如果已接收到部分内容，返回部分内容
            if (stringBuilder.length() > 0) {
                log.info("LLM_STREAM_PARTIAL_RESPONSE | traceId={} | length={}",
                        traceId, stringBuilder.length());
                return stringBuilder.toString();
            }
            
            throw e;
        }
    }
    
    /**
     * 检查是否是Connection reset错误
     */
    private boolean isConnectionResetError(Throwable e) {
        if (e == null) {
            return false;
        }
        
        // 检查异常消息
        String errorMsg = e.getMessage();
        if (errorMsg != null && (errorMsg.contains("Connection reset") ||
                errorMsg.contains("connection reset"))) {
            return true;
        }
        
        // 检查是否是SocketException
        if (e instanceof SocketException) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Connection reset") || msg.contains("connection reset"))) {
                return true;
            }
        }
        
        // 递归检查cause
        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            return isConnectionResetError(cause);
        }
        
        return false;
    }
    
    /**
     * 获取友好错误消息
     */
    private String getFriendlyErrorMessage(Exception e) {
        String errorMsg = e.getMessage();
        if (errorMsg == null) {
            return "网络连接异常，请稍后重试";
        }
        
        // 检查是否是Connection reset错误
        if (isConnectionResetError(e)) {
            return "网络连接中断，正在重新尝试连接…";
        }
        
        // 网络异常
        if (e instanceof TimeoutException ||
                e instanceof SocketTimeoutException ||
                e instanceof IOException) {
            return "网络连接超时，正在重新尝试…";
        }
        
        // 服务不可用
        if (errorMsg.contains("500") || errorMsg.contains("502") ||
                errorMsg.contains("503") || errorMsg.contains("504")) {
            return "服务暂时不可用，请稍后再试";
        }
        
        // API限流
        if (errorMsg.contains("429") || errorMsg.contains("rate limit") ||
                errorMsg.contains("quota") || errorMsg.contains("Quota")) {
            return "请求过于频繁，请稍后再试";
        }
        
        // 认证错误
        if (errorMsg.contains("401") || errorMsg.contains("403") ||
                errorMsg.contains("unauthorized") || errorMsg.contains("Unauthorized")) {
            return "API密钥认证失败，请联系管理员";
        }
        
        // 默认错误消息
        return "网络连接异常，请稍后重试";
    }
}

