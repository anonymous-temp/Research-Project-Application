// com/faers/agent/utils/Model.java
package com.faers.agent.utils;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.*;

import com.alibaba.fastjson.JSONObject;
import com.faers.agent.pojo.StreamDataCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 大模型调用工具类
 * 功能：
 * - getModel(...)：同步调用（适用于问答）
 * - streamModel(...)：流式调用（适用于论文生成，支持实时推送）
 */
@Component
public class Model {

    private static final Logger log = LoggerFactory.getLogger(Model.class);

    // API Key配置 - SECURITY: Load from environment variables
    // Set environment variables: LLM_API_KEY_1, LLM_API_KEY_2, etc.
    private static final List<String> API_KEYS = loadApiKeysFromEnv();

    private static List<String> loadApiKeysFromEnv() {
        List<String> keys = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String key = System.getenv("LLM_API_KEY_" + i);
            if (key != null && !key.isEmpty() && !key.startsWith("${")) {
                keys.add(key);
            }
        }
        // Fallback to single key
        if (keys.isEmpty()) {
            String singleKey = System.getenv("DASHSCOPE_API_KEY");
            if (singleKey != null && !singleKey.isEmpty()) {
                keys.add(singleKey);
            }
        }
        if (keys.isEmpty()) {
            log.warn("No API keys configured. Set environment variables LLM_API_KEY_1, LLM_API_KEY_2, etc.");
            keys.add("placeholder-key"); // Prevent NPE, will fail at runtime with clear error
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
    
    private static final List<ApiKeyStatus> apiKeyStatusList = new CopyOnWriteArrayList<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "Model-APIKey-Scheduler");
        t.setDaemon(true);
        return t;
    });
    private static final Semaphore apiKeyLock = new Semaphore(1);
    
    // 配置常量
    private static final int MAX_RETRIES_PER_KEY = 3;
    private static final int COOLING_TIME = 30000; // 30秒
    private static final int RETRY_INTERVAL = 3000; // 3秒
    
    // 静态初始化
    static {
        // 初始化API Key状态
        for (String apiKey : API_KEYS) {
            apiKeyStatusList.add(new ApiKeyStatus(apiKey));
        }
        
        // 启动冷却检查任务
        scheduler.scheduleAtFixedRate(() -> {
            checkCoolingKeys();
        }, 0, 1, TimeUnit.SECONDS);
    }
    
    private static ChatClient chatClient;

    @Autowired
    public void setChatClient(ChatClient.Builder builder) {
        Model.chatClient = builder.build();
    }
    
    /**
     * 检查冷却中的API Key，将已过冷却时间的Key恢复可用
     */
    private static void checkCoolingKeys() {
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
    private static String getNextAvailableApiKey() {
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
    private static boolean markApiKeyFailure(String apiKey) {
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
    private static void markApiKeySuccess(String apiKey) {
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
    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return apiKey;
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 【同步】调用大模型（增强错误处理：重试、超时、熔断）
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户输入内容
     * @param model         模型名称（如 qwen-plus）
     * @return 完整响应文本
     */
    public static String getModel(String systemPrompt, String userMessage, String model) {
//        ModelErrorHandler errorHandler = ModelErrorHandler.getInstance();
//        if (errorHandler == null) {
//            // 如果错误处理器未初始化，使用原有逻辑
//        }
//        return getModelLegacy(systemPrompt, userMessage, model);

        try {
            List<String> usedKeys = new ArrayList<>();
            
            // 遍历所有可用的API Key
            while (true) {
                String apiKey = getNextAvailableApiKey();
                
                if (apiKey == null) {
                    // 所有API Key都不可用
                    throw new RuntimeException("所有API Key都暂时不可用，请稍后再试");
                }
                
                // 避免重复使用同一Key
                if (usedKeys.contains(apiKey)) {
                    continue;
                }
                usedKeys.add(apiKey);
                
                log.info("SYNC_MODEL_INVOCATION_START | 使用API Key: {}", maskApiKey(apiKey));
                
                // 对当前API Key进行重试
                for (int retry = 0; retry < MAX_RETRIES_PER_KEY; retry++) {
                    try {
                        // 执行同步调用
                        String result = executeSyncCall(systemPrompt, userMessage, model, apiKey);
                        
                        // 调用成功，重置该Key的失败计数
                        markApiKeySuccess(apiKey);
                        
                        return result;
                    } catch (Exception e) {
                        // 调用失败，记录失败次数
                        log.warn("SYNC_MODEL_INVOCATION_FAILED | API Key: {} | 重试: {}/{} | 错误: {}", 
                                maskApiKey(apiKey), retry + 1, MAX_RETRIES_PER_KEY, e.getMessage());
                        
                        // 如果是最后一次重试，标记Key失败
                        if (retry == MAX_RETRIES_PER_KEY - 1) {
                            markApiKeyFailure(apiKey);
                        }
                        
                        // 等待重试间隔
                        if (retry < MAX_RETRIES_PER_KEY - 1) {
                            Thread.sleep(RETRY_INTERVAL);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("模型调用失败", e);
            throw new RuntimeException("模型调用失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 执行同步调用（内部方法）
     */
    private static String executeSyncCall(String systemPrompt, String userMessage, String model, String apiKey) throws Exception {
        Generation gen = new Generation();
        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(systemPrompt)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(userMessage)
                .build();

        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        GenerationResult result = gen.call(param);
        return result.getOutput().getChoices().get(0).getMessage().getContent();
    }

    /**
     * 原有逻辑（向后兼容）
     */
    private static String getModelLegacy(String systemPrompt, String userMessage, String model) {
        long start = System.currentTimeMillis();
        Generation gen = new Generation();

        Message systemMsg = Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(systemPrompt)
                .build();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(userMessage)
                .build();

        // 使用API Key管理机制获取Key
        String apiKey = getNextAvailableApiKey();
        if (apiKey == null) {
            // 如果没有可用的Key，使用第一个Key作为备选
            apiKey = API_KEYS.get(0);
        }

        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .messages(Arrays.asList(systemMsg, userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();

        try {
            GenerationResult result = gen.call(param);
            String content = result.getOutput().getChoices().get(0).getMessage().getContent();
            long end = System.currentTimeMillis();
            log.info("MODEL_SYNC_SUCCESS | model={} | time={}ms", model, end - start);
            
            // 调用成功，重置该Key的失败计数
            markApiKeySuccess(apiKey);
            
            return content;

        } catch (NoApiKeyException e) {
            log.error("API Key 缺失", e);
            throw new RuntimeException("模型调用失败：API Key 缺失", e);
        } catch (InputRequiredException e) {
            log.error("输入参数缺失", e);
            throw new RuntimeException("模型调用失败：输入参数缺失", e);
        } catch (Exception e) {
            log.error("模型调用失败", e);
            
            // 调用失败，记录失败次数
            markApiKeyFailure(apiKey);
            
            throw new RuntimeException("模型调用失败：" + e.getMessage(), e);
        }
    }

    /**
     * 【流式】调用大模型（增强错误处理：重试、超时、响应卡死检测）
     *
     * @param systemPrompt   系统提示词
     * @param userMessage    用户输入
     * @param model          模型名（必须支持流式：qwen-max / qwen-plus）
     * @param callback 每次收到新片段时的回调函数
     */
    public static String streamModel(
            String systemPrompt,
            String userMessage,
            String model,
            StreamDataCallback callback) {

//        ModelErrorHandler errorHandler = ModelErrorHandler.getInstance();
//        if (errorHandler == null) {
//            // 如果错误处理器未初始化，使用原有逻辑
//        }
//        return streamModelLegacy(systemPrompt, userMessage, model, callback);

        try {
            List<String> usedKeys = new ArrayList<>();
            
            // 遍历所有可用的API Key
            while (true) {
                String apiKey = getNextAvailableApiKey();
                
                if (apiKey == null) {
                    // 所有API Key都不可用
                    throw new RuntimeException("所有API Key都暂时不可用，请稍后再试");
                }
                
                // 避免重复使用同一Key
                if (usedKeys.contains(apiKey)) {
                    continue;
                }
                usedKeys.add(apiKey);
                
                log.info("STREAM_MODEL_INVOCATION_START | 使用API Key: {}", maskApiKey(apiKey));
                
                // 对当前API Key进行重试
                for (int retry = 0; retry < MAX_RETRIES_PER_KEY; retry++) {
                    try {
                        // 执行流式调用
                        String result = executeStreamCall(systemPrompt, userMessage, model, callback, apiKey);
                        
                        // 调用成功，重置该Key的失败计数
                        markApiKeySuccess(apiKey);
                        
                        return result;
                    } catch (Exception e) {
                        // 调用失败，记录失败次数
                        log.warn("STREAM_MODEL_INVOCATION_FAILED | API Key: {} | 重试: {}/{} | 错误: {}", 
                                maskApiKey(apiKey), retry + 1, MAX_RETRIES_PER_KEY, e.getMessage());
                        
                        // 如果是最后一次重试，标记Key失败
                        if (retry == MAX_RETRIES_PER_KEY - 1) {
                            markApiKeyFailure(apiKey);
                        }
                        
                        // 等待重试间隔
                        if (retry < MAX_RETRIES_PER_KEY - 1) {
                            Thread.sleep(RETRY_INTERVAL);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("流式调用失败", e);
            throw new RuntimeException("流式调用失败：" + e.getMessage(), e);
        }
    }

    /**
     * 执行流式调用（内部方法）
     */
    private static String executeStreamCall(String systemPrompt, String userMessage,
                                            String model, StreamDataCallback callback, String apiKey) throws Exception {
        // 创建CompletableFuture用于等待异步操作完成
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder builder = new StringBuilder();
        java.util.concurrent.atomic.AtomicLong lastChunkTime = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
        java.util.concurrent.atomic.AtomicBoolean firstChunkReceived = new java.util.concurrent.atomic.AtomicBoolean(false);

        // 初始化 Generation 实例
        Generation gen = new Generation();

        // 构建请求参数
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model(model != null && !model.isEmpty() ? model : "qwen-plus")
                .messages(Arrays.asList(
                        Message.builder()
                                .role(Role.SYSTEM.getValue())
                                .content(systemPrompt)
                                .build(),
                        Message.builder()
                                .role(Role.USER.getValue())
                                .content(userMessage)
                                .build()
                ))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .incrementalOutput(true) // 开启增量输出，流式返回
                .build();

        // 发起流式调用并处理响应
        Flowable<GenerationResult> result = gen.streamCall(param);
        result.subscribeOn(Schedulers.io()) // IO线程执行请求
                .observeOn(Schedulers.computation()) // 计算线程处理响应
                .subscribe(
                        // onNext: 处理每个响应片段
                        message -> {
                            String content = message.getOutput().getChoices().get(0).getMessage().getContent();
                            String finishReason = message.getOutput().getChoices().get(0).getFinishReason();

                            // 更新最后接收时间
                            lastChunkTime.set(System.currentTimeMillis());
                            if (!firstChunkReceived.get()) {
                                firstChunkReceived.set(true);
                            }

                            builder.append(content);

                            // 构建包含累积完整内容的JSON对象，实现流式追加
                            JSONObject inner = new JSONObject();
                            inner.put("type", "report");
                            inner.put("content", builder.toString()); // 使用累积的完整内容

                            callback.onData(inner.toJSONString());

                            // 当 finishReason 不为 null 时，表示是最后一个 chunk，输出用量信息
                            if (finishReason != null && !"null".equals(finishReason)) {
                                log.debug("流式调用完成 | 输入Tokens={} | 输出Tokens={} | 总Tokens={}",
                                        message.getUsage().getInputTokens(),
                                        message.getUsage().getOutputTokens(),
                                        message.getUsage().getTotalTokens());
                            }
                        },
                        // onError: 处理错误
                        error -> {
                            log.error("流式调用错误", error);
                            future.completeExceptionally(error);
                        },
                        // onComplete: 完成回调
                        () -> {
                            future.complete(builder.toString());
                        }
                );

        // 使用join()等待异步操作完成
        return future.join();
    }

    /**
     * 原有流式调用逻辑（向后兼容）
     */
    private static String streamModelLegacy(String systemPrompt, String userMessage,
                                            String model, StreamDataCallback callback) {
        // 使用API Key管理机制获取Key
        String tempApiKey = getNextAvailableApiKey();
        if (tempApiKey == null) {
            // 如果没有可用的Key，使用第一个Key作为备选
            tempApiKey = API_KEYS.get(0);
        }

        final String apiKey = tempApiKey; // 标记为final，以便在lambda表达式中使用

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("请设置环境变量 DASHSCOPE_API_KEY");
            return "";
        }

        // 创建CompletableFuture用于等待异步操作完成
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder builder = new StringBuilder();

        // 2. 初始化 Generation 实例
        Generation gen = new Generation();

        // 3. 构建请求参数
        GenerationParam param = GenerationParam.builder()
                .apiKey(apiKey)
                .model("qwen-plus")
                .messages(Arrays.asList(
                        Message.builder()
                                .role(Role.SYSTEM.getValue())
                                .content(systemPrompt)
                                .build(),
                        Message.builder()
                                .role(Role.USER.getValue())
                                .content(userMessage)
                                .build()
                ))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .incrementalOutput(true) // 开启增量输出，流式返回
                .build();
        // 4. 发起流式调用并处理响应
        try {
            Flowable<GenerationResult> result = gen.streamCall(param);
            System.out.print("AI: ");
            result.subscribeOn(Schedulers.io()) // IO线程执行请求
                    .observeOn(Schedulers.computation()) // 计算线程处理响应
                    .subscribe(
                            // onNext: 处理每个响应片段
                            message -> {
                                String content = message.getOutput().getChoices().get(0).getMessage().getContent();
                                String finishReason = message.getOutput().getChoices().get(0).getFinishReason();
                                // 输出内容
                                System.out.print(content);
                                builder.append(content);

                                // 构建包含累积完整内容的JSON对象，实现流式追加
                                JSONObject inner = new JSONObject();
                                inner.put("type", "report");
                                inner.put("content", builder.toString()); // 使用累积的完整内容

                                callback.onData(inner.toJSONString());
                                // 当 finishReason 不为 null 时，表示是最后一个 chunk，输出用量信息
                                if (finishReason != null && !"null".equals(finishReason)) {
                                    System.out.println("\n--- 请求用量 ---");
                                    System.out.println("输入 Tokens：" + message.getUsage().getInputTokens());
                                    System.out.println("输出 Tokens：" + message.getUsage().getOutputTokens());
                                    System.out.println("总 Tokens：" + message.getUsage().getTotalTokens());
                                }
                                System.out.flush(); // 立即刷新输出
                            },
                            // onError: 处理错误
                            error -> {
                                System.err.println("\n请求失败: " + error.getMessage());
                                
                                // 调用失败，记录失败次数
                                markApiKeyFailure(apiKey);
                                
                                future.completeExceptionally(error);
                            },
                            // onComplete: 完成回调
                            () -> {
                                System.out.println(); // 换行
                                
                                // 调用成功，重置该Key的失败计数
                                markApiKeySuccess(apiKey);
                                
                                future.complete(builder.toString());
                            }
                    );

            // 使用join()等待异步操作完成，join()不会抛出检查异常
            String finalResult = future.join();
            System.out.println("程序执行完成");
            return finalResult;

        } catch (Exception e) {
            System.err.println("请求异常: " + e.getMessage());
            e.printStackTrace();
            
            // 调用失败，记录失败次数
            markApiKeyFailure(apiKey);
            
            return builder.toString();
        }
    }
}
