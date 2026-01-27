// com/faers/agent/agent/impl/ParamRevisionAgent.java
package com.faers.agent.agent.impl;

import com.faers.agent.agent.Agent;
import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.exception.AgentException;
import com.faers.agent.model.Session;
import com.faers.agent.pojo.ProjectApplicationParams;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.service.MongodbSessionStorageService;
import com.faers.agent.utils.Model;
import com.faers.agent.utils.ParamUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

import java.util.Map;

@Component
public class ParamRevisionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ParamRevisionAgent.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private MongodbSessionStorageService sessionStorageService;

    @Override
    public void process(AnalysisContext context, String id,
                        String parentId,
                        String userId,
                        StreamDataCallback streamDataCallback) {
        String traceId = context.getTraceId();
        log.info("PARAM_REVISION_START | traceId={}", traceId);

        try {
            Map<String, Object> params = context.getQueryParams();
            String currentSummary = ParamUtils.buildSummary(params);

            String systemPrompt = """
                用户希望修改已有参数。
                
                当前参数：
                %s
                
                修改指令：
                "%s"
                
                请提取更新字段，并返回 JSON：
                {
                  "extractedParams": { ... },
                  "responseMessage": "确认语句"
                }
                """.formatted(currentSummary.isEmpty() ? "暂无" : currentSummary, context.getUserMessage());

            String modelResponse = Model.getModel("请生成响应。请严格以json格式返回",systemPrompt , "qwen-plus");
            JsonNode root = objectMapper.readTree(modelResponse);
            Map<String, Object> updates = jsonNodeToFlatMap(root.path("extractedParams"));

            applyDateNormalization(updates);
            ParamUtils.updateParams(params, updates);

            // 更新Session并持久化到MongoDB
            Session session = context.getSession();
            if (session != null) {
                // 将最新参数快照映射为 ProjectApplicationParams
                ProjectApplicationParams projectParams = ProjectApplicationParams.fromMap(params);
                
                // 同步核心参数到Session对象
                session.setTopicName(projectParams.getTopicName());
                session.setDisciplineField(projectParams.getDisciplineField());
                session.setTeamInfo(projectParams.getTeamInfo());
                session.setResearchFocus(projectParams.getResearchFocus());
                session.setOutputLanguage(projectParams.getOutputLanguage());
                
                // 更新最后访问时间
                session.setLastAccessed(LocalDateTime.now());
                
                // 保存到MongoDB
                sessionStorageService.saveSession(session);
                log.info("SESSION_UPDATED_AFTER_REVISION | traceId={}", traceId);
            }

            String reply = root.has("responseMessage") ? root.get("responseMessage").asText().trim() : "参数已更新。";
            context.setResponseContent(reply);

            log.info("PARAM_REVISION_DONE | traceId={}", traceId);
        } catch (Exception e) {
            log.error("PARAM_REVISION_ERROR | traceId={}", traceId, e);
            throw new AgentException("参数修改失败", e);
        }
    }

    private Map<String, Object> jsonNodeToFlatMap(JsonNode node) {
        // 同上
        Map<String, Object> map = new java.util.HashMap<>();
        if (node == null || node.isMissingNode() || !node.isObject()) return map;
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isArray()) {
                java.util.List<String> list = new java.util.ArrayList<>();
                value.forEach(v -> {
                    if (v.isTextual()) list.add(v.asText().trim());
                });
                if (!list.isEmpty()) map.put(key, list);
            } else if (value.isTextual()) {
                String text = value.asText().trim();
                if (!text.isEmpty()) map.put(key, text);
            }
        });
        return map;
    }

    private void applyDateNormalization(Map<String, Object> params) {
        if (params.containsKey("startDate")) {
            String raw = (String) params.get("startDate");
            String normalized = ParamUtils.normalizeStartDate(raw);
            if (normalized != null) params.put("startDate", normalized);
        }
        if (params.containsKey("endDate")) {
            String raw = (String) params.get("endDate");
            String normalized = ParamUtils.normalizeEndDate(raw);
            if (normalized != null) params.put("endDate", normalized);
        }
    }
}
