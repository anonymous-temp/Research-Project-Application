// com/faers/agent/task/DelayedSummaryTaskManager.java
package com.faers.agent.agent.task;

import com.faers.agent.model.Session;
import com.faers.agent.service.MongodbSessionStorageService;
import com.faers.agent.utils.Model;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

@Component
public class DelayedSummaryTaskManager {

    private static final Logger log = LoggerFactory.getLogger(DelayedSummaryTaskManager.class);

    @Autowired private MongodbSessionStorageService sessionStorageService;

    // 待处理的记忆条目缓存：sessionId -> List<UnprocessedEntry>
    private final Map<String, List<UnprocessedEntry>> pendingEntries = new ConcurrentHashMap<>();

    // 执行调度器
    private ScheduledExecutorService scheduler;

    @PostConstruct
    public void init() {
        this.scheduler = Executors.newScheduledThreadPool(2, r -> new Thread(r, "summary-task-pool"));
    }

    /**
     * 提交一条待摘要的记忆（5轮后批量处理）
     */
    public void submitForSummarization(String sessionId, int indexInMemory, String userMsg, String systemResp) {
        pendingEntries.computeIfAbsent(sessionId, k -> new ArrayList<>())
                      .add(new UnprocessedEntry(indexInMemory, userMsg, systemResp, LocalDateTime.now()));
    }

    /**
     * 触发指定会话的摘要生成（延迟5轮后由外部调用）
     */
    @Async
    public void processPendingSummaries(String sessionId) {
        List<UnprocessedEntry> entries = pendingEntries.remove(sessionId);
        if (entries == null || entries.isEmpty()) return;

        try {
            Session session = sessionStorageService.getSession(sessionId);
            if (session == null || session.getMemory() == null) return;

            // 批量调用 LLM 生成摘要
            List<String> summaries = generateSummariesBatch(entries);

            // 更新 memory 中对应条目
            for (int i = 0; i < Math.min(entries.size(), summaries.size()); i++) {
                UnprocessedEntry entry = entries.get(i);
                if (entry.getIndex() < session.getMemory().size()) {
                    Session.MemoryEntry mem = session.getMemory().get(entry.getIndex());
                    mem.setSummary(summaries.get(i)); // ✅ 填入 LLM 生成的摘要
                }
            }

            // 保存回存储
            sessionStorageService.saveSession(session);
            log.info("ASYNC_SUMMARY_COMPLETED | sessionId={} | generated={} summaries", sessionId, summaries.size());

        } catch (Exception e) {
            log.error("ASYNC_SUMMARY_FAILED | sessionId={}", sessionId, e);
        }
    }

    /**
     * 批量生成摘要（一次 prompt 包含多条）
     */
    private List<String> generateSummariesBatch(List<UnprocessedEntry> entries) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("请为以下 ").append(entries.size()).append(" 条对话生成独立摘要（每条一句话，不超过80字）：\n\n");

        for (int i = 0; i < entries.size(); i++) {
            UnprocessedEntry e = entries.get(i);
            promptBuilder.append(i + 1).append(". 用户：").append(e.getUserMessage())
                         .append("\n   系统：").append(e.getSystemResponse()).append("\n\n");
        }

        promptBuilder.append("输出格式：\n1. 摘要1\n2. 摘要2\n...");

        try {
            String response = Model.getModel(
                "你是一个高效的对话摘要生成器，请为每条对话生成简洁事实性总结。",
                promptBuilder.toString(),
                "qwen-plus"
            );

            return parseNumberedList(response);

        } catch (Exception e) {
            log.warn("BATCH_SUMMARY_FAILED, using fallback", e);
            return entries.stream()
                .map(x -> truncate(x.getUserMessage(), 77) + "...")
                .collect(Collectors.toList());
        }
    }

    private List<String> parseNumberedList(String text) {
        return Arrays.stream(text.split("\\r?\\n"))
                     .filter(line -> line.trim().matches("^\\d+\\..*"))
                     .map(line -> line.replaceAll("^\\d+\\.\\s*", "").trim())
                     .filter(s -> !s.isEmpty())
                     .collect(Collectors.toList());
    }

    private String truncate(String str, int len) {
        return str.length() <= len ? str : str.substring(0, len);
    }

    // ===== 内部类 =====
    private static class UnprocessedEntry {
        private final int index;
        private final String userMessage;
        private final String systemResponse;
        private final LocalDateTime timestamp;

        public UnprocessedEntry(int index, String userMessage, String systemResponse, LocalDateTime timestamp) {
            this.index = index;
            this.userMessage = userMessage;
            this.systemResponse = systemResponse;
            this.timestamp = timestamp;
        }

        public int getIndex() { return index; }
        public String getUserMessage() { return userMessage; }
        public String getSystemResponse() { return systemResponse; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
