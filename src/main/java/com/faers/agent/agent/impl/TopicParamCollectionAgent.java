// com/faers/agent/agent/impl/TopicParamCollectionAgent.java
package com.faers.agent.agent.impl;

import com.faers.agent.agent.Agent;
import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.config.AnalysisState;
import com.faers.agent.config.TopicParamDefinition;
import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.dto.responseDto.ResponseContent;
import com.faers.agent.exception.AgentException;
import com.faers.agent.model.Session;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.service.LLMInvocationService;
import com.faers.agent.service.MongodbSessionStorageService;
import com.faers.agent.utils.Model;
import com.faers.agent.pojo.ProjectApplicationParams;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用课题参数收集智能体（Topic Param Collection Agent）
 * 职责：处理用户输入课题后的参数收集和引导
 * 特性：
 * - 支持通用课题的参数收集
 * - 自动引导用户补充缺失的重要参数
 * - 基于TopicParamDefinition定义的参数进行智能提取
 * - 支持参数完整性检查和标准化
 * - 新增：支持预算规模、计划申报类别等项目申报特有参数
 */
@Component
public class TopicParamCollectionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(TopicParamCollectionAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MongodbSessionStorageService sessionStorageService;

    @Autowired
    private LLMInvocationService llmInvocationService;

    private ChatClient chatClient;
    public TopicParamCollectionAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    // 用于预算金额正则匹配的Pattern
    private static final Pattern BUDGET_PATTERN = Pattern.compile(
            "(?:预算|经费|资金|金额)[：:]?\\s*([\\d.]+)(?:万元|元|万)?");

    // 用于日期正则匹配的Pattern
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(?<year>\\d{4})[年/-]*(?<month>0?[1-9]|1[0-2])[月/-]*(?<day>0?[1-9]|[12]\\d|3[01])?日?");

    @Override
    public void process(AnalysisContext context, String id,
                        String parentId,
                        String userId,
                        StreamDataCallback streamDataCallback) {
        String traceId = context.getTraceId();
        log.info("TOPIC_PARAM_COLLECTION_START | traceId={}", traceId);

        try {
            Map<String, Object> params = context.getQueryParams();
            String userMessage = context.getUserMessage();
            Session session = context.getSession();

            // 参数管理逻辑：当前对话保存参数，新建对话重置参数
            if (session != null) {
                // 检查是否为新会话
                if (context.isNewSession()) {
                    log.info("NEW_SESSION_DETECTED | traceId={} | sessionId={} | 重置所有参数，开始新会话",
                            traceId, session.getSessionId());
                    // 重置Session中的所有参数
                    session.setTopicName(null);
                    session.setDisciplineField(null);
                    session.setTeamInfo(null);
                    session.setResearchFocus(null);
                    session.setOutputLanguage(null);
                    // 重置会话状态为IDLE，避免直接进入报告生成流程
                    session.setAnalysisState(AnalysisState.IDLE);
                    // 清空queryParams，确保新会话参数干净
                    params.clear();
                } else {
                    // 当前对话中保存参数，不进行跨会话复用
                    // 仅当params中没有对应参数时，从session中恢复（当前对话内的参数保持）
                    if (session.getTopicName() != null && !params.containsKey("topicName")) {
                        params.put("topicName", session.getTopicName());
                        log.info("PARAM_RESTORED | traceId={} | paramName=topicName | value={}", traceId, session.getTopicName());
                    }
                    if (session.getDisciplineField() != null && !params.containsKey("disciplineField")) {
                        params.put("disciplineField", session.getDisciplineField());
                        log.info("PARAM_RESTORED | traceId={} | paramName=disciplineField | value={}", traceId, session.getDisciplineField());
                    }
                    if (session.getTeamInfo() != null && !params.containsKey("teamInfo")) {
                        params.put("teamInfo", session.getTeamInfo());
                        log.info("PARAM_RESTORED | traceId={} | paramName=teamInfo | value={}", traceId, session.getTeamInfo());
                    }
                    if (session.getResearchFocus() != null && !params.containsKey("researchFocus")) {
                        params.put("researchFocus", session.getResearchFocus());
                        log.info("PARAM_RESTORED | traceId={} | paramName=researchFocus | value={}", traceId, session.getResearchFocus());
                    }
                    if (session.getOutputLanguage() != null && !params.containsKey("outputLanguage")) {
                        params.put("outputLanguage", session.getOutputLanguage());
                        log.info("PARAM_RESTORED | traceId={} | paramName=outputLanguage | value={}", traceId, session.getOutputLanguage());
                    }
                }
            }

            // 对话状态管理：检查是否属于特殊路径
            if (handleSpecialPaths(context, userMessage, params, streamDataCallback)) {
                return;
            }

            // 首先尝试解析结构化输入（字段：值格式）
            Map<String, Object> structuredParams = parseStructuredInput(userMessage, context);
            if (!structuredParams.isEmpty()) {
                log.info("TOPIC_PARAM_STRUCTURED_INPUT | traceId={} | extractedParams={}", traceId, structuredParams);
                // 将结构化解析的结果合并到参数中
                params.putAll(structuredParams);
                // 应用日期标准化
                applyDateNormalization(params);
                // 更新参数
                updateParams(params, structuredParams);
            }

            // 检查结构化解析后是否有课题名称
            boolean isFirstRound = !params.containsKey("topicName");
            boolean hasNoTopicNameAfterStructured = !structuredParams.containsKey("topicName") || structuredParams.get("topicName") == null;

            // 如果是第一轮对话且结构化解析后仍然没有课题名称，直接提醒用户输入
            if (isFirstRound && hasNoTopicNameAfterStructured) {
                // 不需要调用LLM，直接提醒用户
                String reply = "请提供课题名称以便进行分析，如：基于自述数据智能认知的中医指南中患者价值观获取和报告方法研究";
                streamResponse(reply, streamDataCallback, false); // 参数不完整
                log.info("TOPIC_NAME_REQUIRED_AFTER_STRUCTURED | traceId={} | response={}", traceId, reply);
                return;
            }

            String currentSummary = buildSummary(params);
            List<String> missingSuggestions = getMissingAskableSuggestions(params);

            // 先调用printParamCollectionStatus来判断收集了哪些参数
            com.faers.agent.utils.ParamUtils.printParamCollectionStatus(params);
            
            // ===== 第一步：调用大模型提取参数（仅提取参数，不生成回复消息） =====
            String paramExtractionPrompt = "你是科研项目参数收集助手，请根据用户输入分析并提取课题相关参数。" +
                    "\n\n【输出要求】" +
                    "\n请严格按照以下JSON格式返回结果，不要包含任何其他解释或说明，只返回纯JSON文本：" +
                    "\n{" +
                    "\n  \"extractedParams\": {" +
                    "\n    \"topicName\": \"课题的正式名称\"," +
                    "\n    \"disciplineField\": \"申报学科领域\"," +
                    "\n    \"teamInfo\": {" +
                    "\n      \"priorResearch\": \"前期研究成果\"," +
                    "\n      \"clinicalData\": \"临床数据资源或合作医院\"," +
                    "\n      \"teamBackground\": \"团队成员专业背景\"" +
                    "\n    }," +
                    "\n    \"researchFocus\": \"研究重点方向\"" +
                    "\n  }" +
                    "\n}" +
                    "\n\n【参数定义】" +
                    "\n- topicName（课题名称）：课题的正式名称，必填参数" +
                    "\n- disciplineField（申报学科领域）：学科代码，例如H2701医学信息学、C0708生物信息学、H3515临床药理学等" +
                    "\n- teamInfo（研究团队情况）：复合对象，包含以下信息：" +
                    "\n  * priorResearch：前期研究成果（如已发表论文、专利、前期研究数据等）" +
                    "\n  * clinicalData：临床数据资源或合作医院" +
                    "\n  * teamBackground：团队成员专业背景（AI、药学、临床医学等）" +
                    "\n- researchFocus（研究重点方向）：希望侧重的研究方向" +
                    "\n- outputLanguage（输出格式）：报告内容的输出语言，可选\"中文\"或\"英文\"，仅当用户明确指定时才返回此参数，否则不包含此字段" +
                    "\n\n【识别规则】" +
                    "\n1. 用户输入可能包含\"字段：值\"的结构化信息，优先识别这些明确标注的参数" +
                    "\n2. 从自然语言中智能推断参数值" +
                    "\n3. teamInfo需要提取三个子字段，如果用户只提供了部分信息，也要保存" +
                    "\n4. disciplineField应识别学科代码，如\"H2701\"、\"C0708\"等" +
                    "\n5. outputLanguage应识别用户的语言偏好：" +
                    "\na) 用户明确说\"中文\"或\"中文报告\" → 设为\"中文\"" +
                    "\nb) 用户明确说\"英文\"、\"English\"或\"English report\" → 设为\"英文\"" +
                    "\nc) 用户未明确指定时，不返回此参数" +
                    "\n6. 仅提取用户明确提供或能明确推断的参数，未提供的参数不包含在extractedParams中" +
                    "\n\n当前已确认参数：" +
                    "\n%s" +
                    "\n\n用户最新输入：" +
                    "\n\"%s\"" +
                    "\n\n上下文记忆：" +
                    "\n%s";

            // 使用String.format代替.formatted方法，确保参数匹配
            paramExtractionPrompt = String.format(paramExtractionPrompt,
                    currentSummary.isEmpty() ? "暂无" : currentSummary,
                    context.getUserMessage(),
                    context.getMemorySummaries().isEmpty() ? "无" : String.join("\n", context.getMemorySummaries()));

            // 调用LLM提取参数（非流式调用，只获取参数）
            String modelResponse;
            try {
                modelResponse = Model.getModel("使用json返回", paramExtractionPrompt, "qwen-plus");
            } catch (Exception e) {
                log.error("LLM调用失败 | traceId={} | error={}", traceId, e.getMessage(), e);
                String errorMessage = e.getMessage();
                if (errorMessage != null && isFriendlyErrorMessage(errorMessage)) {
                    Response errorResponse = Response.ResponseContentChatBuilder(errorMessage, "agent");
                    streamDataCallback.onData(errorResponse);
                } else {
                    Response errorResponse = Response.ResponseContentChatBuilder("网络连接异常，请稍后重试。", "agent");
                    streamDataCallback.onData(errorResponse);
                }
                return;
            }
            
            log.info("LLM_RAW_RESPONSE | traceId={} | responseLength={}", traceId, modelResponse != null ? modelResponse.length() : 0);

            if (modelResponse == null || modelResponse.trim().isEmpty()) {
                log.warn("LLM返回为空，使用默认回复 | traceId={}", traceId);
                Response errorResponse = Response.ResponseContentChatBuilder("抱歉，我暂时无法理解您的输入，请重新描述您的需求。", "agent");
                streamDataCallback.onData(errorResponse);
                return;
            }

            // 检查是否是错误消息（非JSON格式）
            String trimmedResponse = modelResponse.trim();
            if (isFriendlyErrorMessage(trimmedResponse) || !isLikelyJson(trimmedResponse)) {
                log.warn("LLM返回错误消息而非JSON | traceId={} | response={}", traceId, trimmedResponse);
                Response errorResponse = Response.ResponseContentChatBuilder(trimmedResponse, "agent");
                streamDataCallback.onData(errorResponse);
                return;
            }

            // 清理JSON响应
            String cleanedResponse = cleanJsonResponse(modelResponse);
            log.debug("LLM_CLEANED_RESPONSE | traceId={} | cleanedLength={}", traceId, cleanedResponse.length());

            JsonNode root;
            try {
                root = objectMapper.readTree(cleanedResponse); // 使用清理后的响应
            } catch (Exception e) {
                log.error("LLM响应JSON解析失败 | traceId={} | originalResponse={} | cleanedResponse={}",
                        traceId, modelResponse, cleanedResponse, e);
                // 如果解析失败，检查是否是错误消息
                if (isFriendlyErrorMessage(cleanedResponse)) {
                    Response errorResponse = Response.ResponseContentChatBuilder(cleanedResponse, "agent");
                    streamDataCallback.onData(errorResponse);
                } else {
                    Response errorResponse = Response.ResponseContentChatBuilder("系统处理出现异常，请稍后重试。", "agent");
                    streamDataCallback.onData(errorResponse);
                }
                return;
            }

            Map<String, Object> extracted = jsonNodeToFlatMap(root.path("extractedParams"));

            applyDateNormalization(extracted);

            // 更新参数
            updateParams(params, extracted);

            // 打印参数更新后的状态
            com.faers.agent.utils.ParamUtils.printParamCollectionStatus(params);

            // 将最新参数快照映射为 ProjectApplicationParams，挂载到 queryParams 中，便于后续使用
            ProjectApplicationParams projectParams = ProjectApplicationParams.fromMap(params);
            params.put("projectApplicationParams", projectParams);

            // ===== 同步核心参数到Session对象，并持久化到MongoDB =====
            if (session != null) {
                session.setTopicName(projectParams.getTopicName());
                session.setDisciplineField(projectParams.getDisciplineField());
                session.setTeamInfo(projectParams.getTeamInfo());
                session.setResearchFocus(projectParams.getResearchFocus());
                // 更新outputLanguage参数，如果没有则不设置，等待后续流程处理
            if (params.containsKey("outputLanguage")) {
                session.setOutputLanguage(String.valueOf(params.get("outputLanguage")));
            }
                // 更新最后访问时间
                session.setLastAccessed(LocalDateTime.now());
                // 保存到MongoDB（通过会话存储服务）
                sessionStorageService.saveSession(session);
            }




            // 打印参数收集状态
            System.out.println("【参数更新完成】");
            com.faers.agent.utils.ParamUtils.printParamCollectionStatus(params);

            // ===== 第二步：根据参数收集状态，使用固定模板生成回复（不调用大模型） =====
            
            // 检查是否未识别出课题名称
            boolean hasNoTopicName = !extracted.containsKey("topicName") || extracted.get("topicName") == null;

            // 判断参数是否完整
            boolean paramsComplete = com.faers.agent.utils.ParamUtils.isComplete(params);
            
            String reply;
            
            // 第一轮对话：如果未识别出课题名称，强制提醒用户输入
            if (isFirstRound && hasNoTopicName) {
                reply = "请提供课题名称以便进行分析，如：基于自述数据智能认知的中医指南中患者价值观获取和报告方法研究";
                log.info("TOPIC_NAME_REQUIRED | traceId={} | response={}", traceId, reply);
            } 
            // 参数收集完整：使用完整参数的固定模板
            else if (paramsComplete) {
                reply = buildCompletedParamsTemplate(params);
                log.info("PARAMS_COMPLETED_TEMPLATE | traceId={} | response={}", traceId, reply);
            } 
            // 参数未收集完整：使用引导用户补充参数的固定模板
            else {
                reply = buildIncompleteParamsTemplate(params);
                log.info("PARAMS_INCOMPLETE_TEMPLATE | traceId={} | response={}", traceId, reply);
            }

            // 通过流式方式返回给前端
            StringBuilder accumulatedContent = new StringBuilder();
            char[] chars = reply.toCharArray();
            for (int i = 0; i < chars.length; i++) {
                // 将当前字符添加到累积字符串中
                accumulatedContent.append(chars[i]);
                // 通过Response.ResponseContentChatBuilder流式返回累积的字符串，使用delta字段
                Response charResponse = Response.ResponseContentChatBuilder(accumulatedContent.toString(), "agent");
                
                // 判断是否为最后一个字符，且参数未收集完整时，设置isFinished为true
                // 参数完整时不设置isFinished，因为会自动进入报告生成流程
                if (i == chars.length - 1 && !paramsComplete) {
                    ((ResponseContent)charResponse.getData()).setIsFinished("true");
                    log.info("STREAM_RESPONSE_FINISHED | traceId={} | 参数未完整，设置isFinished=true", traceId);
                }
                
                streamDataCallback.onData(charResponse);
                // 添加20毫秒延迟，模拟真实的流式效果
                try {
                    Thread.sleep(10); // 20毫秒延迟，根据要求设置
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            context.setResponseContent(reply);
            log.info("TOPIC_PARAM_COLLECTION_SET_RESPONSE | traceId={} | response={}", traceId, reply);

            // 判断是否完成
            boolean isCompleted = root.has("isCompleted") ? root.get("isCompleted").asBoolean() : false;
            paramsComplete = com.faers.agent.utils.ParamUtils.isComplete(params);

            // 打印参数收集状态
            System.out.println("【参数更新完成】");
            com.faers.agent.utils.ParamUtils.printParamCollectionStatus(params);

            log.info("TOPIC_PARAM_COLLECTION_CHECK | traceId={} | paramsComplete={}", traceId, paramsComplete);

            // 参数收集完成后自动进入报告生成流程
            if (paramsComplete) {
                context.setCompleted(true);
                // 更新会话状态为PARAMS_READY，这样AnalysisOrchestrator会自动执行完整流程
                if (context.getSession() != null) {
                    context.getSession().setAnalysisState(AnalysisState.PARAMS_READY);
                }
                log.info("TOPIC_PARAM_COLLECTION_COMPLETED | traceId={} | 参数已齐全，状态已设置为PARAMS_READY，将自动执行分析流程", traceId);
            } else {
                log.info("TOPIC_PARAM_COLLECTION_INCOMPLETE | traceId={} | 参数未齐全，继续收集", traceId);
            }

        } catch (Exception e) {
            log.error("TOPIC_PARAM_COLLECTION_ERROR | traceId={}", traceId, e);
            throw new AgentException("课题参数收集失败", e);
        }
    }

    /**
     * 处理特殊对话路径
     *
     * @param context      分析上下文
     * @param userMessage  用户最新消息
     * @param params       当前参数集合
     * @param streamDataCallback 流式数据回调
     * @return 是否处理了特殊路径
     */
    private boolean handleSpecialPaths(AnalysisContext context, String userMessage, Map<String, Object> params, StreamDataCallback streamDataCallback) {
        String traceId = context.getTraceId();

        // 1. 检查确认路径
        if (isConfirmationMessage(userMessage)) {
            handleConfirmationPath(context, params, streamDataCallback);
            return true;
        }

        // 2. 检查暂停路径
        if (isPauseMessage(userMessage)) {
            handlePausePath(context, streamDataCallback);
            return true;
        }

        // 3. 检查修改路径
        if (isModifyMessage(userMessage)) {
            handleModifyPath(context, userMessage, params, streamDataCallback);
            return true;
        }

        // 4. 检查追问路径（默认处理，无需特殊返回）

        return false;
    }

    /**
     * 判断是否为确认消息
     *
     * @param message 用户消息
     * @return 是否为确认消息
     */
    private boolean isConfirmationMessage(String message) {
        return message.matches(".*(开始吧|可以|确认|没问题|继续|执行).*");
    }

    /**
     * 处理确认路径
     *
     * @param context 分析上下文
     * @param params  当前参数集合
     * @param streamDataCallback 流式数据回调
     */
    private void handleConfirmationPath(AnalysisContext context, Map<String, Object> params, StreamDataCallback streamDataCallback) {
        String traceId = context.getTraceId();
        log.info("TOPIC_PARAM_CONFIRM_PATH | traceId={}", traceId);

        // 检查参数完整性，只有所有参数完整才进入执行阶段
        boolean isComplete = com.faers.agent.utils.ParamUtils.isComplete(params);

        if (isComplete) {
            // 参数完整，进入查询执行阶段
            context.setCompleted(true);
            if (context.getSession() != null) {
                context.getSession().setAnalysisState(AnalysisState.PARAMS_READY);
            }

            String summary = buildSummary(params);
            String reply = "已确认开始执行分析任务。\n当前已收集参数：\n" +
                    (summary.isEmpty() ? "暂无" : summary);
            streamResponse(reply, streamDataCallback, true); // 参数完整
            context.setResponseContent(reply);
        } else {
            // 参数不完整，继续收集
            context.setCompleted(false);
            if (context.getSession() != null) {
                context.getSession().setAnalysisState(AnalysisState.COLLECTING_PARAMS);
            }

            // 打印参数收集状态
            com.faers.agent.utils.ParamUtils.printParamCollectionStatus(params);

            String summary = buildSummary(params);
            String reply = "参数收集尚未完整，无法开始分析。\n当前已收集参数：\n" +
                    (summary.isEmpty() ? "暂无" : summary) + "\n\n" +
                    getMissingAskableSuggestions(params);
            streamResponse(reply, streamDataCallback, false); // 参数不完整
            context.setResponseContent(reply);
        }
    }

    /**
     * 判断是否为暂停消息
     *
     * @param message 用户消息
     * @return 是否为暂停消息
     */
    private boolean isPauseMessage(String message) {
        return message.matches(".*(暂停|稍后|等一下|先停|保存).*");
    }

    /**
     * 处理暂停路径
     *
     * @param context 分析上下文
     * @param streamDataCallback 流式数据回调
     */
    private void handlePausePath(AnalysisContext context, StreamDataCallback streamDataCallback) {
        String traceId = context.getTraceId();
        log.info("TOPIC_PARAM_PAUSE_PATH | traceId={}", traceId);

        // 保存当前会话状态
        if (context.getSession() != null) {
            // 将状态设置为IDLE，表示暂停
            context.getSession().setAnalysisState(AnalysisState.IDLE);
            // 更新最后访问时间
            context.getSession().setLastAccessed(LocalDateTime.now());
            // 保存会话信息
            sessionStorageService.saveSession(context.getSession());
        }

        String reply = "已暂停当前任务，您可以随时返回继续。";
        streamResponse(reply, streamDataCallback, false); // 暂停时参数未完整
        context.setResponseContent(reply);
    }

    /**
     * 判断是否为修改消息
     *
     * @param message 用户消息
     * @return 是否为修改消息
     */
    private boolean isModifyMessage(String message) {
        return message.matches(".*(修改|更改|调整|重新|不是|不对).*");
    }

    /**
     * 处理修改路径
     *
     * @param context     分析上下文
     * @param userMessage 用户消息
     * @param params      当前参数集合
     * @param streamDataCallback 流式数据回调
     */
    private void handleModifyPath(AnalysisContext context, String userMessage, Map<String, Object> params, StreamDataCallback streamDataCallback) {
        String traceId = context.getTraceId();
        log.info("TOPIC_PARAM_MODIFY_PATH | traceId={}", traceId);

        // 使用LLM提取需要修改的参数和新值
        String currentSummary = buildSummary(params);
        String systemPrompt = """
            你是科研文献分析助手，请帮助用户修改已收集的参数。
            
            当前已确认参数：
            %s
            
            用户修改请求：
            "%s"
            
            请从用户修改请求中提取需要修改的参数及其新值，并以JSON格式返回：
            {
              "modifiedParams": { ... },
              "responseMessage": "自然语言回复"
            }
        """
                .formatted(
                        currentSummary.isEmpty() ? "暂无" : currentSummary,
                        userMessage
                );

        String modelResponse = Model.getModel("使用json返回", systemPrompt, "qwen-plus");
        log.debug("LLM_MODIFY_RESPONSE | traceId={} | response={}", traceId, modelResponse);

        try {
            JsonNode root = objectMapper.readTree(modelResponse);
            Map<String, Object> modified = jsonNodeToFlatMap(root.path("modifiedParams"));

            // 应用修改
            for (Map.Entry<String, Object> entry : modified.entrySet()) {
                params.put(entry.getKey(), entry.getValue());
            }

            // 更新会话状态
            Session session = context.getSession();
            if (session != null) {
                session.setAnalysisState(AnalysisState.COLLECTING_PARAMS);

                // 将最新参数快照映射为 ProjectApplicationParams
                ProjectApplicationParams projectParams = ProjectApplicationParams.fromMap(params);
                params.put("projectApplicationParams", projectParams);

                // 同步核心参数到Session对象
                session.setTopicName(projectParams.getTopicName());
                session.setDisciplineField(projectParams.getDisciplineField());
                session.setTeamInfo(projectParams.getTeamInfo());
                session.setResearchFocus(projectParams.getResearchFocus());
                session.setOutputLanguage(projectParams.getOutputLanguage());

                // 更新最后访问时间
                session.setLastAccessed(LocalDateTime.now());

                // 保存到MongoDB（通过会话存储服务）
                sessionStorageService.saveSession(session);
                log.info("SESSION_UPDATED_AFTER_MODIFY | traceId={}", traceId);
            }

            // 设置回复消息
            String reply = root.has("responseMessage") ? root.get("responseMessage").asText().trim() : "已更新参数。";
            // 修改后检查参数完整性
            boolean modifiedParamsComplete = com.faers.agent.utils.ParamUtils.isComplete(params);
            streamResponse(reply, streamDataCallback, modifiedParamsComplete);
            context.setResponseContent(reply);

        } catch (Exception e) {
            log.error("LLM_MODIFY_PARSE_ERROR | traceId={}", traceId, e);
            String errorMessage = "参数修改失败，请重新表述您的修改请求。";
            streamResponse(errorMessage, streamDataCallback, false); // 修改失败，参数不完整
            context.setResponseContent(errorMessage);
        }
    }

    // 构建参数摘要
    private String buildSummary(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        for (TopicParamDefinition def : TopicParamDefinition.ALL) {
            Object value = params.get(def.getParamName());
            if (value == null) continue;

            String label = getLabel(def.getParamName());
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) value;
                if (!list.isEmpty()) {
                    sb.append("- ").append(label).append("：").append(String.join(", ", list)).append("\n");
                }
            } else if (value instanceof String && !((String) value).isEmpty()) {
                sb.append("- ").append(label).append("：").append(value).append("\n");
            } else if (value instanceof Number) {
                sb.append("- ").append(label).append("：").append(value).append("\n");
            } else if (value instanceof Boolean) {
                sb.append("- ").append(label).append("：").append((Boolean) value ? "是" : "否").append("\n");
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                if (!map.isEmpty()) {
                    sb.append("- ").append(label).append("：" + map.toString()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    // 获取缺失的可询问参数建议
    private List<String> getMissingAskableSuggestions(Map<String, Object> params) {
        List<String> suggestions = new ArrayList<>();
        for (TopicParamDefinition def : TopicParamDefinition.ALL) {
            if (!params.containsKey(def.getParamName()) || params.get(def.getParamName()) == null) {
                String suggestion = generateSuggestionByStrategy(def);
                suggestions.add(suggestion);
            }
        }
        return suggestions;
    }

    // 根据引导策略生成建议
    private String generateSuggestionByStrategy(TopicParamDefinition def) {
        // 如果有选项，使用选项建议
        if (def.getOptions() != null && !def.getOptions().isEmpty()) {
            return generateOptionSuggestion(def);
        }
        // 如果有澄清确认文本，使用澄清确认
        else if (def.getClarificationAskText() != null && !def.getClarificationAskText().isEmpty()) {
            return generateClarificationSuggestion(def);
        }
        // 默认使用直接询问
        else {
            return def.getDirectAskText();
        }
    }

    // 生成选项建议
    private String generateOptionSuggestion(TopicParamDefinition def) {
        StringBuilder sb = new StringBuilder(def.getOptionsAskText() != null && !def.getOptionsAskText().isEmpty() ? def.getOptionsAskText() : def.getDirectAskText());
        sb.append("：");

        List<String> options = def.getOptions();
        for (int i = 0; i < options.size(); i++) {
            sb.append("\n");
            sb.append((char) ('A' + i)).append(". ").append(options.get(i));
        }

        return sb.toString();
    }

    // 生成澄清确认建议
    private String generateClarificationSuggestion(TopicParamDefinition def) {
        // 对于需要澄清的参数，我们可以提供一些常见的理解选项
        // 这里可以根据参数类型和上下文进行扩展
        if ("timeRange".equals(def.getParamName())) {
            return def.getClarificationAskText() + "\n例如：最近一年/最近三年/2020-2023年";
        } else if (def.getParamType() == TopicParamDefinition.ParamType.STRING) {
            return def.getClarificationAskText() + "\n例如：您提到的'近期'是指最近一年内，还是最近三年内？";
        }
        return def.getClarificationAskText();
    }

    /**
     * 从课题名称中提取关键词
     * 简单分词：按常见分隔符和关键词提取
     */
    private List<String> extractKeywordsFromTopic(String topicName) {
        List<String> keywords = new ArrayList<>();
        if (topicName == null || topicName.trim().isEmpty()) {
            return keywords;
        }

        // 移除常见停用词和标点
        String cleaned = topicName.replaceAll("[的|在|与|及|和|、|，|。]", " ");

        // 按空格和常见分隔符分割
        String[] parts = cleaned.split("[\\s、，。]+");

        for (String part : parts) {
            part = part.trim();
            // 提取长度>=2的词，且不是纯数字
            if (part.length() >= 2 && !part.matches("^\\d+$")) {
                keywords.add(part);
            }
        }

        // 限制关键词数量（最多10个）
        if (keywords.size() > 10) {
            keywords = keywords.subList(0, 10);
        }

        return keywords;
    }

    /**
     * 检查参数完整性
     *
     * @param params 参数集合
     * @return 是否完整
     */
    private boolean isParamsComplete(Map<String, Object> params) {
        // 使用统一的参数完整性检查
        return com.faers.agent.utils.ParamUtils.isComplete(params);
    }

    // 更新参数
    private void updateParams(Map<String, Object> current, Map<String, Object> updates) {
        for (TopicParamDefinition def : TopicParamDefinition.ALL) {
            if (updates.containsKey(def.getParamName())) {
                Object value = updates.get(def.getParamName());
                if (value != null) {
                    // 如果有选项，可能需要将字母选项转换为实际值
                    if (def.getOptions() != null && !def.getOptions().isEmpty()) {
                        value = convertOptionValue(def, value);
                    }
                    current.put(def.getParamName(), value);
                    // 打印已成功收集的参数名和值
//                    log.info("参数收集成功 | 参数名={} | 值={}", def.getParamName(), value);
                }
            }
        }
    }

    // 将选项字母转换为实际值
    private Object convertOptionValue(TopicParamDefinition def, Object value) {
        if (value instanceof String) {
            String input = ((String) value).trim().toUpperCase();
            if (input.length() == 1 && Character.isLetter(input.charAt(0))) {
                int index = input.charAt(0) - 'A';
                List<String> options = def.getOptions();
                if (index >= 0 && index < options.size()) {
                    return options.get(index);
                }
            }
        }
        return value;
    }

    // JSON节点转换为Map，支持复合对象、布尔值和数字类型
    private Map<String, Object> jsonNodeToFlatMap(JsonNode node) {
        Map<String, Object> map = new HashMap<>();
        if (node == null || node.isMissingNode() || !node.isObject()) return map;

        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            // 处理数组
            if (value.isArray()) {
                List<String> list = new ArrayList<>();
                value.forEach(v -> {
                    if (v.isTextual()) list.add(v.asText().trim());
                    else if (v.isBoolean()) list.add(String.valueOf(v.asBoolean()));
                    else if (v.isNumber()) list.add(String.valueOf(v.asDouble()));
                });
                if (!list.isEmpty()) map.put(key, list);
            }
            // 处理复合对象
            else if (value.isObject()) {
                // 对于applicantInfo等复合对象，直接转换为Map，并过滤空字符串
                Map<String, Object> subMap = new HashMap<>();
                value.fields().forEachRemaining(subEntry -> {
                    JsonNode subValue = subEntry.getValue();
                    if (subValue.isTextual()) {
                        String textValue = subValue.asText().trim();
                        if (!textValue.isEmpty()) {
                            subMap.put(subEntry.getKey(), textValue);
                        }
                    } else if (subValue.isBoolean()) {
                        subMap.put(subEntry.getKey(), subValue.asBoolean());
                    } else if (subValue.isNumber()) {
                        subMap.put(subEntry.getKey(), subValue.asDouble());
                    }
                });
                if (!subMap.isEmpty()) {
                    map.put(key, subMap);
                }
            }
            // 处理布尔值
            else if (value.isBoolean()) {
                // 保存所有布尔值，包括true和false
                boolean boolValue = value.asBoolean();
                map.put(key, boolValue);
            }
            // 处理数字
            else if (value.isNumber()) {
                // 对于数字类型，特别是预算规模，0.0视为无效值
                double numValue = value.asDouble();
                if (numValue != 0.0) {
                    map.put(key, numValue);
                } else if (!"budgetScale".equals(key)) {
                    // 其他数字类型可以保留0值
                    map.put(key, numValue);
                }
            }
            // 处理文本
            else if (value.isTextual()) {
                String text = value.asText().trim();
                if (!text.isEmpty()) map.put(key, text);
            }
        });
        return map;
    }

    // 日期标准化
    private void applyDateNormalization(Map<String, Object> params) {
        if (params.containsKey("startDate")) {
            String raw = (String) params.get("startDate");
            String normalized = normalizeDate(raw);
            if (normalized != null) params.put("startDate", normalized);
        }
        if (params.containsKey("endDate")) {
            String raw = (String) params.get("endDate");
            String normalized = normalizeDate(raw);
            if (normalized != null) params.put("endDate", normalized);
        }
    }

    // 日期标准化工具方法
    private String normalizeDate(String input) {
        if (input == null || input.isEmpty()) return null;
        input = input.trim();
        try {
            if (input.matches("\\d{4}")) return input + "-01-01";
            if (input.matches("\\d{4}-\\d{2}")) return input + "-01";
            if (input.matches("\\d{4}-\\d{2}-\\d{2}")) return input;
        } catch (Exception ignored) {}
        return input;
    }

    // 参数键转换为友好标签
    private String getLabel(String key) {
        switch (key) {
            case "topicName": return "课题名称";
            case "disciplineField": return "申报学科领域";
            case "teamInfo": return "研究团队情况";
            case "researchFocus": return "研究重点方向";
            case "outputLanguage": return "输出格式";
            default: return key;
        }
    }
    
    /**
     * 封装流式输出逻辑
     * @param reply 完整回复内容
     * @param streamDataCallback 流式数据回调
     * @param paramsComplete 参数是否完整
     */
    private void streamResponse(String reply, StreamDataCallback streamDataCallback, boolean paramsComplete) {
        // 通过流式方式返回给前端
        StringBuilder accumulatedContent = new StringBuilder();
        char[] chars = reply.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            // 将当前字符添加到累积字符串中
            accumulatedContent.append(chars[i]);
            // 通过Response.ResponseContentChatBuilder流式返回累积的字符串，使用delta字段
            Response charResponse = Response.ResponseContentChatBuilder(accumulatedContent.toString(), "agent");
            
            // 判断是否为最后一个字符，且参数未收集完整时，设置isFinished为true
            // 参数完整时不设置isFinished，因为会自动进入报告生成流程
            if (i == chars.length - 1 && !paramsComplete) {
                ((ResponseContent)charResponse.getData()).setIsFinished("true");
            }
            
            streamDataCallback.onData(charResponse);
            // 添加20毫秒延迟，模拟真实的流式效果
            try {
                Thread.sleep(10); // 20毫秒延迟，根据要求设置
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 解析结构化输入（字段：值格式）
     * 支持识别用户输入中的各种参数定义
     */
    private Map<String, Object> parseStructuredInput(String userMessage, AnalysisContext context) {
        Map<String, Object> extracted = new HashMap<>();
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return extracted;
        }

        String message = userMessage.trim();

        // 1. 提取课题名称（通常在开头或特定位置）
        extractTopicName(message, extracted, context);

        // 2. 使用正则表达式匹配各种字段：值模式
        Map<String, String> fieldPatterns = Map.ofEntries(
                Map.entry("disciplineField", "(?:申报学科领域|学科领域|学科代码|学科)\s*[:：]\s*([^\n。]+)"),
                Map.entry("priorResearch", "(?:前期研究成果|研究成果|已发表论文|专利|前期研究数据)\s*[:：]\s*([^\n。]+)"),
                Map.entry("clinicalData", "(?:临床数据资源|合作医院|数据资源|已有数据)\s*[:：]\s*([^\n。]+)"),
                Map.entry("teamBackground", "(?:团队成员专业背景|专业背景|团队背景)\s*[:：]\s*([^\n。]+)"),
                Map.entry("researchFocus", "(?:研究重点方向|重点方向|研究方向)\s*[:：]\s*([^\n。]+)"),
                Map.entry("outputLanguage", "(?:输出格式|报告输出语言|输出语言|报告语言|语言)\s*[:：]\s*([^\n。]+)")
        );

        // 3. 应用所有匹配规则
        for (Map.Entry<String, String> entry : fieldPatterns.entrySet()) {
            String field = entry.getKey();
            String pattern = entry.getValue();

            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(message);

            if (m.find()) {
                String value = m.group(1).trim();
                if (!value.isEmpty()) {
                    processFieldValue(field, value, extracted);
//                    log.debug("解析到参数 | field={} | value={}", field, value);
                }
            }
        }

        // 4. 提取研究团队信息复合对象
        extractTeamInfo(extracted);

        return extracted;
    }

    /**
     * 提取课题名称
     */
    private void extractTopicName(String message, Map<String, Object> extracted, AnalysisContext context) {
        Map<String, Object> params = context.getQueryParams();
        String userMessage = context.getUserMessage();
        String traceId = context.getTraceId();

        // 检查是否已有课题名称（区分第一轮和后续对话）
        if (params.containsKey("topicName")) {
            // 后续对话：默认沿用已有课题名称
            String existingTopicName = String.valueOf(params.get("topicName"));

            // 检查用户是否明确要求修改课题名称
            if (!isModifyMessage(userMessage)) {
                // 用户没有明确要求修改，直接使用现有课题名称
                extracted.put("topicName", existingTopicName);
                log.info("TOPIC_NAME_REUSED | traceId={} | topicName={}", traceId, existingTopicName);
                return;
            }
            // 如果用户明确要求修改，则继续执行提取逻辑
            log.info("TOPIC_NAME_MODIFY_REQUEST | traceId={} | userMessage={}", traceId, userMessage);
        }

        // 执行现有的课题名称提取逻辑
        // 1. 检查是否以"生成项目申报书："开头
        if (message.startsWith("生成项目申报书：")) {
            String topicPart = message.substring("生成项目申报书：".length()).trim();
            // 找到第一个标点符号或换行符前的部分作为课题名称
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([^。；！？\n]+)");
            java.util.regex.Matcher matcher = pattern.matcher(topicPart);
            if (matcher.find()) {
                String topicName = matcher.group(1).trim();
                if (!topicName.isEmpty() && topicName.length() > 3) {
                    extracted.put("topicName", topicName);
                    log.debug("提取课题名称：{}", topicName);
                }
            }
        }

        // 2. 检查是否包含"课题名称："或"项目名称："
        if (!extracted.containsKey("topicName")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:课题名称|项目名称)\s*[:：]\s*([^。；！？\n]+)");
            java.util.regex.Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String topicName = matcher.group(1).trim();
                if (!topicName.isEmpty()) {
                    extracted.put("topicName", topicName);
                    log.debug("提取课题名称：{}", topicName);
                }
            }
        }

//        // 3. 如果还没提取到，尝试从研究/监测/分析等关键词前的内容提取
//        if (!extracted.containsKey("topicName")) {
//            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([^\n]*?(?:研究|监测|分析|项目)[^\n]*?)(?:[。；\n]|$)");
//            java.util.regex.Matcher matcher = pattern.matcher(message);
//            if (matcher.find()) {
//                String potentialTopic = matcher.group(1).trim();
//                if (potentialTopic.length() > 5 && potentialTopic.length() < 100) {
//                    extracted.put("topicName", potentialTopic);
////                    log.debug("自动提取课题名称：{}", potentialTopic);
//                }
//            }
//        }

        // 3. 如果还没提取到，尝试从科研关键词前的内容提取
        // 扩展关键词列表，覆盖更广泛的科研活动
        if (!extracted.containsKey("topicName")) {
            // 构建包含多种科研关键词的正则表达式
            String scienceKeywords = String.join("|",
                    // 基础研究类
                    "研究", "调查", "探索", "探讨", "考察", "观察", "检测", "监测", "测定", "鉴定",
                    // 评估验证类
                    "分析", "评估", "评价", "验证", "论证", "证实", "检验",
                    // 应用开发类
                    "应用", "开发", "设计", "构建", "建立", "创建", "优化", "改进", "改善",
                    // 医学临床类
                    "预防", "治疗", "诊断", "筛查", "干预", "护理", "康复", "预后",
                    // 试验实验类
                    "试验", "实验", "测试", "试点", "对比", "比较", "实践",
                    // 项目管理类
                    "项目", "课题", "计划", "方案", "策略", "措施",
                    // 技术方法类
                    "技术", "方法", "模型", "系统", "平台", "工具", "机制", "体系",
                    // 效果评价类
                    "效果", "效能", "影响", "作用", "功能", "价值", "意义"
            );

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "([^\n]*?(?:" + scienceKeywords + ")[^\n]*?)(?:[。；\n]|$)");
            java.util.regex.Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String potentialTopic = matcher.group(1).trim();
                // 放宽长度限制，允许更长的课题名称
                if (potentialTopic.length() > 5 && potentialTopic.length() < 150) {
                    // 关键增强：调用LLM验证是否为有效的课题名称
                    if (validateTopicNameWithLLM(potentialTopic, traceId)) {
                        extracted.put("topicName", potentialTopic);
                        log.info("TOPIC_NAME_VALIDATED | traceId={} | topic={} | length={}",
                                traceId, potentialTopic, potentialTopic.length());
                    } else {
                        log.warn("TOPIC_NAME_INVALID | traceId={} | rejectedInput={} | reason=LLM验证未通过",
                                traceId, potentialTopic);
                    }
                }
            }
        }

        // 第一轮对话：如果未识别出课题名称，提醒用户
        if (!extracted.containsKey("topicName") && !params.containsKey("topicName")) {
            log.info("TOPIC_NAME_NOT_RECOGNIZED | traceId={}", traceId);
            // 设置响应内容，提醒用户输入课题名称
            // 注意：这里只是设置响应内容的一部分，完整的响应会在process方法中生成
        }
    }

    /**
     * 处理字段值的标准化
     */
    private void processFieldValue(String field, String value, Map<String, Object> extracted) {
        switch (field) {
            case "outputLanguage":
                // 输出语言处理：标准化为"中文"或"英文"
                String normalizedLanguage = normalizeLanguage(value);
                extracted.put(field, normalizedLanguage);
                break;

            default:
                // 其他字段直接保存字符串值
                if (!value.isEmpty()) {
                    extracted.put(field, value);
                }
                break;
        }
    }

    /**
     * 提取研究团队信息复合对象
     */
    private void extractTeamInfo(Map<String, Object> extracted) {
        // 检查是否有研究团队信息相关字段
        if (extracted.containsKey("priorResearch") ||
                extracted.containsKey("clinicalData") ||
                extracted.containsKey("teamBackground")) {
            Map<String, Object> teamInfo = new HashMap<>();

            // 提取前期研究成果
            if (extracted.containsKey("priorResearch")) {
                teamInfo.put("priorResearch", extracted.remove("priorResearch"));
            }

            // 提取临床数据资源或合作医院
            if (extracted.containsKey("clinicalData")) {
                teamInfo.put("clinicalData", extracted.remove("clinicalData"));
            }

            // 提取团队成员专业背景
            if (extracted.containsKey("teamBackground")) {
                teamInfo.put("teamBackground", extracted.remove("teamBackground"));
            }

            // 只有当至少有一个字段时才添加研究团队信息
            if (!teamInfo.isEmpty()) {
                extracted.put("teamInfo", teamInfo);
                log.debug("提取研究团队信息：{}", teamInfo);
            }
        }
    }

    /**
     * 解析时间范围为startDate和endDate
     */
    private void parseTimeRange(String timeRange, Map<String, Object> extracted) {
        if (timeRange == null || timeRange.trim().isEmpty()) {
            return;
        }

        String range = timeRange.trim();

        // 匹配常见的年份范围格式，如：2020-2024年、2020年-2024年、2020至2024年等
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{4})\\s*[-至到]\\s*(\\d{4})\\s*年?");
        java.util.regex.Matcher matcher = pattern.matcher(range);

        if (matcher.find()) {
            String startYear = matcher.group(1);
            String endYear = matcher.group(2);

            extracted.put("startDate", startYear + "-01-01");
            extracted.put("endDate", endYear + "-12-31");

            log.debug("解析时间范围 | {} -> startDate={}, endDate={}", range, startYear + "-01-01", endYear + "-12-31");
        } else {
            // 如果无法解析范围，尝试提取单个年份作为startDate
            java.util.regex.Pattern singleYearPattern = java.util.regex.Pattern.compile("(\\d{4})\\s*年?");
            java.util.regex.Matcher singleMatcher = singleYearPattern.matcher(range);
            if (singleMatcher.find()) {
                String year = singleMatcher.group(1);
                extracted.put("startDate", year + "-01-01");
                extracted.put("endDate", year + "-12-31");
                log.debug("解析单个年份 | {} -> startDate={}, endDate={}", range, year + "-01-01", year + "-12-31");
            }
        }
    }

    /**
     * 标准化语言参数
     * @param language 用户输入的语言描述
     * @return 标准化后的语言（"中文"或"英文"）
     */
    private String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            return "中文"; // 默认为中文
        }

        String normalized = language.trim().toLowerCase();

        // 判断是否为英文
        if (normalized.contains("英文") || normalized.contains("english") ||
                normalized.contains("en") || normalized.equals("英")) {
            return "英文";
        }

        // 判断是否为中文（明确说明或默认）
        if (normalized.contains("中文") || normalized.contains("chinese") ||
                normalized.contains("zh") || normalized.equals("中")) {
            return "中文";
        }

        // 如果无法判断，默认为中文
        return "中文";
    }

    /**
     * 清理LLM响应中的代码块标记
     *
     * @param response LLM原始响应
     * @return 清理后的JSON字符串
     */
    private String cleanJsonResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return response;
        }

        String cleaned = response.trim();

        // 移除开头的代码块标记
        if (cleaned.startsWith("``json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        // 移除结尾的代码块标记
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    /**
     * 判断是否是友好的错误消息（而非JSON响应）
     *
     * @param message 消息内容
     * @return true表示是错误消息，false表示可能是JSON
     */
    private boolean isFriendlyErrorMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        String lowerMessage = message.toLowerCase();
        // 检查是否包含常见的错误消息关键词
        return lowerMessage.contains("暂时无法处理") ||
               lowerMessage.contains("请稍后重试") ||
               lowerMessage.contains("网络") ||
               lowerMessage.contains("连接") ||
               lowerMessage.contains("异常") ||
               lowerMessage.contains("系统处理出现异常") ||
               lowerMessage.contains("服务暂时不可用") ||
               lowerMessage.contains("请求过于频繁");
    }

    /**
     * 判断字符串是否可能是JSON格式
     *
     * @param text 待检查的文本
     * @return true表示可能是JSON，false表示不是JSON
     */
    private boolean isLikelyJson(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String trimmed = text.trim();
        // JSON通常以 { 或 [ 开头
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    /**
     * 使用LLM验证提取的文本是否为有效的课题名称
     *
     * @param potentialTopic 待验证的课题名称
     * @param traceId 追踪ID
     * @return true表示有效，false表示无效
     */
    private boolean validateTopicNameWithLLM(String potentialTopic, String traceId) {
        try {
            // 快速规则过滤：如果只是单个关键词或过于简短，直接拒绝
            if (potentialTopic.length() <= 3) {
                log.debug("TOPIC_VALIDATION_SKIP | traceId={} | reason=过短 | input={}", traceId, potentialTopic);
                return false;
            }

            // 如果只包含单个词，直接拒绝
            String[] words = potentialTopic.split("\\s+");
            if (words.length == 1 && potentialTopic.length() < 8) {
                log.debug("TOPIC_VALIDATION_SKIP | traceId={} | reason=单个词 | input={}", traceId, potentialTopic);
                return false;
            }

            // 构建验证提示词
            String validationPrompt = String.format("""
                请判断以下文本是否为有效的科研项目课题名称。
                
                判断标准：
                1. 必须包含研究对象或研究内容
                2. 必须包含研究目的或研究方法
                3. 不能是单个关键词（如只有"研究"、"分析"等）
                4. 不能是药品名称、疾病名称等单一实体
                5. 应该是一个完整的、有意义的课题描述
                
                待判断文本："%s"
                
                请严格按照以下JSON格式返回结果，只返回JSON，不要包含任何其他文字：
                {
                  "isValid": true/false,
                  "reason": "判断理由"
                }
                
                示例：
                - "糖尿病研究" → {"isValid": false, "reason": "过于笼统，缺少具体研究内容"}
                - "研究" → {"isValid": false, "reason": "仅为单个关键词，无实际内容"}
                - "乌帕替尼" → {"isValid": false, "reason": "仅为药品名称，非课题名称"}
                - "基于人工智能的糖尿病早期预测研究" → {"isValid": true, "reason": "包含研究对象、方法和目的"}
                """, potentialTopic);

            // 调用LLM进行验证
            String llmResponse = Model.getModel("使用json返回", validationPrompt, "qwen-plus");
            log.debug("TOPIC_VALIDATION_LLM_RAW | traceId={} | response={}", traceId, llmResponse);

            // 清理可能的代码块标记
            String cleanedResponse = cleanJsonResponse(llmResponse);

            // 解析LLM返回的JSON
            JsonNode root = objectMapper.readTree(cleanedResponse);
            boolean isValid = root.has("isValid") ? root.get("isValid").asBoolean() : false;
            String reason = root.has("reason") ? root.get("reason").asText() : "未知原因";

            log.info("TOPIC_VALIDATION_RESULT | traceId={} | input={} | isValid={} | reason={}",
                    traceId, potentialTopic, isValid, reason);

            return isValid;

        } catch (Exception e) {
            log.error("TOPIC_VALIDATION_ERROR | traceId={} | input={} | error={}",
                    traceId, potentialTopic, e.getMessage(), e);
            // 验证失败时，采用保守策略：如果长度>=8且不是单个词，则认为有效
            // 这样即使LLM服务异常，也不会完全阻断用户
            boolean fallbackValid = potentialTopic.length() >= 8 && !potentialTopic.matches("^[\\u4e00-\\u9fa5]{1,4}$");
            log.warn("TOPIC_VALIDATION_FALLBACK | traceId={} | input={} | fallbackValid={}",
                    traceId, potentialTopic, fallbackValid);
            return fallbackValid;
        }
    }

    /**
     * 构建参数收集完整时的固定模板
     *
     * @param params 参数集合
     * @return 固定模板消息
     */
    private String buildCompletedParamsTemplate(Map<String, Object> params) {
        // 获取课题名称
        String topicName = params.containsKey("topicName") ? String.valueOf(params.get("topicName")) : "";
        // 获取申报学科领域
        String disciplineField = params.containsKey("disciplineField") ? String.valueOf(params.get("disciplineField")) : "";
        // 获取研究团队情况
        String teamInfo = formatTeamInfo(params.get("teamInfo"));
        // 获取研究重点方向
        String researchFocus = params.containsKey("researchFocus") ? String.valueOf(params.get("researchFocus")) : "";
        // 获取报告输出语言
        String outputLanguage = params.containsKey("outputLanguage") ? String.valueOf(params.get("outputLanguage")) : "中文";

        // 构建固定模板
        StringBuilder template = new StringBuilder();
        template.append("好的，我正在为您撰写《").append(topicName).append("》项目申报书。已收集到的内容为：\n");
        template.append("\n");
        template.append("1. **申报学科领域**：").append(disciplineField).append("\n");
        template.append("\n");
        template.append("2. **研究团队情况**：").append(teamInfo).append("\n");
        template.append("\n");
        template.append("3. **研究重点方向**：").append(researchFocus).append("\n");
        template.append("\n");
        template.append("4. **输出格式**：").append(outputLanguage);

        return template.toString();
    }

    /**
     * 构建参数未收集完整时的固定模板
     *
     * @param params 参数集合
     * @return 固定模板消息
     */
    private String buildIncompleteParamsTemplate(Map<String, Object> params) {
        // 获取课题名称（如果已提取）
        String topicName = params.containsKey("topicName") ? String.valueOf(params.get("topicName")) : null;

        StringBuilder template = new StringBuilder();

        // 如果有课题名称，先确认课题名称
        if (topicName != null && !topicName.isEmpty()) {
            template.append("好的，我将为您提供《").append(topicName).append("》国自然项目申报书的撰写支持。");
        } else {
            template.append("好的，我将为您提供国自然项目申报书的撰写支持。");
        }

        template.append("为了更好地完成这份申报书，我需要了解一些关键信息：\n\n");

        // 根据缺失的参数动态生成提示
        List<String> missingParams = new ArrayList<>();
        int questionNumber = 0;

        // 检查申报学科领域
        if (!params.containsKey("disciplineField") || params.get("disciplineField") == null) {
            template.append(++questionNumber).append(". **申报学科领域**：\n");
            template.append("哪个学科代码？（例如：H2701医学信息学、C0708生物信息学、H3515临床药理学等）\n\n");
        }

        // 检查研究团队情况
        if (!params.containsKey("teamInfo") || params.get("teamInfo") == null || isTeamInfoIncomplete(params.get("teamInfo"))) {
            template.append(++questionNumber).append(". **研究团队情况**：\n");
            template.append("(1) 您或团队在相关领域已有哪些研究成果？（如已发表论文、专利、前期研究数据等）\n");
            template.append("(2) 是否有相关的临床数据资源或合作医院？\n");
            template.append("(3) 团队成员的专业背景（AI、药学、临床医学等）\n\n");
        }

        // 检查研究重点方向
        if (!params.containsKey("researchFocus") || params.get("researchFocus") == null) {
            template.append(++questionNumber).append(". **研究重点方向**：\n");
            template.append("您希望侧重于哪些方向？\n\n");
        }

        // 检查输出格式
        if (!params.containsKey("outputLanguage") || params.get("outputLanguage") == null) {
            template.append(++questionNumber).append(". **输出格式**：\n");
//            template.append(questionNumber++).append(". 输出格式：\n");
            template.append("您希望最后以什么语种交付？\n");
            template.append("(1) 中文\n");
            template.append("(2) 英文\n\n");
        }

        template.append("我的工作过程中，您可以随时打断我，告诉我新的信息。");

        return template.toString();
    }

    /**
     * 格式化研究团队信息
     *
     * @param teamInfo 研究团队信息对象
     * @return 格式化后的字符串
     */
    private String formatTeamInfo(Object teamInfo) {
        if (teamInfo == null) {
            return "";
        }

        if (teamInfo instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> teamMap = (Map<String, Object>) teamInfo;
            StringBuilder sb = new StringBuilder();

            if (teamMap.containsKey("priorResearch") && teamMap.get("priorResearch") != null) {
                sb.append("前期研究成果：").append(teamMap.get("priorResearch")).append("；");
            }
            if (teamMap.containsKey("clinicalData") && teamMap.get("clinicalData") != null) {
                sb.append("临床数据资源：").append(teamMap.get("clinicalData")).append("；");
            }
            if (teamMap.containsKey("teamBackground") && teamMap.get("teamBackground") != null) {
                sb.append("团队背景：").append(teamMap.get("teamBackground"));
            }

            return sb.toString();
        }

        return String.valueOf(teamInfo);
    }

    /**
     * 检查研究团队信息是否不完整
     *
     * @param teamInfo 研究团队信息对象
     * @return true表示不完整，false表示完整
     */
    private boolean isTeamInfoIncomplete(Object teamInfo) {
        if (teamInfo == null) {
            return true;
        }

        if (teamInfo instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> teamMap = (Map<String, Object>) teamInfo;
            // 检查三个子字段是否都存在且非空
            boolean hasPriorResearch = teamMap.containsKey("priorResearch") && teamMap.get("priorResearch") != null;
            boolean hasClinicalData = teamMap.containsKey("clinicalData") && teamMap.get("clinicalData") != null;
            boolean hasTeamBackground = teamMap.containsKey("teamBackground") && teamMap.get("teamBackground") != null;

            return !(hasPriorResearch && hasClinicalData && hasTeamBackground);
        }

        return false;
    }

}