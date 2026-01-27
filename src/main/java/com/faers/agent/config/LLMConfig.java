package com.faers.agent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * LLMConfig - Externalized configuration for LLM API keys
 *
 * This configuration class follows Spring best practices:
 * 1. Configuration is externalized to application.yml
 * 2. Sensitive values should be injected via environment variables
 * 3. Supports multiple API keys for failover
 * 4. Configurable retry and timeout parameters
 *
 * Usage in application.yml:
 * <pre>
 * llm:
 *   api-keys:
 *     - ${DASHSCOPE_API_KEY_1}
 *     - ${DASHSCOPE_API_KEY_2}
 *   retry:
 *     max-retries-per-key: 3
 *     cooling-time-ms: 30000
 *     retry-interval-ms: 3000
 *   timeout:
 *     total-ms: 30000
 *     stall-ms: 10000
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "llm")
@Validated
public class LLMConfig {

    private static final Logger log = LoggerFactory.getLogger(LLMConfig.class);

    /**
     * List of API keys for the LLM service
     * Should be injected via environment variables for security
     */
    private List<String> apiKeys = new ArrayList<>();

    /**
     * Retry configuration
     */
    private RetryConfig retry = new RetryConfig();

    /**
     * Timeout configuration
     */
    private TimeoutConfig timeout = new TimeoutConfig();

    /**
     * Model configuration
     */
    private ModelConfig model = new ModelConfig();

    @PostConstruct
    public void validate() {
        if (apiKeys == null || apiKeys.isEmpty()) {
            log.warn("No LLM API keys configured. Using fallback from spring.ai.dashscope.api-keys");
        } else {
            // Filter out empty keys and validate format
            List<String> validKeys = apiKeys.stream()
                    .filter(key -> key != null && !key.trim().isEmpty())
                    .filter(key -> !key.startsWith("${")) // Filter out unresolved env vars
                    .toList();

            if (validKeys.isEmpty()) {
                log.warn("No valid LLM API keys after filtering. Check environment variables.");
            } else {
                log.info("LLM configured with {} API key(s)", validKeys.size());
            }
        }
    }

    /**
     * Get list of valid API keys
     */
    public List<String> getValidApiKeys() {
        if (apiKeys == null) return new ArrayList<>();
        return apiKeys.stream()
                .filter(key -> key != null && !key.trim().isEmpty())
                .filter(key -> !key.startsWith("${")) // Filter out unresolved env vars
                .toList();
    }

    // Getters and Setters

    public List<String> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(List<String> apiKeys) {
        this.apiKeys = apiKeys;
    }

    public RetryConfig getRetry() {
        return retry;
    }

    public void setRetry(RetryConfig retry) {
        this.retry = retry;
    }

    public TimeoutConfig getTimeout() {
        return timeout;
    }

    public void setTimeout(TimeoutConfig timeout) {
        this.timeout = timeout;
    }

    public ModelConfig getModel() {
        return model;
    }

    public void setModel(ModelConfig model) {
        this.model = model;
    }

    /**
     * Retry configuration nested class
     */
    public static class RetryConfig {
        private int maxRetriesPerKey = 3;
        private long coolingTimeMs = 30000;
        private long retryIntervalMs = 3000;
        private boolean enableExponentialBackoff = true;
        private double backoffMultiplier = 1.5;
        private long maxBackoffMs = 30000;

        public int getMaxRetriesPerKey() {
            return maxRetriesPerKey;
        }

        public void setMaxRetriesPerKey(int maxRetriesPerKey) {
            this.maxRetriesPerKey = maxRetriesPerKey;
        }

        public long getCoolingTimeMs() {
            return coolingTimeMs;
        }

        public void setCoolingTimeMs(long coolingTimeMs) {
            this.coolingTimeMs = coolingTimeMs;
        }

        public long getRetryIntervalMs() {
            return retryIntervalMs;
        }

        public void setRetryIntervalMs(long retryIntervalMs) {
            this.retryIntervalMs = retryIntervalMs;
        }

        public boolean isEnableExponentialBackoff() {
            return enableExponentialBackoff;
        }

        public void setEnableExponentialBackoff(boolean enableExponentialBackoff) {
            this.enableExponentialBackoff = enableExponentialBackoff;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }

        public long getMaxBackoffMs() {
            return maxBackoffMs;
        }

        public void setMaxBackoffMs(long maxBackoffMs) {
            this.maxBackoffMs = maxBackoffMs;
        }
    }

    /**
     * Timeout configuration nested class
     */
    public static class TimeoutConfig {
        private long totalMs = 120000;
        private long stallMs = 30000;
        private long connectMs = 30000;
        private long readMs = 60000;

        public long getTotalMs() {
            return totalMs;
        }

        public void setTotalMs(long totalMs) {
            this.totalMs = totalMs;
        }

        public long getStallMs() {
            return stallMs;
        }

        public void setStallMs(long stallMs) {
            this.stallMs = stallMs;
        }

        public long getConnectMs() {
            return connectMs;
        }

        public void setConnectMs(long connectMs) {
            this.connectMs = connectMs;
        }

        public long getReadMs() {
            return readMs;
        }

        public void setReadMs(long readMs) {
            this.readMs = readMs;
        }
    }

    /**
     * Model configuration nested class
     */
    public static class ModelConfig {
        private String defaultModel = "qwen3-235b-a22b-instruct-2507";
        private double temperature = 0.7;
        private int maxTokens = 8192;
        private double topP = 0.9;

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public double getTopP() {
            return topP;
        }

        public void setTopP(double topP) {
            this.topP = topP;
        }
    }
}
