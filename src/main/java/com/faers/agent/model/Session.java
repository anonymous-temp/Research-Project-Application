package com.faers.agent.model;

import com.faers.agent.config.AnalysisState;
import com.faers.agent.pojo.ProjectApplicationParams;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Session会话模型
 * 使用MongoDB进行持久化存储
 */
@Data
@Document(collection = "analysis_sessions")
public class Session implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 会话ID，作为MongoDB文档主键
     */
    @Id
    private String sessionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 药品名称
     */
    private String drugName;

    /**
     * 结构化输入的清洗后JSON
     */
    private String cleanJson;

    /**
     * 对话记忆条目
     */
    private List<MemoryEntry> memory = new ArrayList<>();

    /**
     * 生成的项目申报书版本
     */
    private List<PaperVersion> paperVersions = new ArrayList<>();

    /**
     * 会话创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最近访问时间（用于会话过期判断）
     */
    private LocalDateTime lastAccessed;

    /**
     * 当前对话所处的状态
     */
    private AnalysisState analysisState = AnalysisState.IDLE;

    /**
     * ===== 用户输入的项目申报参数（持久化到MongoDB，字段名保持一致） =====
     */
    private String topicName;                           // 课题名称
    private String disciplineField;                     // 申报学科领域
    private ProjectApplicationParams.TeamInfo teamInfo; // 研究团队情况
    private String researchFocus;                       // 研究重点方向
    private String outputLanguage;                      // 报告输出语言（中文/英文）

    // Getters and Setters
    public AnalysisState getAnalysisState() {
        return analysisState;
    }

    public void setAnalysisState(AnalysisState analysisState) {
        this.analysisState = analysisState;
    }

    public static class MemoryEntry implements Serializable {
        private static final long serialVersionUID = 1L;

        private String userMessage;
        private String systemResponse;
        private String summary;
        private boolean isImportant;
        private LocalDateTime timestamp;

        // Getters and Setters
        public String getUserMessage() { return userMessage; }
        public void setUserMessage(String userMessage) { this.userMessage = userMessage; }
        public String getSystemResponse() { return systemResponse; }
        public void setSystemResponse(String systemResponse) { this.systemResponse = systemResponse; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public boolean isImportant() { return isImportant; }
        public void setImportant(boolean important) { isImportant = important; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class PaperVersion implements Serializable {
        private static final long serialVersionUID = 1L;

        private String versionId;
        private String fullPaper;
        private String userInstruction;
        private LocalDateTime createdAt;

        private String version;
        private LocalDateTime timestamp;

        // Getters and Setters
        public String getVersionId() { return versionId; }
        public void setVersionId(String versionId) { this.versionId = versionId; }
        public String getFullPaper() { return fullPaper; }
        public void setFullPaper(String fullPaper) { this.fullPaper = fullPaper; }
        public String getUserInstruction() { return userInstruction; }
        public void setUserInstruction(String userInstruction) { this.userInstruction = userInstruction; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getVersion() {
            return version;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }

    // 其余字段的 Getter/Setter 由 @Data 自动生成
}
