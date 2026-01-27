// com/faers/agent/config/AnalysisState.java
package com.faers.agent.config;

/**
 * 对话状态枚举（用于控制多轮交互流程）
 * 该状态保存在 Session 中，随对话演进而更新
 */
public enum AnalysisState {

    /**
     * 初始空闲状态
     * - 用户尚未输入或对话已结束
     * - 下一步：接收新查询
     */
    IDLE,

    /**
     * 正在收集参数中
     * - 用户已开始输入但关键参数不全（如缺少 roleCodes）
     * - 系统持续追问
     * - 可被 REVISE 意图中断
     */
    COLLECTING_PARAMS,

    /**
     * 参数已齐备，待分析
     * - 所有必要参数已满足（至少：课题名称 + 研究领域）
     * - 用户确认后进入分析
     */
    PARAMS_READY,

    /**
     * 正在执行项目申报书分析
     * - 已调用相关分析服务
     * - 可响应 QUESTION / CANCEL
     */
    ANALYZING,

    /**
     * 分析完成，结果就绪
     * - cleanJson 已生成
     * - 可触发报告生成或导出
     */
    ANALYSIS_DONE,

    /**
     * 报告已生成（论文/Markdown/图表）
     * - finalPaper 已填充
     * - 用户可修改、导出、提问
     */
    REPORT_GENERATED,

    /**
     * 报告生成后等待用户反馈
     * - 推送：“是否需要调整？”
     * - 超时自动结束会话
     */
    AWAITING_USER_INPUT_AFTER_REPORT,

    /**
     * 任务已取消
     * - 用户明确说“取消”
     * - 可清空上下文或重新开始
     */
    CANCELLED;

    // ===== 辅助方法 =====

    /**
     * 是否处于活跃分析流程中
     */
    public boolean isActive() {
        return this != IDLE && this != CANCELLED;
    }

    /**
     * 是否已完成分析（可用于触发报告）
     */
    public boolean isAnalysisCompleted() {
        return this == ANALYSIS_DONE || this == REPORT_GENERATED || this == AWAITING_USER_INPUT_AFTER_REPORT;
    }

    /**
     * 是否允许修改参数
     */
    public boolean isReviseAllowed() {
        return this != IDLE && this != CANCELLED;
    }

    /**
     * 是否可以导出
     */
    public boolean isExportAllowed() {
        return this == ANALYSIS_DONE || this == REPORT_GENERATED || this == AWAITING_USER_INPUT_AFTER_REPORT;
    }
}
