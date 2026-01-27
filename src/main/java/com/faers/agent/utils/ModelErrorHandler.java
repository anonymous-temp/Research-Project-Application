//package com.faers.agent.utils;
//
//import com.faers.agent.config.LLMRetryConfig;
//import com.faers.agent.service.CircuitBreakerManager;
//import com.faers.agent.service.LLMMetricsCollector;
//import io.github.resilience4j.circuitbreaker.CircuitBreaker;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import java.io.IOException;
//import java.net.SocketTimeoutException;
//import java.util.concurrent.TimeoutException;
//
///**
// * Model类错误处理辅助类
// * 为Model.getModel()和Model.streamModel()提供错误处理逻辑
// */
//@Component
//public class ModelErrorHandler {
//
//    private static final Logger log = LoggerFactory.getLogger(ModelErrorHandler.class);
//    private static final String CIRCUIT_BREAKER_NAME = "dashscopeSDK";
//
//    private static ModelErrorHandler instance;
//
//    private final CircuitBreakerManager circuitBreakerManager;
//    private final LLMMetricsCollector metricsCollector;
//    private final LLMRetryConfig config;
//
//    @Autowired
//    public ModelErrorHandler(CircuitBreakerManager circuitBreakerManager,
//                            LLMMetricsCollector metricsCollector,
//                            LLMRetryConfig config) {
//        this.circuitBreakerManager = circuitBreakerManager;
//        this.metricsCollector = metricsCollector;
//        this.config = config;
//        ModelErrorHandler.instance = this;
//    }
//
//    /**
//     * 获取单例实例（用于静态方法调用）
//     */
//    public static ModelErrorHandler getInstance() {
//        return instance;
//    }
//
//    /**
//     * 执行带重试的调用
//     */
//    public <T> T executeWithRetry(String invocationType, ModelCallable<T> callable) throws Exception {
//        int attempt = 0;
//        int maxAttempts = config.getRetry().getMaxAttempts();
//        Exception lastException = null;
//
//        while (attempt < maxAttempts) {
//            attempt++;
//            long attemptStartTime = System.currentTimeMillis();
//
//            try {
//                // 检查熔断器状态
//                CircuitBreaker circuitBreaker = circuitBreakerManager.getCircuitBreaker(CIRCUIT_BREAKER_NAME);
//                if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
//                    log.warn("MODEL_CIRCUIT_BREAKER_OPEN | attempt={}", attempt);
//                    throw new RuntimeException("服务暂时不可用，请稍后再试");
//                }
//
//                // 执行调用（使用熔断器保护）
//                T result = circuitBreaker.executeSupplier(() -> {
//                    try {
//                        return callable.call();
//                    } catch (Exception e) {
//                        throw new RuntimeException(e);
//                    }
//                });
//
//                // 记录成功
//                long responseTime = System.currentTimeMillis() - attemptStartTime;
//                metricsCollector.recordSuccess(invocationType, responseTime, attempt - 1);
//                log.info("MODEL_INVOCATION_SUCCESS | type={} | attempt={} | responseTime={}ms",
//                        invocationType, attempt, responseTime);
//                return result;
//
//            } catch (Exception e) {
//                lastException = e;
//                long responseTime = System.currentTimeMillis() - attemptStartTime;
//                boolean shouldRetry = shouldRetry(e, attempt, maxAttempts);
//
//                if (shouldRetry) {
//                    // 计算重试延迟（指数退避）
//                    long delay = calculateRetryDelay(attempt);
//                    log.warn("MODEL_INVOCATION_RETRY | type={} | attempt={} | delay={}ms | error={}",
//                            invocationType, attempt, delay, e.getMessage());
//
//                    try {
//                        Thread.sleep(delay);
//                    } catch (InterruptedException ie) {
//                        Thread.currentThread().interrupt();
//                        metricsCollector.recordFailure(invocationType, responseTime, attempt - 1);
//                        throw new RuntimeException("重试被中断", ie);
//                    }
//                } else {
//                    // 不再重试，记录失败
//                    metricsCollector.recordFailure(invocationType, responseTime, attempt - 1);
//                    log.error("MODEL_INVOCATION_FAILED | type={} | attempt={} | error={}",
//                            invocationType, attempt, e.getMessage(), e);
//                    throw e;
//                }
//            }
//        }
//
//        // 所有重试都失败
//        metricsCollector.recordFailure(invocationType, 0, maxAttempts - 1);
//        throw new RuntimeException("暂时无法处理您的请求，您可以稍后重试或简化您的问题", lastException);
//    }
//
//    /**
//     * 判断是否应该重试
//     */
//    private boolean shouldRetry(Exception e, int attempt, int maxAttempts) {
//        if (attempt >= maxAttempts) {
//            return false;
//        }
//
//        // 网络异常：超时、连接断开
//        if (e instanceof TimeoutException ||
//            e instanceof SocketTimeoutException ||
//            e instanceof IOException ||
//            (e.getCause() != null && (
//                e.getCause() instanceof TimeoutException ||
//                e.getCause() instanceof SocketTimeoutException ||
//                e.getCause() instanceof IOException))) {
//            return true;
//        }
//
//        // 检查是否是5xx错误（通过异常消息判断）
//        String errorMsg = e.getMessage();
//        if (errorMsg != null) {
//            if (errorMsg.contains("500") || errorMsg.contains("502") ||
//                errorMsg.contains("503") || errorMsg.contains("504")) {
//                return true;
//            }
//            // API限流错误（429）
//            if (errorMsg.contains("429") || errorMsg.contains("rate limit")) {
//                return true;
//            }
//        }
//
//        // 其他异常不重试
//        return false;
//    }
//
//    /**
//     * 计算重试延迟（指数退避）
//     */
//    private long calculateRetryDelay(int attempt) {
//        long initialInterval = config.getRetry().getInitialInterval();
//        double multiplier = config.getRetry().getMultiplier();
//        long maxInterval = config.getRetry().getMaxInterval();
//
//        long delay = (long) (initialInterval * Math.pow(multiplier, attempt - 1));
//        return Math.min(delay, maxInterval);
//    }
//
//    /**
//     * 可调用接口
//     */
//    @FunctionalInterface
//    public interface ModelCallable<T> {
//        T call() throws Exception;
//    }
//}
//
//
//
