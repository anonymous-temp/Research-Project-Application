package com.faers.agent.core.orchestrator;

import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.core.agent.CriticAgent;
import com.faers.agent.core.agent.PlannerAgent;
import com.faers.agent.core.agent.ResearcherAgent;
import com.faers.agent.core.draft.ProposalDraftPack;
import com.faers.agent.core.evidence.ProposalEvidencePack;
import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.service.LLMInvocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;

/**
 * ProposalGenerationOrchestrator - Main coordinator for proposal generation
 *
 * This orchestrator implements the four-step main flow as specified in the design:
 * 1. Input Parsing & Evidence Collection (ResearcherAgent)
 * 2. Logical Blueprint Creation (PlannerAgent)
 * 3. Core Draft Generation (WriterAgent - via LLM)
 * 4. Quality Assurance (CriticAgent)
 *
 * Key design principles:
 * - Separation of evidence collection (engineering) from generation (LLM)
 * - Standardized intermediate objects (EvidencePack, DraftPack)
 * - Multi-agent collaboration with specialized roles
 * - Think-Write-Reflect cognitive loop
 */
@Service
public class ProposalGenerationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProposalGenerationOrchestrator.class);

    @Autowired
    private ResearcherAgent researcherAgent;

    @Autowired
    private PlannerAgent plannerAgent;

    @Autowired
    private CriticAgent criticAgent;

    @Autowired
    private LLMInvocationService llmInvocationService;

    /**
     * Execute the complete proposal generation pipeline
     *
     * @param request The generation request containing topic and parameters
     * @param context The analysis context
     * @param callback Callback for streaming updates
     * @param traceId Trace ID for logging
     * @return The generated proposal result
     */
    public ProposalResult generateProposal(GenerationRequest request, AnalysisContext context,
                                           StreamDataCallback callback, String traceId) throws Exception {
        log.info("ORCHESTRATOR_START | traceId={} | topic={}", traceId, request.getTopicName());

        ProposalResult result = new ProposalResult();
        result.setResultId(UUID.randomUUID().toString());
        result.setTraceId(traceId);
        result.setTopicName(request.getTopicName());
        result.setStartTime(LocalDateTime.now());

        sendStatus(callback, "开始生成项目申报书", "正在启动多智能体协作流程");

        try {
            // ========== Stage 1: Evidence Collection ==========
            log.info("ORCHESTRATOR_STAGE_1 | traceId={} | stage=EVIDENCE_COLLECTION", traceId);
            sendStatus(callback, "【阶段1/4】证据收集", "ResearcherAgent 正在搜索文献和网络资源");

            ResearcherAgent.ResearchRequest researchRequest = ResearcherAgent.ResearchRequest.of(request.getTopicName());
            researchRequest.setKeywords(request.getKeywords());
            researchRequest.setPreviousProposal(request.getPreviousProposal());

            ProposalEvidencePack evidencePack = researcherAgent.execute(researchRequest, context, callback, traceId);
            result.setEvidencePack(evidencePack);

            log.info("ORCHESTRATOR_STAGE_1_COMPLETE | traceId={} | evidenceCount={}",
                    traceId, evidencePack.getEvidenceCards().size());

            // ========== Stage 2: Logical Blueprint Creation ==========
            log.info("ORCHESTRATOR_STAGE_2 | traceId={} | stage=PLANNING", traceId);
            sendStatus(callback, "【阶段2/4】逻辑规划", "PlannerAgent 正在构建研究框架蓝图");

            PlannerAgent.LogicalBlueprint blueprint = plannerAgent.execute(evidencePack, context, callback, traceId);
            result.setBlueprint(blueprint);

            log.info("ORCHESTRATOR_STAGE_2_COMPLETE | traceId={} | questions={} | hypotheses={}",
                    traceId, blueprint.getScientificQuestions().size(), blueprint.getHypotheses().size());

            // ========== Stage 3: Core Draft Generation ==========
            log.info("ORCHESTRATOR_STAGE_3 | traceId={} | stage=GENERATION", traceId);
            sendStatus(callback, "【阶段3/4】核心生成", "WriterAgent 正在基于证据和蓝图生成申报书");

            ProposalDraftPack draftPack = generateDraft(evidencePack, blueprint, context, callback, traceId);
            result.setDraftPack(draftPack);

            log.info("ORCHESTRATOR_STAGE_3_COMPLETE | traceId={} | wordCount={}",
                    traceId, draftPack.getTotalWordCount());

            // ========== Stage 4: Quality Assurance ==========
            log.info("ORCHESTRATOR_STAGE_4 | traceId={} | stage=VALIDATION", traceId);
            sendStatus(callback, "【阶段4/4】质量审查", "CriticAgent 正在进行事实核查和逻辑验证");

            CriticAgent.ValidationRequest validationRequest = CriticAgent.ValidationRequest.builder()
                    .draftPack(draftPack)
                    .evidencePack(evidencePack)
                    .draftContent(draftPack.assembleFullProposalHtml())
                    .build();

            CriticAgent.ValidationReport validationReport = criticAgent.execute(validationRequest, context, callback, traceId);
            result.setValidationReport(validationReport);

            log.info("ORCHESTRATOR_STAGE_4_COMPLETE | traceId={} | isValid={} | score={}",
                    traceId, validationReport.isValid(), validationReport.getOverallScore());

            // ========== Finalize ==========
            result.setEndTime(LocalDateTime.now());
            result.setSuccess(true);

            // Store final paper in context for compatibility
            context.setFinalPaper(draftPack.assembleFullProposalHtml());

            sendStatus(callback, "申报书生成完成",
                    String.format("总字数: %d, 质量评分: %.1f%%",
                            draftPack.getTotalWordCount(),
                            validationReport.getOverallScore() * 100));

            log.info("ORCHESTRATOR_COMPLETE | traceId={} | success={} | duration={}ms",
                    traceId, result.isSuccess(),
                    java.time.Duration.between(result.getStartTime(), result.getEndTime()).toMillis());

        } catch (Exception e) {
            log.error("ORCHESTRATOR_ERROR | traceId={} | error={}", traceId, e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());

            sendStatus(callback, "生成过程出错", e.getMessage());
            throw e;
        }

        return result;
    }

    /**
     * Generate the draft using holistic LLM approach
     */
    private ProposalDraftPack generateDraft(ProposalEvidencePack evidencePack,
                                             PlannerAgent.LogicalBlueprint blueprint,
                                             AnalysisContext context,
                                             StreamDataCallback callback,
                                             String traceId) throws Exception {

        ProposalDraftPack draftPack = ProposalDraftPack.builder()
                .draftId(UUID.randomUUID().toString())
                .evidencePackId(evidencePack.getPackId())
                .traceId(traceId)
                .topicName(evidencePack.getTopicName())
                .createdAt(LocalDateTime.now())
                .build();

        // Build the comprehensive generation prompt
        String prompt = buildHolisticGenerationPrompt(evidencePack, blueprint);

        // Stream the generation
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = new StreamDataCallback() {
            @Override
            public void onData(Response response) {
                callback.onData(response);
            }

            @Override
            public void onData(String chunk) {
                contentBuilder.append(chunk);
                // Forward to user callback
                Response reportResponse = Response.ResponseReportChatBuilder(contentBuilder.toString(), "agent");
                callback.onData(reportResponse);
            }
        };

        String generatedContent = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);

        // Parse the generated content into structured sections
        parseGeneratedContent(draftPack, generatedContent);

        // Add logical chain from blueprint
        ProposalDraftPack.LogicalChain logicalChain = new ProposalDraftPack.LogicalChain();
        for (PlannerAgent.ScientificQuestion sq : blueprint.getScientificQuestions()) {
            logicalChain.getScientificQuestions().add(ProposalDraftPack.ScientificQuestion.builder()
                    .questionId(sq.getQuestionId())
                    .question(sq.getQuestion())
                    .background(sq.getBackground())
                    .expectedOutcome(sq.getExpectedOutcome())
                    .build());
        }
        for (PlannerAgent.Hypothesis h : blueprint.getHypotheses()) {
            logicalChain.getHypotheses().add(ProposalDraftPack.Hypothesis.builder()
                    .hypothesisId(h.getHypothesisId())
                    .statement(h.getStatement())
                    .rationale(h.getRationale())
                    .relatedQuestionIds(h.getRelatedQuestionIds())
                    .verificationApproach(h.getVerificationApproach())
                    .build());
        }
        for (PlannerAgent.InnovationPoint ip : blueprint.getInnovationPoints()) {
            logicalChain.getInnovationPoints().add(ProposalDraftPack.InnovationPoint.builder()
                    .innovationId(ip.getInnovationId())
                    .title(ip.getTitle())
                    .description(ip.getDescription())
                    .type(ip.getType())
                    .differentiationFromExisting(ip.getDifferentiationFromExisting())
                    .build());
        }
        draftPack.setLogicalChain(logicalChain);

        return draftPack;
    }

    /**
     * Build the holistic generation prompt
     */
    private String buildHolisticGenerationPrompt(ProposalEvidencePack evidencePack,
                                                  PlannerAgent.LogicalBlueprint blueprint) {
        return String.format(HOLISTIC_GENERATION_PROMPT,
                evidencePack.getTopicName(),
                formatBlueprint(blueprint),
                evidencePack.toPromptFormat());
    }

    private String formatBlueprint(PlannerAgent.LogicalBlueprint blueprint) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 逻辑蓝图 ===\n\n");

        sb.append("【核心主题】\n").append(blueprint.getCoreTheme()).append("\n\n");

        sb.append("【科学问题】\n");
        for (PlannerAgent.ScientificQuestion sq : blueprint.getScientificQuestions()) {
            sb.append("- ").append(sq.getQuestionId()).append(": ").append(sq.getQuestion()).append("\n");
            sb.append("  背景: ").append(sq.getBackground()).append("\n");
        }
        sb.append("\n");

        sb.append("【研究假设】\n");
        for (PlannerAgent.Hypothesis h : blueprint.getHypotheses()) {
            sb.append("- ").append(h.getHypothesisId()).append(": ").append(h.getStatement()).append("\n");
            sb.append("  验证方法: ").append(h.getVerificationApproach()).append("\n");
        }
        sb.append("\n");

        sb.append("【创新点】\n");
        for (PlannerAgent.InnovationPoint ip : blueprint.getInnovationPoints()) {
            sb.append("- ").append(ip.getTitle()).append(": ").append(ip.getDescription()).append("\n");
        }
        sb.append("\n");

        if (blueprint.getSectionOutline() != null) {
            sb.append("【章节大纲】\n");
            PlannerAgent.SectionOutline outline = blueprint.getSectionOutline();
            if (outline.getResearchSignificance() != null && !outline.getResearchSignificance().isEmpty()) {
                sb.append("- 研究意义: ").append(outline.getResearchSignificance()).append("\n");
            }
            if (outline.getResearchStatus() != null && !outline.getResearchStatus().isEmpty()) {
                sb.append("- 研究现状: ").append(outline.getResearchStatus()).append("\n");
            }
            if (outline.getKeyScientificProblems() != null && !outline.getKeyScientificProblems().isEmpty()) {
                sb.append("- 关键问题: ").append(outline.getKeyScientificProblems()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Parse generated content into draft pack sections
     */
    private void parseGeneratedContent(ProposalDraftPack draftPack, String content) {
        ProposalDraftPack.ProposalSections sections = new ProposalDraftPack.ProposalSections();

        // For simplicity, store the full content as the main section
        // A full implementation would parse each section
        sections.setResearchSignificance(ProposalDraftPack.SectionContent.builder()
                .sectionId("1.1")
                .title("研究意义")
                .htmlContent(content)
                .wordCount(countChineseWords(content))
                .build());

        draftPack.setSections(sections);
    }

    private int countChineseWords(String text) {
        if (text == null) return 0;
        // Simple Chinese character count
        int count = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                count++;
            }
        }
        return count;
    }

    private void sendStatus(StreamDataCallback callback, String title, String description) {
        if (callback != null) {
            try {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder(title, description, callId, "");
                callback.onData(statusResponse);
            } catch (Exception e) {
                log.warn("ORCHESTRATOR_STATUS_SEND_ERROR | error={}", e.getMessage());
            }
        }
    }

    // Request and Result classes

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GenerationRequest {
        private String topicName;
        private java.util.List<String> keywords;
        private String previousProposal;
        private String fundType;
        private java.util.Map<String, Object> additionalParams;
    }

    @lombok.Data
    public static class ProposalResult {
        private String resultId;
        private String traceId;
        private String topicName;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean success;
        private String errorMessage;
        private ProposalEvidencePack evidencePack;
        private PlannerAgent.LogicalBlueprint blueprint;
        private ProposalDraftPack draftPack;
        private CriticAgent.ValidationReport validationReport;

        public String getFinalHtml() {
            if (draftPack != null) {
                return draftPack.assembleFullProposalHtml();
            }
            return null;
        }
    }

    // Holistic generation prompt template
    private static final String HOLISTIC_GENERATION_PROMPT = """
            【角色与任务】
            您是一位资深的科研项目申报专家，具有丰富的国家自然科学基金申报经验。
            请基于提供的逻辑蓝图和研究证据，生成一份完整的项目申报书。

            【核心约束 - 必须严格遵守】
            1. 禁止使用完成式表述：不得使用"已完成"、"已验证"、"已证明"等暗示研究已完成的词语
            2. 禁止绝对化表述：不得使用"首次"、"首创"、"唯一"、"最先进"等绝对性词语，改用"有望"、"拟"、"预期"
            3. 证据溯源：所有论点必须引用证据编号，格式为[编号]
            4. 逻辑一致：各章节之间必须保持逻辑连贯，研究内容必须能回应科学问题，创新点必须与现有研究差异化

            【项目名称】
            %s

            【逻辑蓝图】
            %s

            【证据资料】
            %s

            【输出格式要求】
            请按照以下结构生成完整的HTML格式申报书：

            1. 摘要（200-250字）
            2. 立项依据与研究内容
               2.1 研究意义与背景
               2.2 国内外研究现状与发展趋势
               2.3 拟解决的关键科学问题
               2.4 研究目标与研究内容
            3. 研究方案及可行性分析
               3.1 技术路线与研究方案
               3.2 可行性分析
               3.3 本项目的特色与创新之处
            4. 研究基础与工作条件
               4.1 研究基础
               4.2 工作条件
            5. 研究计划与进度安排
               5.1 研究计划与进度安排
               5.2 预期研究成果
            6. 风险分析与对策

            【排版规范】
            - 一级标题使用 <h1 style="text-align: center;">
            - 二级标题使用 <h2>
            - 三级标题使用 <h3>
            - 四级标题使用 <h4>
            - 段落首行缩进使用 &emsp;&emsp;
            - 表格使用标准三线表格式

            请开始生成申报书内容：
            """;
}
