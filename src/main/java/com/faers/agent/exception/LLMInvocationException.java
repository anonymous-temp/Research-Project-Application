package com.faers.agent.exception;

/**
 * LLM调用异常
 * 用于标识LLM调用相关的异常，便于全局异常处理器识别和处理
 */
public class LLMInvocationException extends AgentException {
    
    private final int attempt;
    private final int maxAttempts;
    private final boolean isConnectionReset;
    
    public LLMInvocationException(String message) {
        super(message);
        this.attempt = 0;
        this.maxAttempts = 0;
        this.isConnectionReset = false;
    }
    
    public LLMInvocationException(String message, Throwable cause) {
        super(message, cause);
        this.attempt = 0;
        this.maxAttempts = 0;
        this.isConnectionReset = false;
    }
    
    public LLMInvocationException(String message, Throwable cause, int attempt, int maxAttempts, boolean isConnectionReset) {
        super(message, cause);
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
        this.isConnectionReset = isConnectionReset;
    }
    
    public int getAttempt() {
        return attempt;
    }
    
    public int getMaxAttempts() {
        return maxAttempts;
    }
    
    public boolean isConnectionReset() {
        return isConnectionReset;
    }
}


