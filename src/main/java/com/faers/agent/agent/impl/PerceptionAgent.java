// com/faers/agent/agent/impl/PerceptionAgent.java
package com.faers.agent.agent.impl;

import com.faers.agent.agent.Agent;
import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.config.AnalysisState;
import com.faers.agent.exception.AgentException;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.utils.Model;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 意图识别智能体（Perception Agent）
 * 职责：分析用户输入的真实意图，驱动后续流程调度
 * 特性：
 * - 支持全流程动作：EXPLORE / REVISE / QUESTION / EXPORT / CONFIRM 等
 * - 双引擎驱动：规则匹配（高效） + LLM 理解（语义强）
 * - 结合当前对话状态（AnalysisState）进行上下文感知判断
 * - 若用户明确表示“不补充参数”，则标记为 completed = true
 */
@Component
public class PerceptionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(PerceptionAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ===== 支持的动作类型 =====
    public enum Action {
        EXPLORE,                // 开始或继续探索性分析（首次输入或补充课题）
        REVISE,                 // 修改已有参数（6个参数）
        QUESTION,               // 提问类问题（方法、指标解释）
        CONFIRM,                // 明确确认继续下一步 → 触发 completed
        CANCEL,                 // 取消当前流程
        EXPORT,                 // 导出报告请求
        FEEDBACK,               // 正向反馈（“清楚了”、“很好”）
        STATUS_QUERY            // 查询任务进度（“还在分析吗？”）
    }

    /**
     * 单个意图识别结果对象
     */
    public static class SingleIntentResult {
        private String action;
        private double confidence;
        private String explanation;
        private int priority;

        public SingleIntentResult() {}

        public SingleIntentResult(String action, double confidence, String explanation, int priority) {
            this.action = action;
            this.confidence = confidence;
            this.explanation = explanation;
            this.priority = priority;
        }

        // Getters and Setters
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }

        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }

        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
    }

    /**
     * 意图识别结果对象 - 支持多意图
     */
    public static class IntentResult {
        private List<SingleIntentResult> intentList;

        public IntentResult() {
            this.intentList = new ArrayList<>();
        }

        public IntentResult(List<SingleIntentResult> intentList) {
            this.intentList = intentList;
        }

        // 添加单个意图
        public void addIntent(SingleIntentResult intent) {
            this.intentList.add(intent);
        }

        // 添加单个意图（便捷方法）
        public void addIntent(String action, double confidence, String explanation, int priority) {
            this.intentList.add(new SingleIntentResult(action, confidence, explanation, priority));
        }

        // 按优先级排序意图列表（数字越小优先级越高）
        public void sortByPriority() {
            this.intentList.sort(Comparator.comparingInt(SingleIntentResult::getPriority));
        }

        // Getters and Setters
        public List<SingleIntentResult> getIntentList() { return intentList; }
        public void setIntentList(List<SingleIntentResult> intentList) { this.intentList = intentList; }

        // 获取第一个意图（最高优先级）
        public SingleIntentResult getFirstIntent() {
            return intentList.isEmpty() ? null : intentList.get(0);
        }

        // 获取意图数量
        public int getIntentCount() {
            return intentList.size();
        }
    }

    @Override
    public void process(AnalysisContext context, String id,
                        String parentId,
                        String userId,
                        StreamDataCallback streamDataCallback) {
        String traceId = context.getTraceId();
        String userMessage = context.getUserMessage().trim();

        if (userMessage == null || userMessage.isEmpty()) {
            throw new AgentException("用户输入为空，无法识别意图");
        }

        // 获取上下文信息
        Map<String, Object> params = context.getQueryParams();
        AnalysisState currentState = getCurrentState(context);
        boolean hasResults = context.getCleanJson() != null && !context.getCleanJson().isEmpty();
        boolean hasReport = context.getFinalPaper() != null && !context.getFinalPaper().isEmpty();

        log.info("PERCEPTION_START | traceId={} | message='{}' | state={}",
                traceId, userMessage, currentState);

        try {
            IntentResult result = null;

            List<String> memorySummaries = context.getMemorySummaries();

            // Step 1: 交由 LLM 深度理解（结合上下文）
            if (result == null) {
                result = analyzeWithLLM(userMessage, params, currentState, hasResults, hasReport,memorySummaries);
            }

            // Step 2: 设置最终意图结果
            context.setIntentResult(result);

            // 获取最高优先级的意图
            SingleIntentResult firstIntent = result.getFirstIntent();
            if (firstIntent != null) {
                log.info("PERCEPTION_RESULT | traceId={} | action={} | conf={:.2f} | reason='{}'",
                        traceId, firstIntent.getAction(), firstIntent.getConfidence(), firstIntent.getExplanation());

                // 🔥 新增逻辑：根据意图决定是否标记为“参数已齐备”
                handleCompletionFlag(context, firstIntent.getAction(), userMessage);
            } else {
                log.info("PERCEPTION_RESULT | traceId={} | no valid intents found", traceId);
            }

        } catch (Exception e) {
            log.error("PERCEPTION_ERROR | traceId={}", traceId, e);
            throw new AgentException("意图识别失败", e);
        }
    }
    // ==================== LLM 深度理解（结合上下文）====================
    private IntentResult analyzeWithLLM(
            String input,
            Map<String, Object> params,
            AnalysisState state,
            boolean hasResults,
            boolean hasReport,
            List<String> memorySummaries) {

        String currentSummary = buildParamsSummary(params);
        String systemPrompt = "你是项目申报书研究系统的意图理解专家，请根据用户输入和当前系统状态判断其真实意图。\n" +
                "\n" +
                "=== 当前系统状态 ===\n" +
                "- 对话阶段: " + state + "\n" +
                "- 是否已有分析结果: " + (hasResults ? "是" : "否") + "\n" +
                "- 是否已生成报告: " + (hasReport ? "是" : "否") + "\n" +
                "\n" +
                "- 上下文记忆: " + (memorySummaries.isEmpty() ? "无" : String.join("\n", memorySummaries)) + "\n" +
                "\n" +
                "=== 已收集参数 ===\n" +
                (currentSummary.isEmpty() ? "暂无参数" : currentSummary) + "\n" +
                "\n" +
                "=== 可选动作说明 ===\n" +
                "- EXPLORE: 开始新分析 或 继续参数收集（适用于首次输入或补充参数）\n" +
                "- REVISE: 修改已有参数并重新分析（如调整研究方向、时间等）\n" +
                "- QUESTION: 提出问题（方法、指标解释、数据来源等）\n" +
                "- CONFIRM: 同意继续下一步（如\"开始吧\"、\"可以\"），或者用户说不再补充参数\n" +
                "- CANCEL: 取消当前流程\n" +
                "- EXPORT: 请求导出结果（PDF/Excel）\n" +
                "- FEEDBACK: 正向评价（如\"清楚了\"、\"很好\"）\n" +
                "- STATUS_QUERY: 询问任务进度（如\"还在分析吗？\"）\n" +
                "\n" +
                "=== 优先级规则 (数字越小优先级越高) ===\n" +
                "第一优先级 (1): 修改。\n" +
                "第二优先级 (2): 提问。\n" +
                "第三优先级 (3): 状态查询。\n" +
                "第四优先级 (4): 取消、确认。\n" +
                "第五优先级 (5): 导出报告。\n" +
                "\n" +
                "=== 多意图识别说明 ===\n" +
                "用户单次输入可能包含多个操作 (例如: \"帮我导出一份报告，另外再问问系统状态\")。请识别出所有意图，并为每个意图分配对应的优先级。\n" +
                "\n" +
                "请以 JSON 格式输出：\n" +
                "{\n" +
                "  \"intents\": [\n" +
                "    {\n" +
                "      \"action\": \"STATUS_QUERY\",\n" +
                "      \"confidence\": 0.95,\n" +
                "      \"explanation\": \"用户询问系统状态\",\n" +
                "      \"priority\": 2\n" +
                "    },\n" +
                "    {\n" +
                "      \"action\": \"EXPORT\",\n" +
                "      \"confidence\": 0.90,\n" +
                "      \"explanation\": \"用户请求导出报告\",\n" +
                "      \"priority\": 4\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "\n" +
                "单意图示例：\n" +
                "{\n" +
                "  \"intents\": [\n" +
                "    {\n" +
                "      \"action\": \"REVISE\",\n" +
                "      \"confidence\": 0.93,\n" +
                "      \"explanation\": \"用户要求修改研究方向，属于参数修改\",\n" +
                "      \"priority\": 1\n" +
                "    }\n" +
                "  ]\n" +
                "}\n" +
                "\n" +
                "规则：\n" +
                "- confidence ∈ [0.6, 1.0]\n" +
                "- explanation 必须简短清晰\n" +
                "- 必须按优先级排序意图（优先级数字小的在前）\n" +
                "- 不要编造，不确定时 confidence 设为 0.6~0.7\n" +
                "- 必须返回包含intents数组的JSON格式";

        try {
            String modelResponse = Model.getModel(systemPrompt, "用户输入：" + input, "qwen-plus");
            JsonNode root = objectMapper.readTree(modelResponse);
            JsonNode intentsArray = root.path("intents");

            IntentResult result = new IntentResult();

            // 解析所有意图
            if (intentsArray.isArray()) {
                for (JsonNode intentNode : intentsArray) {
                    String action = intentNode.path("action").asText();
                    double confidence = intentNode.path("confidence").asDouble(0.6);
                    String explanation = intentNode.path("explanation").asText("LLM 分析");
                    int priority = intentNode.path("priority").asInt(4);

                    // 验证 action 是否合法
                    try {
                        Action.valueOf(action);
                    } catch (IllegalArgumentException e) {
                        action = Action.EXPLORE.name();
                        explanation = "未知动作，默认进入探索模式";
                    }

                    // 添加到结果列表
                    result.addIntent(action, confidence, explanation, priority);
                }
            } else {
                // 兼容旧格式或解析失败时，创建单个意图
                String action = root.path("action").asText(Action.EXPLORE.name());
                double confidence = root.path("confidence").asDouble(0.6);
                String explanation = root.path("explanation").asText("LLM 分析");
                
                // 根据动作确定优先级
                int priority = getPriorityForAction(action);
                
                result.addIntent(action, confidence, explanation, priority);
            }

            // 确保按优先级排序
            result.sortByPriority();

            return result;

        } catch (Exception e) {
            log.warn("LLM 意图识别失败，降级为 EXPLORE", e);
            IntentResult result = new IntentResult();
            result.addIntent(Action.EXPLORE.name(), 0.6, "LLM 解析失败，降级处理", 4);
            return result;
        }
    }

    // ==================== 关键新增：处理是否应标记为“已完成” ====================
    private void handleCompletionFlag(AnalysisContext context, String action, String userMessage) {
        // 用户明确说“直接分析”、“不用补充”、“就这样”
        boolean impliesComplete = userMessage.matches(".*(直接分析|马上分析|不用补充|不需要了|就是这样|就这些|够了|开始吧|确认).*$");

        // 如果是 CONFIRM 动作，则认为用户希望跳过追问，直接进入下一阶段
        if ("CONFIRM".equals(action) || impliesComplete) {
            log.info("💡 用户确认完成参数收集 | traceId={}", context.getTraceId());
            context.setCompleted(true); // 标记为完成
            // 同时更新 Session 状态
            if (context.getSession() != null) {
                context.getSession().setAnalysisState(AnalysisState.PARAMS_READY);
            }
        } else if ("CANCEL".equals(action)) {
            context.setCompleted(false);
        }
    }

    // ==================== 工具方法 ====================

    private AnalysisState getCurrentState(AnalysisContext context) {
        return context.getSession() != null && context.getSession().getAnalysisState() != null ?
                context.getSession().getAnalysisState() :
                AnalysisState.IDLE;
    }

    // ==================== 新增：维护参数收集状态表 ====================
    private void updateParamStatus(AnalysisContext context, String action, Map<String, Object> params) {
        // 1. 根据意图类型确定必需参数清单
        List<String> requiredParams = getRequiredParamsByIntent(action);
        
        // 2. 识别缺失的关键参数
        List<String> missingParams = new ArrayList<>();
        for (String param : requiredParams) {
            if (!params.containsKey(param) || params.get(param) == null || String.valueOf(params.get(param)).isEmpty()) {
                missingParams.add(param);
            }
        }
        
        // 3. 存储参数状态到上下文
        Map<String, Object> paramStatus = Map.of(
                "intentType", action,
                "requiredParams", requiredParams,
                "collectedParams", params,
                "missingParams", missingParams,
                "isComplete", missingParams.isEmpty()
        );
        
        // 将参数状态存储到queryParams中
        Map<String, Object> queryParams = context.getQueryParams();
        queryParams.put("paramStatus", paramStatus);
    }

    /**
     * 根据意图类型获取必需参数清单
     *
     * @param intentType 意图类型
     * @return 必需参数清单
     */
    private List<String> getRequiredParamsByIntent(String intentType) {
        List<String> requiredParams = new ArrayList<>();
        
        switch (intentType) {
//            case "LITERATURE_SEARCH":
//                requiredParams.add("topicName");
//                requiredParams.add("keywords");
//                requiredParams.add("startDate");
//                requiredParams.add("endDate");
//                break;
//            case "INSTRUCTIONS_SEARCH":
//                requiredParams.add("drugName");
//                break;
//            case "DATA_ANALYSIS":
//                requiredParams.add("topicName");
//                requiredParams.add("keywords");
//                requiredParams.add("researchType");
//                break;
//            case "REPORT_GENERATION":
//                requiredParams.add("topicName");
//                requiredParams.add("researchField");
//                requiredParams.add("keywords");
//                requiredParams.add("startDate");
//                requiredParams.add("endDate");
//                requiredParams.add("researchType");
//                break;
            default:
                requiredParams.add("topicName");
                requiredParams.add("keywords");
                break;
        }
        
        return requiredParams;
    }

    /**
     * 根据动作类型获取优先级
     */
    private int getPriorityForAction(String action) {
        switch (action) {
            case "REVISE":
                return 1; // 第一优先级
            case "QUESTION":
                return 2; // 第二优先级
            case "STATUS_QUERY":
                return 3; // 第三优先级
            case "CANCEL":
            case "CONFIRM":
                return 4; // 第四优先级
            case "EXPORT":
                return 5; // 第五优先级
            default:
                return 6; // 其他动作默认较低优先级
        }
    }

    /**
     * 构建参数摘要
     * 专注于项目申报书研究的参数
     */
    private String buildParamsSummary(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();

        // 项目申报书相关参数
        appendIfPresent(sb, "课题名称", params.get("topicName"));
        appendIfPresent(sb, "申报学科领域", params.get("disciplineField"));
        appendIfPresent(sb, "研究团队情况", params.get("teamInfo"));
        appendIfPresent(sb, "研究重点方向", params.get("researchFocus"));
        appendIfPresent(sb, "输出格式", params.get("outputLanguage"));

        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, Object value) {
        if (value != null && !String.valueOf(value).isEmpty()) {
            sb.append("- ").append(label).append("：").append(value).append("\n");
        }
    }
}