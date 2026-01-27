// com/faers/agent/agent/impl/ExploreResponseAgent.java
package com.faers.agent.agent.impl;

import com.faers.agent.agent.Agent;
import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.config.AnalysisState;
import com.faers.agent.exception.AgentException;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.utils.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 探索性响应智能体：基于 cleanJson 回答用户问题
 */
@Component
public class ExploreResponseAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ExploreResponseAgent.class);

    @Override
    public void process(AnalysisContext context, String id,
                        String parentId,
                        String userId,
                        StreamDataCallback streamDataCallback) {
        String traceId = context.getTraceId();
        log.info("EXPLORE_RESPONSE_START | traceId={}", traceId);

        try {
            Map<String, Object> params = context.getQueryParams();
            String cleanJson = context.getCleanJson();

            if (cleanJson == null || cleanJson.isEmpty()) {
                context.setResponseContent("暂无分析数据，请先完成参数设置并启动分析。");
                return;
            }

            String prompt = """
                你是一位项目申报书研究专家，请根据以下分析结果向用户解释关键发现。
                
                === 用户参数 ===
                %s
                
                === 分析结果摘要 ===
                %s
                
                要求：
                1. 保持专业、客观、简洁；
                2. 可使用 Markdown 表格展示研究结果；
                3. 若数据不足，请说明原因；
                4. 不要编造信息；
                5. 回答直接针对问题，不要重复问题；
                6. 使用中文，语气友好但正式。
                7. 200-500字左右回复用户问题
                """.formatted(
                    formatParamsSummary(params),
                    truncate(cleanJson, 80000)
                );

            String answer = Model.getModel("回答用户问题",
                    prompt,
                "qwen-plus"
            ).trim();

            context.setResponseContent(answer);
            log.info("EXPLORE_RESPONSE_END | traceId={} | length={}", traceId, answer.length());

        } catch (Exception e) {
            log.error("EXPLORE_RESPONSE_ERROR | traceId={}", traceId, e);
            context.setResponseContent("回答生成失败：" + e.getMessage());
            throw new AgentException("探索性响应失败", e);
        }
    }

    private String formatParamsSummary(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        
        // 添加所有6个必需参数
        appendIfPresent(sb, "课题名称", params.get("topicName"));
        appendIfPresent(sb, "计划申报类别", params.get("applyCategory"));
        
        // 处理布尔值参数
        Object hasPriorWork = params.get("hasPriorWork");
        if (hasPriorWork != null) {
            String value = hasPriorWork instanceof Boolean ? 
                ((Boolean) hasPriorWork ? "有" : "无") : 
                hasPriorWork.toString();
            sb.append("- 课题前期基础：").append(value).append("\n");
        }
        
        appendIfPresent(sb, "数据来源", params.get("dataSource"));
        
        // 处理复合对象参数
        Object applicantInfo = params.get("applicantInfo");
        if (applicantInfo != null) {
            if (applicantInfo instanceof Map) {
                Map<String, Object> info = (Map<String, Object>) applicantInfo;
                StringBuilder infoSb = new StringBuilder();
                appendIfPresent(infoSb, "研究方向", info.get("researchDirection"));
                appendIfPresent(infoSb, "职称", info.get("title"));
                appendIfPresent(infoSb, "单位类型", info.get("unitType"));
                
                if (infoSb.length() > 0) {
                    sb.append("- 申请人信息：\n").append(infoSb.toString());
                }
            } else {
                sb.append("- 申请人信息：").append(applicantInfo.toString()).append("\n");
            }
        }
        
        Object budgetScale = params.get("budgetScale");
        if (budgetScale != null) {
            sb.append("- 预算规模：").append(budgetScale.toString()).append(" 元\n");
        }
        
        appendIfPresent(sb, "报告输出语言", params.get("outputLanguage"));
        
        // 保留原有参数以确保向后兼容
        appendIfPresent(sb, "研究领域", params.get("researchField"));
        appendIfPresent(sb, "时间范围", combineDateRange(params.get("startDate"), params.get("endDate")));
        appendIfPresent(sb, "研究类型", params.get("researchType"));
        
        return sb.toString();
    }

    private String combineDateRange(Object start, Object end) {
        if (start == null && end == null) return "";
        return (start != null ? start : "?") + " ~ " + (end != null ? end : "?");
    }

    private void appendIfPresent(StringBuilder sb, String label, Object value) {
        if (value != null && !String.valueOf(value).isEmpty()) {
            sb.append("- ").append(label).append("：").append(value).append("\n");
        }
    }

    private String truncate(String str, int len) {
        return str == null ? "" : str.length() <= len ? str : str.substring(0, len) + "...";
    }
}