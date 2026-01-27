// com/faers/agent/agent/impl/MemoryAgent.java
package com.faers.agent.agent.impl;

import com.faers.agent.agent.Agent;
import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.agent.task.DelayedSummaryTaskManager;
import com.faers.agent.config.AnalysisState;
import com.faers.agent.exception.AgentException;
import com.faers.agent.model.Session;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.service.MongodbSessionStorageService;
import com.faers.agent.utils.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 记忆智能体（Memory Agent）
 * 职责：
 * - 维护会话级对话历史
 * - 提交异步任务，由 LLM 批量生成专业摘要
 * - 标记重要内容
 * - 控制记忆长度（防 OOM）
 * - 与 AnalysisState 状态机协同
 */
@Component
public class MemoryAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(MemoryAgent.class);

    @Autowired
    private MongodbSessionStorageService sessionStorageService;

    @Autowired
    private DelayedSummaryTaskManager summaryTaskManager;

    @Override
    public void process(AnalysisContext context, String id,
                        String parentId,
                        String userId,
                        StreamDataCallback streamDataCallback) {
        String traceId = context.getTraceId();
        log.info("MEMORY_PROCESS_START | traceId={}", traceId);

        try {
            Session session = context.getSession();

            if (context.isNewSession()) {
                session.setCreatedAt(LocalDateTime.now());
                session.setAnalysisState(AnalysisState.IDLE);
                session.setMemory(new ArrayList<>());

                PerceptionAgent.IntentResult intentResult = context.getIntentResult();
                if (intentResult != null && intentResult.getFirstIntent() != null && "EXPLORE".equals(intentResult.getFirstIntent().getAction())) {
                    session.setCleanJson(context.getCleanJson());
                }

                sessionStorageService.saveSession(session);
                log.info("MEMORY_NEW_SESSION_CREATED | traceId={} | sessionId={}", traceId, session.getSessionId());
                return;
            }

            List<Session.MemoryEntry> memory = session.getMemory();
            if (memory == null) {
                memory = new ArrayList<>();
                session.setMemory(memory);
            }

            // 清理旧记忆
            cleanupMemory(memory, traceId);

            // === 关键修改：立即写入原始内容，摘要留空或临时简写 ===
            String userMsg = context.getUserMessage();
            String systemResp = getResponseContent(context);
            log.info("MEMORY_GET_RESPONSE | traceId={} | responseContent={}", traceId, systemResp);

            Session.MemoryEntry entry = new Session.MemoryEntry();
            entry.setUserMessage(userMsg);
            entry.setSystemResponse(systemResp);
            entry.setSummary(""); // 先置为空，等 LLM 填充
            entry.setImportant(isImportantContent(context));
            entry.setTimestamp(LocalDateTime.now());

            int currentIndex = memory.size(); // 当前索引
            memory.add(entry);

            // 提交到异步摘要任务（5轮后触发）
            summaryTaskManager.submitForSummarization(
                session.getSessionId(),
                currentIndex,
                userMsg,
                systemResp
            );

            // 保存当前状态（带空摘要）
            sessionStorageService.saveSession(session);

            log.info("MEMORY_ENTRY_SAVED | traceId={} | placeholder_summary='{}'",
                traceId, truncate(userMsg, 60));

        } catch (Exception e) {
            log.error("MEMORY_PROCESS_ERROR | traceId={} | error={}", traceId, e.getMessage(), e);
            // 不抛出异常，避免影响主流程
            // throw new AgentException("记忆处理失败", e);
        }
    }

    // ==================== 内存清理策略 ====================
    private void cleanupMemory(List<Session.MemoryEntry> memory, String traceId) {
        int originalSize = memory.size();
        int maxSize = 30; // 最大保留条数

        if (memory.size() > maxSize) {
            int toRemove = memory.size() - maxSize;
            memory.subList(0, toRemove).clear();
            log.info("MEMORY_CLEANUP | traceId={} | removed={} entries (exceed max size {})",
                traceId, toRemove, maxSize);
        } else if (memory.size() > 15) {
            int toRemove = Math.min(5, memory.size() - 15);
            memory.subList(0, toRemove).clear();
            log.info("MEMORY_CLEANUP | traceId={} | removed={} entries (over 15)", traceId, toRemove);
        }
    }

    // ==================== 重要内容判断 ====================
    private boolean isImportantContent(AnalysisContext context) {
        if (context.getUserMessage() == null) return false;

        String userMsg = context.getUserMessage().toLowerCase();
        String action = context.getIntentResult() != null && context.getIntentResult().getFirstIntent() != null ? context.getIntentResult().getFirstIntent().getAction() : "";

        return "REVISE".equals(action) ||
               "CONFIRM".equals(action) ||
               "EXPORT".equals(action) ||
               "QUESTION".equals(action) ||
               userMsg.contains("记住") ||
               userMsg.contains("关键") ||
               userMsg.contains("重要");
    }

    // ==================== 工具方法 ====================

    private String getResponseContent(AnalysisContext context) {
        if (context.getResponseContent() != null && !context.getResponseContent().isEmpty()) {
            return context.getResponseContent();
        }
        if (context.getPaperPreview() != null && !context.getPaperPreview().isEmpty()) {
            return context.getPaperPreview();
        }
        return "无响应";
    }

    private String truncate(String str, int len) {
        return str.length() <= len ? str : str.substring(0, len) + "...";
    }
}
