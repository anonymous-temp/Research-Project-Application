package com.faers.agent.exception;

import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.pojo.StreamDataCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

/**
 * 全局异常处理器
 * 统一处理LLM调用相关的异常，确保所有重试都执行完毕后才报错
 * 注意：由于使用WebSocket，不使用@ControllerAdvice，而是作为Component手动调用
 */
@Component
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理LLM调用异常
     * 当达到最大重试次数后仍然失败时，统一处理异常
     */
    public void handleLLMInvocationException(LLMInvocationException e, StreamDataCallback callback) {
        int attempt = e.getAttempt();
        int maxAttempts = e.getMaxAttempts();
        boolean isConnectionReset = e.isConnectionReset();
        
        log.error("LLM_INVOCATION_GLOBAL_HANDLER | attempt={}/{} | isConnectionReset={} | error={}", 
                attempt, maxAttempts, isConnectionReset, e.getMessage(), e);
        
        // 生成友好的错误消息
        String errorMsg = getFriendlyErrorMessage(e, isConnectionReset);
        
        // 通过回调发送错误消息给前端
        if (callback != null) {
            try {
                Response errorResponse = Response.ResponseContentChatBuilder(errorMsg, "agent");
                callback.onData(errorResponse);
            } catch (Exception ex) {
                log.error("发送错误消息到前端失败", ex);
            }
        }
        
        // 记录详细的错误信息
        if (attempt >= maxAttempts) {
            log.error("LLM_INVOCATION_ALL_RETRIES_FAILED | totalAttempts={} | finalError={}", 
                    maxAttempts, e.getMessage());
        }
    }

    /**
     * 处理RuntimeException（包含LLM调用失败的情况）
     */
    public void handleRuntimeException(RuntimeException e, StreamDataCallback callback) {
        // 检查是否是LLM调用相关的异常
        if (isLLMRelatedException(e)) {
            log.error("LLM_RELATED_RUNTIME_EXCEPTION | error={}", e.getMessage(), e);
            
            String errorMsg = getFriendlyErrorMessage(e, isConnectionResetError(e));
            
            if (callback != null) {
                try {
                    Response errorResponse = Response.ResponseContentChatBuilder(errorMsg, "agent");
                    callback.onData(errorResponse);
                } catch (Exception ex) {
                    log.error("发送错误消息到前端失败", ex);
                }
            }
        } else {
            // 其他RuntimeException，记录日志但不处理
            log.warn("UNHANDLED_RUNTIME_EXCEPTION | error={}", e.getMessage(), e);
        }
    }

    /**
     * 处理WebClientRequestException（Spring WebFlux的异常）
     */
    public void handleWebClientRequestException(WebClientRequestException e, StreamDataCallback callback) {
        log.error("WEB_CLIENT_REQUEST_EXCEPTION | error={}", e.getMessage(), e);
        
        String errorMsg = getFriendlyErrorMessage(e, isConnectionResetError(e));
        
        if (callback != null) {
            try {
                Response errorResponse = Response.ResponseContentChatBuilder(errorMsg, "agent");
                callback.onData(errorResponse);
            } catch (Exception ex) {
                log.error("发送错误消息到前端失败", ex);
            }
        }
    }

    /**
     * 处理SocketException（网络连接异常）
     */
    public void handleSocketException(SocketException e, StreamDataCallback callback) {
        log.error("SOCKET_EXCEPTION | error={}", e.getMessage(), e);
        
        String errorMsg = getFriendlyErrorMessage(e, isConnectionResetError(e));
        
        if (callback != null) {
            try {
                Response errorResponse = Response.ResponseContentChatBuilder(errorMsg, "agent");
                callback.onData(errorResponse);
            } catch (Exception ex) {
                log.error("发送错误消息到前端失败", ex);
            }
        }
    }

    /**
     * 处理超时异常
     */
    public void handleTimeoutException(Exception e, StreamDataCallback callback) {
        log.error("TIMEOUT_EXCEPTION | error={}", e.getMessage(), e);
        
        String errorMsg = "请求超时，请稍后重试";
        
        if (callback != null) {
            try {
                Response errorResponse = Response.ResponseContentChatBuilder(errorMsg, "agent");
                callback.onData(errorResponse);
            } catch (Exception ex) {
                log.error("发送错误消息到前端失败", ex);
            }
        }
    }

    /**
     * 处理所有其他异常
     */
    public void handleException(Exception e, StreamDataCallback callback) {
        log.error("UNHANDLED_EXCEPTION | error={}", e.getMessage(), e);
        
        String errorMsg = "系统处理出现异常，请稍后重试";
        
        if (callback != null) {
            try {
                Response errorResponse = Response.ResponseContentChatBuilder(errorMsg, "agent");
                callback.onData(errorResponse);
            } catch (Exception ex) {
                log.error("发送错误消息到前端失败", ex);
            }
        }
    }

    /**
     * 判断是否是LLM相关的异常
     */
    private boolean isLLMRelatedException(RuntimeException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        
        return message.contains("流式调用失败") ||
               message.contains("LLM") ||
               message.contains("Connection reset") ||
               message.contains("网络连接") ||
               message.contains("暂时无法处理");
    }

    /**
     * 判断是否是Connection reset错误
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

        // 检查是否是WebClientRequestException
        if (e.getClass().getName().contains("WebClientRequestException")) {
            if (errorMsg != null && errorMsg.contains("Connection reset")) {
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
     * 获取友好的错误消息
     */
    private String getFriendlyErrorMessage(Throwable e, boolean isConnectionReset) {
        if (isConnectionReset) {
            return "网络连接中断，正在重新尝试连接…";
        }

        String errorMsg = e.getMessage();
        if (errorMsg == null) {
            return "网络连接异常，请稍后重试";
        }

        // 网络异常
        if (e instanceof TimeoutException || 
            e instanceof SocketTimeoutException) {
            return "网络连接超时，正在重新尝试…";
        }

        // Connection reset错误（通过消息判断）
        if (errorMsg.contains("Connection reset") || errorMsg.contains("connection reset")) {
            return "网络连接中断，正在重新尝试连接…";
        }

        // 服务不可用
        if (errorMsg.contains("500") || errorMsg.contains("502") || 
            errorMsg.contains("503") || errorMsg.contains("504")) {
            return "服务暂时不可用，请稍后再试";
        }

        // API限流
        if (errorMsg.contains("429") || errorMsg.contains("rate limit")) {
            return "请求过于频繁，请稍后再试";
        }

        // 默认错误消息
        return "网络连接异常，请稍后重试";
    }
}

