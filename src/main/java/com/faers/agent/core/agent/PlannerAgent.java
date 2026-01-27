package com.faers.agent.core.agent;

import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.core.evidence.ProposalEvidencePack;
import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.service.LLMInvocationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PlannerAgent - Logical Blueprint Creator
 *
 * Responsible for:
 * 1. Analyzing the EvidencePack to identify key themes and gaps
 * 2. Creating a logical blueprint (LogicalBlueprint) before full draft generation
 * 3. Defining the core logical chain: scientific questions -> hypotheses -> innovations
 * 4. Ensuring coherent structure across all proposal sections
 *
 * The PlannerAgent creates a high-level outline that guides the WriterAgent,
 * enabling "think first, write later" approach.
 */
@Service
public class PlannerAgent implements ProposalAgent<ProposalEvidencePack, PlannerAgent.LogicalBlueprint> {

    private static final Logger log = LoggerFactory.getLogger(PlannerAgent.class);

    @Autowired
    private LLMInvocationService llmInvocationService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String PLANNING_PROMPT_TEMPLATE = """
            【角色与任务】
            您是一位科研战略规划专家，擅长从研究证据中提炼核心科学问题和创新点，构建逻辑严密的研究框架。

            【当前任务】
            基于提供的研究证据，为项目申报书创建一份逻辑蓝图（Logical Blueprint）。
            这份蓝图将指导后续的完整申报书撰写，确保各章节之间逻辑连贯、论证严密。

            【输入证据】
            课题名称：%s

            证据摘要：
            %s

            【输出要求】
            请以JSON格式输出逻辑蓝图，包含以下结构：
            ```json
            {
                "topicAnalysis": {
                    "coreTheme": "核心研究主题描述",
                    "researchGaps": ["研究缺口1", "研究缺口2", "研究缺口3"],
                    "keyTerms": ["关键术语1", "关键术语2", "关键术语3"]
                },
                "scientificQuestions": [
                    {
                        "questionId": "SQ1",
                        "question": "核心科学问题描述",
                        "background": "问题背景与重要性",
                        "expectedOutcome": "预期解答方向"
                    }
                ],
                "hypotheses": [
                    {
                        "hypothesisId": "H1",
                        "statement": "研究假设陈述",
                        "rationale": "假设依据",
                        "relatedQuestionIds": ["SQ1"],
                        "verificationApproach": "验证方法"
                    }
                ],
                "innovationPoints": [
                    {
                        "innovationId": "I1",
                        "title": "创新点标题",
                        "description": "创新点描述",
                        "type": "THEORETICAL/METHODOLOGICAL/TECHNICAL",
                        "differentiationFromExisting": "与现有研究的差异"
                    }
                ],
                "researchObjectives": [
                    {
                        "objectiveId": "O1",
                        "objective": "研究目标描述",
                        "relatedQuestionIds": ["SQ1"],
                        "measurableOutcome": "可量化的成果指标"
                    }
                ],
                "sectionOutline": {
                    "researchSignificance": "研究意义章节的核心论点概要",
                    "researchStatus": "国内外研究现状的分析框架",
                    "keyScientificProblems": "关键科学问题章节的组织思路",
                    "researchContent": "研究内容章节的模块划分",
                    "technicalRoute": "技术路线的关键节点",
                    "innovationPoints": "特色与创新章节的组织框架"
                },
                "evidenceMapping": {
                    "strongEvidence": ["支持力度强的证据ID列表"],
                    "moderateEvidence": ["支持力度中等的证据ID列表"],
                    "gapsNeedingAttention": ["需要补充证据的领域"]
                }
            }
            ```

            【重要约束】
            1. 科学问题应该具体、可验证，避免过于宽泛
            2. 假设应与科学问题直接关联，有明确的验证路径
            3. 创新点应基于现有研究缺口，避免绝对化表述（禁止使用"首次"、"首创"、"唯一"）
            4. 研究目标应可量化，与科学问题和假设形成闭环
            5. 各章节大纲应相互呼应，形成完整的论证链

            请确保输出是有效的JSON格式。
            """;

    @Override
    public String getAgentName() {
        return "PlannerAgent";
    }

    @Override
    public String getAgentDescription() {
        return "Logical blueprint creator: analyzes evidence and creates structured research framework";
    }

    @Override
    public LogicalBlueprint execute(ProposalEvidencePack evidencePack, AnalysisContext context,
                                     StreamDataCallback callback, String traceId) throws Exception {
        log.info("PLANNER_AGENT_START | traceId={} | evidenceCount={}",
                traceId, evidencePack.getEvidenceCards().size());

        sendStatus(callback, "开始规划研究框架...", "正在分析证据并构建逻辑蓝图");

        // Build the planning prompt
        String prompt = String.format(PLANNING_PROMPT_TEMPLATE,
                evidencePack.getTopicName(),
                evidencePack.toPromptFormat());

        // Call LLM to generate blueprint
        sendStatus(callback, "正在生成逻辑蓝图", "AI正在分析研究主题和证据");

        StringBuilder responseBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = new StreamDataCallback() {
            @Override
            public void onData(Response response) {
                // Don't forward to user callback - just collect
            }

            @Override
            public void onData(String chunk) {
                responseBuilder.append(chunk);
            }
        };

        String llmResponse = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);

        // Parse the response
        LogicalBlueprint blueprint = parseBlueprint(llmResponse, evidencePack, traceId);

        log.info("PLANNER_AGENT_COMPLETE | traceId={} | questions={} | hypotheses={} | innovations={}",
                traceId,
                blueprint.getScientificQuestions().size(),
                blueprint.getHypotheses().size(),
                blueprint.getInnovationPoints().size());

        sendStatus(callback, "逻辑蓝图构建完成",
                String.format("识别 %d 个科学问题，%d 个研究假设，%d 个创新点",
                        blueprint.getScientificQuestions().size(),
                        blueprint.getHypotheses().size(),
                        blueprint.getInnovationPoints().size()));

        return blueprint;
    }

    /**
     * Parse LLM response into LogicalBlueprint
     */
    private LogicalBlueprint parseBlueprint(String llmResponse, ProposalEvidencePack evidencePack, String traceId) {
        LogicalBlueprint blueprint = new LogicalBlueprint();
        blueprint.setBlueprintId(UUID.randomUUID().toString());
        blueprint.setEvidencePackId(evidencePack.getPackId());
        blueprint.setTopicName(evidencePack.getTopicName());

        try {
            // Extract JSON from response
            String jsonContent = extractJson(llmResponse);
            JsonNode root = objectMapper.readTree(jsonContent);

            // Parse topic analysis
            if (root.has("topicAnalysis")) {
                JsonNode analysis = root.get("topicAnalysis");
                blueprint.setCoreTheme(getTextOrDefault(analysis, "coreTheme", ""));
                blueprint.setResearchGaps(getStringList(analysis, "researchGaps"));
                blueprint.setKeyTerms(getStringList(analysis, "keyTerms"));
            }

            // Parse scientific questions
            if (root.has("scientificQuestions") && root.get("scientificQuestions").isArray()) {
                for (JsonNode qNode : root.get("scientificQuestions")) {
                    ScientificQuestion sq = new ScientificQuestion();
                    sq.setQuestionId(getTextOrDefault(qNode, "questionId", "SQ" + (blueprint.getScientificQuestions().size() + 1)));
                    sq.setQuestion(getTextOrDefault(qNode, "question", ""));
                    sq.setBackground(getTextOrDefault(qNode, "background", ""));
                    sq.setExpectedOutcome(getTextOrDefault(qNode, "expectedOutcome", ""));
                    blueprint.getScientificQuestions().add(sq);
                }
            }

            // Parse hypotheses
            if (root.has("hypotheses") && root.get("hypotheses").isArray()) {
                for (JsonNode hNode : root.get("hypotheses")) {
                    Hypothesis h = new Hypothesis();
                    h.setHypothesisId(getTextOrDefault(hNode, "hypothesisId", "H" + (blueprint.getHypotheses().size() + 1)));
                    h.setStatement(getTextOrDefault(hNode, "statement", ""));
                    h.setRationale(getTextOrDefault(hNode, "rationale", ""));
                    h.setRelatedQuestionIds(getStringList(hNode, "relatedQuestionIds"));
                    h.setVerificationApproach(getTextOrDefault(hNode, "verificationApproach", ""));
                    blueprint.getHypotheses().add(h);
                }
            }

            // Parse innovation points
            if (root.has("innovationPoints") && root.get("innovationPoints").isArray()) {
                for (JsonNode iNode : root.get("innovationPoints")) {
                    InnovationPoint ip = new InnovationPoint();
                    ip.setInnovationId(getTextOrDefault(iNode, "innovationId", "I" + (blueprint.getInnovationPoints().size() + 1)));
                    ip.setTitle(getTextOrDefault(iNode, "title", ""));
                    ip.setDescription(getTextOrDefault(iNode, "description", ""));
                    ip.setType(getTextOrDefault(iNode, "type", "METHODOLOGICAL"));
                    ip.setDifferentiationFromExisting(getTextOrDefault(iNode, "differentiationFromExisting", ""));
                    blueprint.getInnovationPoints().add(ip);
                }
            }

            // Parse research objectives
            if (root.has("researchObjectives") && root.get("researchObjectives").isArray()) {
                for (JsonNode oNode : root.get("researchObjectives")) {
                    ResearchObjective ro = new ResearchObjective();
                    ro.setObjectiveId(getTextOrDefault(oNode, "objectiveId", "O" + (blueprint.getResearchObjectives().size() + 1)));
                    ro.setObjective(getTextOrDefault(oNode, "objective", ""));
                    ro.setRelatedQuestionIds(getStringList(oNode, "relatedQuestionIds"));
                    ro.setMeasurableOutcome(getTextOrDefault(oNode, "measurableOutcome", ""));
                    blueprint.getResearchObjectives().add(ro);
                }
            }

            // Parse section outline
            if (root.has("sectionOutline")) {
                JsonNode outline = root.get("sectionOutline");
                SectionOutline so = new SectionOutline();
                so.setResearchSignificance(getTextOrDefault(outline, "researchSignificance", ""));
                so.setResearchStatus(getTextOrDefault(outline, "researchStatus", ""));
                so.setKeyScientificProblems(getTextOrDefault(outline, "keyScientificProblems", ""));
                so.setResearchContent(getTextOrDefault(outline, "researchContent", ""));
                so.setTechnicalRoute(getTextOrDefault(outline, "technicalRoute", ""));
                so.setInnovationPoints(getTextOrDefault(outline, "innovationPoints", ""));
                blueprint.setSectionOutline(so);
            }

            log.info("PLANNER_PARSE_SUCCESS | traceId={}", traceId);

        } catch (Exception e) {
            log.error("PLANNER_PARSE_ERROR | traceId={} | error={}", traceId, e.getMessage(), e);
            // Create a minimal blueprint if parsing fails
            createMinimalBlueprint(blueprint, evidencePack);
        }

        return blueprint;
    }

    private String extractJson(String text) {
        // Try to extract JSON from markdown code blocks
        int jsonStart = text.indexOf("```json");
        if (jsonStart != -1) {
            int contentStart = text.indexOf("\n", jsonStart) + 1;
            int jsonEnd = text.indexOf("```", contentStart);
            if (jsonEnd != -1) {
                return text.substring(contentStart, jsonEnd).trim();
            }
        }

        // Try to find JSON object directly
        int braceStart = text.indexOf("{");
        int braceEnd = text.lastIndexOf("}");
        if (braceStart != -1 && braceEnd != -1 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1);
        }

        return text;
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }

    private List<String> getStringList(JsonNode node, String field) {
        List<String> result = new ArrayList<>();
        if (node.has(field) && node.get(field).isArray()) {
            for (JsonNode item : node.get(field)) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private void createMinimalBlueprint(LogicalBlueprint blueprint, ProposalEvidencePack evidencePack) {
        // Create a minimal blueprint based on topic name
        ScientificQuestion sq = new ScientificQuestion();
        sq.setQuestionId("SQ1");
        sq.setQuestion("如何" + evidencePack.getTopicName() + "？");
        sq.setBackground("基于现有研究，该领域存在以下研究缺口需要解决");
        sq.setExpectedOutcome("通过系统性研究，建立完整的理论和方法体系");
        blueprint.getScientificQuestions().add(sq);

        Hypothesis h = new Hypothesis();
        h.setHypothesisId("H1");
        h.setStatement("通过本研究提出的方法，可以有效解决上述科学问题");
        h.setRationale("基于前期研究基础和证据支持");
        h.setRelatedQuestionIds(List.of("SQ1"));
        h.setVerificationApproach("通过实验验证和案例分析");
        blueprint.getHypotheses().add(h);

        InnovationPoint ip = new InnovationPoint();
        ip.setInnovationId("I1");
        ip.setTitle("方法创新");
        ip.setDescription("提出新的研究方法和技术路线");
        ip.setType("METHODOLOGICAL");
        ip.setDifferentiationFromExisting("相比现有方法，具有更高的效率和准确性");
        blueprint.getInnovationPoints().add(ip);
    }

    private void sendStatus(StreamDataCallback callback, String title, String description) {
        if (callback != null) {
            try {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder(title, description, callId, "");
                callback.onData(statusResponse);
            } catch (Exception e) {
                log.warn("PLANNER_STATUS_SEND_ERROR | error={}", e.getMessage());
            }
        }
    }

    // Inner classes for LogicalBlueprint structure

    @lombok.Data
    public static class LogicalBlueprint {
        private String blueprintId;
        private String evidencePackId;
        private String topicName;
        private String coreTheme;
        private List<String> researchGaps = new ArrayList<>();
        private List<String> keyTerms = new ArrayList<>();
        private List<ScientificQuestion> scientificQuestions = new ArrayList<>();
        private List<Hypothesis> hypotheses = new ArrayList<>();
        private List<InnovationPoint> innovationPoints = new ArrayList<>();
        private List<ResearchObjective> researchObjectives = new ArrayList<>();
        private SectionOutline sectionOutline = new SectionOutline();
    }

    @lombok.Data
    public static class ScientificQuestion {
        private String questionId;
        private String question;
        private String background;
        private String expectedOutcome;
    }

    @lombok.Data
    public static class Hypothesis {
        private String hypothesisId;
        private String statement;
        private String rationale;
        private List<String> relatedQuestionIds = new ArrayList<>();
        private String verificationApproach;
    }

    @lombok.Data
    public static class InnovationPoint {
        private String innovationId;
        private String title;
        private String description;
        private String type;
        private String differentiationFromExisting;
    }

    @lombok.Data
    public static class ResearchObjective {
        private String objectiveId;
        private String objective;
        private List<String> relatedQuestionIds = new ArrayList<>();
        private String measurableOutcome;
    }

    @lombok.Data
    public static class SectionOutline {
        private String researchSignificance;
        private String researchStatus;
        private String keyScientificProblems;
        private String researchContent;
        private String technicalRoute;
        private String innovationPoints;
    }
}
