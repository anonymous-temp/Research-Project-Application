package com.faers.agent.agent.context;

import com.faers.agent.config.AnalysisState;
import com.faers.agent.model.Session;

import java.time.LocalDateTime;

public class DialogContext {

    private final String dialogId;        // 对话ID（来自服务端）

    private final Session session;        // 会话数据（记忆、历史报告等）
    private final AnalysisContext analysisContext; // 分析上下文
    private volatile long lastActiveTime; // 最后一次交互时间（毫秒）








    public DialogContext(String dialogId) {
        this.dialogId = dialogId;

        this.session = new Session();
        this.session.setSessionId(dialogId);

        this.session.setCreatedAt(LocalDateTime.now());
        this.session.setAnalysisState(AnalysisState.IDLE);
        this.session.setMemory(new java.util.ArrayList<>());
        this.session.setPaperVersions(new java.util.ArrayList<>());

        // 关键：创建 AnalysisContext 时，注入回调（实现自动通知）
        this.analysisContext = new AnalysisContext();
        this.analysisContext.setChangeListener(this::onAnalysisContextChanged); // 回调绑定
        this.analysisContext.setSessionId(dialogId);

        this.analysisContext.setSession(session);
        // 初始化感知型集合（传入回调）
        this.analysisContext.setQueryParams(new java.util.HashMap<>());
        this.analysisContext.setMemorySummaries(new java.util.ArrayList<>());

        this.lastActiveTime = System.currentTimeMillis();
    }

    // ===== 回调实现：AnalysisContext 变化时触发 =====
    private void onAnalysisContextChanged() {
        refreshLastActive(); // 自动刷新最后活跃时间
        // 这里可以直接调用 DialogManager 保存，但为了解耦，推荐让 DialogManager 监听
        // 下文会优化 DialogManager，实现自动保存
    }

    // ===== 原有方法保持不变 =====
    public String getDialogId() { return dialogId; }

    public Session getSession() { return session; }
    public AnalysisContext getAnalysisContext() { return analysisContext; }
    public long getLastActiveTime() { return lastActiveTime; }

    public void refreshLastActive() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    public void reset() {
        this.session.getPaperVersions().clear();
        this.session.getMemory().clear();
        this.session.setAnalysisState(AnalysisState.IDLE);
        this.analysisContext.getQueryParams().clear();
        this.analysisContext.getMemorySummaries().clear();
        this.analysisContext.setCleanJson(null);
        this.analysisContext.setMarkdown(null);
        this.analysisContext.setFinalPaper(null);
        this.analysisContext.setResponseContent(null);
        this.analysisContext.setIntentResult(null);

        this.lastActiveTime = System.currentTimeMillis();
    }
}