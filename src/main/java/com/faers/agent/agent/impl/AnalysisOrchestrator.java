// com/faers/agent/orchestration/AnalysisOrchestrator.java
package com.faers.agent.agent.impl;

import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.config.AnalysisState;
import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.pojo.StreamDataCallback;

//import com.faers.agent.tools.ParallelQueryTool;
import com.faers.agent.utils.ParamUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AnalysisOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AnalysisOrchestrator.class);

    @Autowired private PerceptionAgent perceptionAgent;
    @Autowired private TopicParamCollectionAgent topicParamCollectionAgent;
    @Autowired private ParamRevisionAgent paramRevisionAgent;
    @Autowired private ExploreResponseAgent exploreResponseAgent;
    @Autowired private MemoryAgent memoryAgent;
    @Autowired private ProgressDisplayAgent progressDisplayAgent;
//    @Autowired private DynamicProgressAgent progressDisplayAgent;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void process(AnalysisContext context, String id,
                        String parentId,
                        String userId,
                        StreamDataCallback callback) {
        String traceId = context.getTraceId();
        String dialogId = context.getSessionId(); // 因为每个 session 即一个对话
        String userMessage = context.getUserMessage();

        log.info("ORCHESTRATOR_START | traceId={} | dialogId={} | msg='{}'", traceId, dialogId, userMessage);

        try {
            // 设置userId到Session对象中
            if (context.getSession() != null) {
                context.getSession().setUserId(userId);
                context.setUserId(userId);
            }
            // 1. 意图识别
            perceptionAgent.process(context, id, parentId, userId, callback);
            // 获取意图识别结果
            PerceptionAgent.IntentResult intentResult = context.getIntentResult();
            List<PerceptionAgent.SingleIntentResult> allIntents = null;
            if (intentResult != null && intentResult.getIntentList() != null) {
                allIntents = intentResult.getIntentList();
            } else {
                // 如果没有识别到意图，默认使用EXPLORE动作
                PerceptionAgent.SingleIntentResult defaultIntent = new PerceptionAgent.SingleIntentResult(
                        PerceptionAgent.Action.EXPLORE.name(), 0.6, "默认探索动作", 4);
                allIntents = new ArrayList<>();
                allIntents.add(defaultIntent);
            }
            
            // 获取上下文信息
            AnalysisState currentState = getCurrentState(context);
            boolean paramsComplete = context.isCompleted();
            Map<String, Object> params = context.getQueryParams();
            
            // 检查是否需要生成报告
//            boolean reportGenerationMode = checkReportGenerationMode(params, allIntents);

            // 2. 应用动作互斥规则，过滤掉不允许执行的动作
            List<PerceptionAgent.SingleIntentResult> filteredIntents = applyMutexRules(allIntents, traceId);
            
            // 3. 流程调度：按优先级串行执行所有过滤后的动作
            log.info("ORCHESTRATOR_DISPATCH | traceId={} | 原始动作数={} | 过滤后动作数={}", 
                    traceId, allIntents.size(), filteredIntents.size());
            
            // 串行执行所有动作
            for (PerceptionAgent.SingleIntentResult intent : filteredIntents) {
                String action = intent.getAction();
                log.info("ORCHESTRATOR_EXECUTE_ACTION | traceId={} | action={} | priority={} | confidence={:.2f}", 
                        traceId, action, intent.getPriority(), intent.getConfidence());
                
                // 根据动作类型执行相应的处理逻辑
                boolean shouldContinue = executeAction(context, id, parentId, userId, callback, 
                        action, currentState, paramsComplete, params, dialogId);
                
                // 如果动作处理结果为false，停止后续动作执行
                if (!shouldContinue) {
                    break;
                }
                
                // 更新上下文状态
                currentState = getCurrentState(context);
                paramsComplete = context.isCompleted();
                params = context.getQueryParams();
            }

            // 4. 记忆管理（所有动作执行完成后执行）
            memoryAgent.process(context, id, parentId, userId, callback);

        } catch (Exception e) {
            log.error("ORCHESTRATOR_ERROR | traceId={} | error={}", traceId, e.getMessage());
            context.setResponseContent("系统内部错误：" + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 应用动作互斥规则，过滤掉不允许执行的动作
     */
    private List<PerceptionAgent.SingleIntentResult> applyMutexRules(
            List<PerceptionAgent.SingleIntentResult> allIntents, String traceId) {
        List<PerceptionAgent.SingleIntentResult> filteredIntents = new ArrayList<>();
        
        // 检查是否包含高优先级动作
        boolean hasRevise = false;
        boolean hasQuestion = false;
        boolean hasCancel = false;
        boolean hasConfirm = false;
        boolean hasExport = false;
        boolean hasStatusQuery = false;
        
        // 统计各种动作类型
        for (PerceptionAgent.SingleIntentResult intent : allIntents) {
            String action = intent.getAction().toUpperCase();
            if ("REVISE".equals(action)) hasRevise = true;
            if ("QUESTION".equals(action)) hasQuestion = true;
            if ("CANCEL".equals(action)) hasCancel = true;
            if ("CONFIRM".equals(action)) hasConfirm = true;
            if ("EXPORT".equals(action)) hasExport = true;
            if ("STATUS_QUERY".equals(action)) hasStatusQuery = true;
        }
        
        // 应用互斥规则
        for (PerceptionAgent.SingleIntentResult intent : allIntents) {
            String action = intent.getAction().toUpperCase();
            boolean allowExecute = true;
            
            // 规则1: 当意图中包含提问、修改、取消、确认、导出报告时，不能执行状态查询
            if ("STATUS_QUERY".equals(action)) {
                if (hasRevise || hasQuestion || hasCancel || hasConfirm || hasExport) {
                    allowExecute = false;
                    log.info("ORCHESTRATOR_MUTEX_FILTER | traceId={} | action=STATUS_QUERY | 被过滤（存在高优先级动作）", traceId);
                }
            }
            
            // 规则2: 当意图中包含提问、修改时，不能执行取消、确认
            if (("CANCEL".equals(action) || "CONFIRM".equals(action)) && (hasRevise || hasQuestion)) {
                allowExecute = false;
                log.info("ORCHESTRATOR_MUTEX_FILTER | traceId={} | action={} | 被过滤（存在提问/修改动作）", traceId, action);
            }
            
            // 规则3: 状态查询、取消、确认仅当意图分析后识别出的动作仅为这三类之一时才执行相应操作
            if (("STATUS_QUERY".equals(action) || "CANCEL".equals(action) || "CONFIRM".equals(action))) {
                int count = 0;
                if (hasStatusQuery) count++;
                if (hasCancel) count++;
                if (hasConfirm) count++;
                
                if (count != allIntents.size()) {
                    allowExecute = false;
                    log.info("ORCHESTRATOR_MUTEX_FILTER | traceId={} | action={} | 被过滤（存在其他类型动作）", traceId, action);
                }
            }
            
            if (allowExecute) {
                filteredIntents.add(intent);
            }
        }
        
        return filteredIntents;
    }
    
    /**
     * 检查是否需要生成报告
     */
    private boolean checkReportGenerationMode(Map<String, Object> params, 
                                            List<PerceptionAgent.SingleIntentResult> intents) {
        // 检查参数中是否明确要求生成报告
        if (params != null && Boolean.TRUE.equals(params.get("reportGenerationMode"))) {
            return true;
        }
        
        // 检查意图中是否包含EXPORT动作
        for (PerceptionAgent.SingleIntentResult intent : intents) {
            if ("EXPORT".equals(intent.getAction().toUpperCase())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 执行单个动作并返回是否应继续执行下一个动作
     */
    private boolean executeAction(AnalysisContext context, String id, String parentId, String userId,
                                 StreamDataCallback callback, String action, AnalysisState currentState,
                                 boolean paramsComplete, Map<String, Object> params, String dialogId) {
        String traceId = context.getTraceId();
        
        try {
            // 使用统一的参数完整性检查
            boolean allParamsComplete = com.faers.agent.utils.ParamUtils.isComplete(params);
            switch (action.toUpperCase()) {
                case "EXPLORE":
                    topicParamCollectionAgent.process(context, id, parentId, userId, callback);
                    //重新获取参数并检查参数完整性
                    //获取参数
                    params = context.getQueryParams();
                    //检查是否有课题名称参数
                    boolean hasTopicName = params != null && params.containsKey("topicName") &&
                            params.get("topicName") != null &&
                            !String.valueOf(params.get("topicName")).trim().isEmpty();
                    // 优先使用topicParamCollectionAgent设置的completed状态
                    allParamsComplete = context.isCompleted() || ParamUtils.isComplete(params);
                    if(hasTopicName){
                        try {
                            // 使用并行查询工具执行ES查询和BochaWebSearch
//                            parallelQueryTool.executeParallelQuery(context,callback);
                            if(allParamsComplete){
                                progressDisplayAgent.process(context, id, userId, parentId, callback);
//                                reportGenerationAgent.process(context, id, parentId, userId, callback);
                                log.info("ORCHESTRATOR_EXPLORE_COMPLETE | traceId={} | 执行完整分析流程（参数收集->并行查询->进度显示->报告生成）", traceId);
                            }else {
//                                exploreResponseAgent.process(context, id, parentId, userId, callback);

                                log.info("ORCHESTRATOR_EXPLORE_COMPLETE1 | traceId={} | 执行完整流程（参数收集->并行查询->探索）", traceId);
                            }
                        } catch (Exception e) {
                            log.error("ORCHESTRATOR_EXPLORE_PARALLEL_ERROR | traceId={} | 并行查询失败", traceId, e);
                            context.setResponseContent("并行查询失败：" + e.getMessage());
                        }
                    }else {
                        //如果没有课题则提示用户补充
                        context.setResponseContent("请提供课题名称以便进行分析，如：基于自述数据智能认知的中医指南中患者价值观获取和报告方法研究");
                        log.info("ORCHESTRATOR_EXPLORE_NO_TOPIC | traceId={} | 请补充课题名称", traceId);
                    }
                    break;
                case "REVISE":
                    log.info("ORCHESTRATOR_REVISE | traceId={} | 执行参数修改", traceId);
                    paramRevisionAgent.process(context, id, parentId, userId, callback);
                    // 更新后重新分析参数完整性
                    boolean revisedParamsComplete = com.faers.agent.utils.ParamUtils.isComplete(context.getQueryParams());
                    if (revisedParamsComplete) {
                        log.info("ORCHESTRATOR_REVISE_COMPLETE | traceId={} | 参数修改后完整，执行ES查询并导出报告", traceId);
//                        topicESQueryAgent.process(context, id, parentId, userId, callback);
                        // 使用并行查询工具执行ES查询和BochaWebSearch
//                        parallelQueryTool.executeParallelQuery(context,callback);
                        progressDisplayAgent.process(context, id, userId, parentId, callback);
//                        reportGenerationAgent.process(context, id, parentId, userId, callback);
                    } else {
                        log.info("ORCHESTRATOR_REVISE_INCOMPLETE | traceId={} | 参数修改后不完整，继续参数收集", traceId);
                        topicParamCollectionAgent.process(context, id, parentId, userId, callback);
                    }
                    break;

                case "QUESTION":
                    log.info("ORCHESTRATOR_QUESTION | traceId={} | 执行提问处理", traceId);
//                    exploreResponseAgent.process(context, id, parentId, userId, callback);
                    topicParamCollectionAgent.process(context, id, parentId, userId, callback);
                    boolean complete = ParamUtils.isComplete(context.getQueryParams());
                    if(complete) {
                        progressDisplayAgent.process(context, id, userId, parentId, callback);
//                        reportGenerationAgent.process(context, id, parentId, userId, callback);
                    }
                    break;

                case "CONFIRM":
                    log.info("ORCHESTRATOR_CONFIRM | traceId={} | 执行确认操作", traceId);
                    String topicName = getTopicNameFromContext(context);
                    StringBuilder sb = new StringBuilder();
                    sb.append("好的，我来为您生成关于" + topicName + "的项目申报书");
                    Response sb1 = Response.ResponseContentChatBuilder(sb.toString(), "agent");
                    callback.onData(sb1);
                    progressDisplayAgent.process(context, id, userId, parentId, callback);
//                    reportGenerationAgent.process(context, id, parentId, userId, callback);
                    break;

                case "EXPORT":
                    log.info("ORCHESTRATOR_EXPORT | traceId={} | 执行导出操作", traceId);
                    if (context.getFinalPaper() != null && !context.getFinalPaper().isEmpty()) {
                        sendExportReady(dialogId, id, parentId, userId);
                    } else if (context.getCleanJson() != null) {
                        progressDisplayAgent.process(context, id, userId, parentId, callback);
//                        reportGenerationAgent.process(context, id, parentId, userId, callback);
                    } else {
                        context.setResponseContent("⚠️ 当前无可导出内容，请先完成分析。");
                    }
                    break;

                case "CANCEL":
                    log.info("ORCHESTRATOR_CANCEL | traceId={} | 执行取消操作", traceId);
                    context.setResponseContent("已取消当前操作。");
                    context.getSession().setAnalysisState(AnalysisState.CANCELLED);
                    // 取消操作后不再执行后续动作
                    return false;

                case "STATUS_QUERY":
                    log.info("ORCHESTRATOR_STATUS_QUERY | traceId={} | 执行状态查询", traceId);
                    exploreResponseAgent.process(context, id, parentId, userId, callback);
                    break;

                default:
//                    log.warn("ORCHESTRATOR_UNKNOWN_ACTION | traceId={} | action={} | 使用默认EXPLORE流程", traceId, action);
                    // 未知动作，默认使用EXPLORE流程
//                        topicESQueryAgent.process(context, id, parentId, userId, callback);
//                        exploreResponseAgent.process(context, id, parentId, userId, callback);
                    break;
            }
            return true;
        } catch (Exception e) {
            log.error("ORCHESTRATOR_ACTION_ERROR | traceId={} | action={} | error={}", traceId, action, e.getMessage());
            // 单个动作执行失败不影响其他动作执行
            return true;
        }
    }

    private AnalysisState getCurrentState(AnalysisContext context) {
        return context.getSession() != null && context.getSession().getAnalysisState() != null ?
                context.getSession().getAnalysisState() :
                AnalysisState.IDLE;
    }

    private void sendExportReady(String dialogId, String id,
                                 String parentId,
                                 String userId) {
        try {
            String msg = objectMapper.writeValueAsString(Map.of(
                "type", "export_ready",
                "message", "📄 报告已生成，可点击【导出】按钮下载 PDF/Word"
            ));
            // webSocketClient.sendMessage(dialogId, msg, id, parentId, userId);
        } catch (Exception e) {
            log.warn("Failed to send export_ready", e);
        }
    }

    /**
     * 从上下文中获取课题名称
     * @param context 分析上下文
     * @return 课题名称
     */
    private String getTopicNameFromContext(AnalysisContext context) {
        // 优先从queryParams中获取
        if (context.getQueryParams() != null && context.getQueryParams().containsKey("topicName")) {
            return context.getQueryParams().get("topicName").toString();
        }
        // 其次从userMessage中提取
        return context.getUserMessage();
    }


}
