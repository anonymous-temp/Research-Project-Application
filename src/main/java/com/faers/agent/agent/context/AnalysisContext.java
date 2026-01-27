package com.faers.agent.agent.context;

import com.faers.agent.agent.impl.PerceptionAgent;
import com.faers.agent.model.Session;
import lombok.Data;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析上下文：存储对话过程中的参数、响应、状态等信息
 * 支持属性变化自动触发通知，无需手动保存/刷新
 */
@Data
public class AnalysisContext {
    // 核心：变化回调监听器（由 DialogContext 注入）
    private OnContextChangeListener changeListener;

    // 原有业务字段（保持完整）
    private String sessionId;
    private String userId;
    private String userMessage;
    private String rawJson;
    private Session session;
    private boolean isNewSession = false;
    private String cleanJson;

    private String markdown;
    private PerceptionAgent.IntentResult intentResult;
    private String responseContent;

    private String dataInfo;
    private String paperPreview;
    private String finalPaper;
    private boolean isConfirmedRevision = false;
    private SseEmitter sseEmitter;
    private String traceId;
    private boolean completed = false;
    private String literature;
    private String webSearchSource; // 网页搜索引擎来源（bocha/baidu/bing/duckduckgo等）

    // 可感知变化的集合字段（修复初始化报错）
    private Map<String, Object> queryParams;
    private List<String> memorySummaries;


    // ===== 无参构造：兼容原有实例化逻辑 =====
    public AnalysisContext() {
        // 初始化集合（默认空回调，后续通过 setChangeListener 绑定）
        this.queryParams = new ObservableMap<>();
        this.memorySummaries = new ObservableList<>();
    }



    // ===== 核心：注入变化回调（DialogContext 调用）=====
    public void setChangeListener(OnContextChangeListener changeListener) {
        this.changeListener = changeListener;
        // 同步更新集合的回调引用（关键：让集合操作能触发通知）
        ((ObservableMap<String, Object>) getQueryParams()).setListener(changeListener);
        ((ObservableList<String>) getMemorySummaries()).setListener(changeListener);
    }

    // ===== 内部工具：触发变化通知（仅内部调用）=====
    private void triggerChange() {
        if (changeListener != null) {
            try {
                changeListener.onContextChanged();
            } catch (Exception e) {
                // 避免回调异常影响业务主流程
                e.printStackTrace();
            }
        }
    }

    // ===== 内部工具：判断值是否真变化（避免重复触发）=====
    private boolean equals(Object oldVal, Object newVal) {
        if (oldVal == null && newVal == null) return true;
        if (oldVal == null || newVal == null) return false;
        return oldVal.equals(newVal);
    }

    // ===== 可感知变化的 Map 实现（支持自动通知）=====
    public static class ObservableMap<K, V> extends HashMap<K, V> {
        private OnContextChangeListener listener;

        // 无参构造：兼容初始化
        public ObservableMap() {
            this.listener = () -> {}; // 默认空回调（不触发任何操作）
        }

        // 带参构造：直接传入回调
        public ObservableMap(OnContextChangeListener listener) {
            this.listener = listener != null ? listener : () -> {};
        }

        // 后续绑定/更新回调
        public void setListener(OnContextChangeListener listener) {
            this.listener = listener != null ? listener : () -> {};
        }

        // 重写修改操作：触发通知
        @Override
        public V put(K key, V value) {
            V result = super.put(key, value);
            triggerChange();
            return result;
        }

        @Override
        public void putAll(Map<? extends K, ? extends V> m) {
            super.putAll(m);
            triggerChange();
        }

        @Override
        public V remove(Object key) {
            V result = super.remove(key);
            triggerChange();
            return result;
        }

        @Override
        public void clear() {
            super.clear();
            triggerChange();
        }

        private void triggerChange() {
            if (listener != null) {
                listener.onContextChanged();
            }
        }
    }

    // ===== 可感知变化的 List 实现（支持自动通知）=====
    public static class ObservableList<E> extends ArrayList<E> {
        private OnContextChangeListener listener;

        // 无参构造：兼容初始化
        public ObservableList() {
            this.listener = () -> {}; // 默认空回调
        }

        // 带参构造：直接传入回调
        public ObservableList(OnContextChangeListener listener) {
            this.listener = listener != null ? listener : () -> {};
        }

        // 后续绑定/更新回调
        public void setListener(OnContextChangeListener listener) {
            this.listener = listener != null ? listener : () -> {};
        }

        // 重写修改操作：触发通知
        @Override
        public boolean add(E e) {
            boolean result = super.add(e);
            triggerChange();
            return result;
        }

        @Override
        public void add(int index, E element) {
            super.add(index, element);
            triggerChange();
        }

        // @Override
        // public boolean addAll(List<? extends E> c) {
        //     boolean result = super.addAll(c);
        //     triggerChange();
        //     return result;
        // }

        @Override
        public boolean remove(Object o) {
            boolean result = super.remove(o);
            triggerChange();
            return result;
        }

        @Override
        public E remove(int index) {
            E result = super.remove(index);
            triggerChange();
            return result;
        }

        @Override
        public void clear() {
            super.clear();
            triggerChange();
        }

        private void triggerChange() {
            if (listener != null) {
                listener.onContextChanged();
            }
        }
    }

    // ===== 变化回调接口（内部定义，无需额外类）=====
    @FunctionalInterface
    public interface OnContextChangeListener {
        void onContextChanged();
    }

    // ===== 所有字段的 Getter/Setter（完整实现，带变化触发）=====
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        if (equals(this.sessionId, sessionId)) return;
        this.sessionId = sessionId;
        triggerChange();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        if (equals(this.userId, userId)) return;
        this.userId = userId;
        triggerChange();
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        if (equals(this.userMessage, userMessage)) return;
        this.userMessage = userMessage;
        triggerChange();
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        if (equals(this.rawJson, rawJson)) return;
        this.rawJson = rawJson;
        triggerChange();
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        if (equals(this.session, session)) return;
        this.session = session;
        triggerChange();
    }

    public boolean isNewSession() {
        return isNewSession;
    }

    public void setNewSession(boolean newSession) {
        if (this.isNewSession == newSession) return;
        this.isNewSession = newSession;
        triggerChange();
    }

    public String getCleanJson() {
        return cleanJson;
    }

    public void setCleanJson(String cleanJson) {
        if (equals(this.cleanJson, cleanJson)) return;
        this.cleanJson = cleanJson;
        triggerChange();
    }

    public PerceptionAgent.IntentResult getIntentResult() {
        return intentResult;
    }

    public void setIntentResult(PerceptionAgent.IntentResult intentResult) {
        if (equals(this.intentResult, intentResult)) return;
        this.intentResult = intentResult;
        triggerChange();
    }

    public List<String> getMemorySummaries() {
        // 懒加载：防止空指针（未初始化时自动创建）
        if (memorySummaries == null) {
            memorySummaries = new ObservableList<>(changeListener);
        }
        return memorySummaries;
    }

    public void setMemorySummaries(List<String> memorySummaries) {
        this.memorySummaries = new ObservableList<>(changeListener);
        if (memorySummaries != null && !memorySummaries.isEmpty()) {
            this.memorySummaries.addAll(memorySummaries);
        }
        triggerChange(); // 手动触发一次（确保更新生效）
    }

    public String getResponseContent() {
        return responseContent;
    }

    public void setResponseContent(String responseContent) {
        if (equals(this.responseContent, responseContent)) return;
        this.responseContent = responseContent;
        triggerChange();
    }

    public void setDataInfo(String dataInfo) {
        if (equals(this.dataInfo, dataInfo)) return;
        this.dataInfo = dataInfo;
        triggerChange();
    }

    public String getDataInfo() {
        return dataInfo;
    }

    public String getPaperPreview() {
        return paperPreview;
    }

    public void setPaperPreview(String paperPreview) {
        if (equals(this.paperPreview, paperPreview)) return;
        this.paperPreview = paperPreview;
        triggerChange();
    }

    public String getFinalPaper() {
        return finalPaper;
    }

    public void setFinalPaper(String finalPaper) {
        if (equals(this.finalPaper, finalPaper)) return;
        this.finalPaper = finalPaper;
        triggerChange();
    }

    public boolean isConfirmedRevision() {
        return isConfirmedRevision;
    }

    public void setConfirmedRevision(boolean confirmedRevision) {
        if (this.isConfirmedRevision == confirmedRevision) return;
        this.isConfirmedRevision = confirmedRevision;
        triggerChange();
    }

    public SseEmitter getSseEmitter() {
        return sseEmitter;
    }

    public void setSseEmitter(SseEmitter sseEmitter) {
        if (equals(this.sseEmitter, sseEmitter)) return;
        this.sseEmitter = sseEmitter;
        triggerChange();
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        if (equals(this.traceId, traceId)) return;
        this.traceId = traceId;
        triggerChange();
    }

    public Map<String, Object> getQueryParams() {
        // 懒加载：防止空指针（未初始化时自动创建）
        if (queryParams == null) {
            queryParams = new ObservableMap<>(changeListener);
        }
        return queryParams;
    }

    public void setQueryParams(Map<String, Object> queryParams) {
        this.queryParams = new ObservableMap<>(changeListener);
        if (queryParams != null && !queryParams.isEmpty()) {
            this.queryParams.putAll(queryParams);
        }
        triggerChange(); // 手动触发一次（确保更新生效）
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        if (this.completed == completed) return;
        this.completed = completed;
        triggerChange();
    }

    public String getLiterature() {
        return literature;
    }

    public void setLiterature(String literature) {
        if (equals(this.literature, literature)) return;
        this.literature = literature;
        triggerChange();
    }

    public String getWebSearchSource() {
        return webSearchSource;
    }

    public void setWebSearchSource(String webSearchSource) {
        if (equals(this.webSearchSource, webSearchSource)) return;
        this.webSearchSource = webSearchSource;
        triggerChange();
    }
}