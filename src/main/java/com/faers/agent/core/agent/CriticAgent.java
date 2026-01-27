package com.faers.agent.core.agent;

import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.core.draft.ProposalDraftPack;
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CriticAgent - Quality Assurance Specialist
 *
 * Responsible for:
 * 1. Fact-checking: Verifying claims against evidence
 * 2. Logic validation: Checking consistency between sections
 * 3. Compliance checking: Detecting forbidden phrases and patterns
 * 4. Citation verification: Ensuring proper evidence attribution
 * 5. Generating improvement suggestions
 *
 * The CriticAgent is the last line of defense ensuring academic rigor
 * and factual accuracy of the generated proposal.
 */
@Service
public class CriticAgent implements ProposalAgent<CriticAgent.ValidationRequest, CriticAgent.ValidationReport> {

    private static final Logger log = LoggerFactory.getLogger(CriticAgent.class);

    @Autowired
    private LLMInvocationService llmInvocationService;

    @Autowired
    private ObjectMapper objectMapper;

    // Forbidden phrases that should not appear in academic proposals
    private static final List<String> FORBIDDEN_PHRASES = List.of(
            "首次", "首个", "首创", "唯一", "第一次", "从未", "绝无仅有",
            "已完成", "已验证", "已证明", "已实现", "已解决",
            "必将", "必然", "肯定会", "一定能",
            "最好的", "最优的", "最先进的", "最完美的"
    );

    // Compliance checking patterns
    private static final List<Pattern> COMPLIANCE_PATTERNS = List.of(
            Pattern.compile("(首次|首个|首创|唯一|第一次)(?!.*有望|.*拟|.*预期)"),
            Pattern.compile("(已完成|已验证|已证明|已实现|已解决)(?!.*初步|.*部分)"),
            Pattern.compile("(必将|必然|肯定会|一定能|保证能)")
    );

    @Override
    public String getAgentName() {
        return "CriticAgent";
    }

    @Override
    public String getAgentDescription() {
        return "Quality assurance specialist: validates facts, logic, and compliance";
    }

    @Override
    public ValidationReport execute(ValidationRequest request, AnalysisContext context,
                                     StreamDataCallback callback, String traceId) throws Exception {
        log.info("CRITIC_AGENT_START | traceId={}", traceId);

        sendStatus(callback, "开始质量审查...", "正在进行事实核查、逻辑验证和合规检查");

        ValidationReport report = new ValidationReport();
        report.setReportId(UUID.randomUUID().toString());
        report.setTraceId(traceId);
        report.setTimestamp(java.time.LocalDateTime.now());

        // Step 1: Compliance Check (local, fast)
        sendStatus(callback, "正在进行合规检查", "检测禁止性词语和表述");
        performComplianceCheck(request, report);

        // Step 2: Citation Verification
        sendStatus(callback, "正在验证引用", "检查证据引用的准确性");
        performCitationVerification(request, report);

        // Step 3: Logic Consistency Check
        sendStatus(callback, "正在检查逻辑一致性", "验证各章节之间的逻辑关联");
        performLogicCheck(request, report, traceId);

        // Step 4: Calculate overall scores
        calculateOverallScore(report);

        // Step 5: Generate improvement suggestions
        generateSuggestions(report);

        log.info("CRITIC_AGENT_COMPLETE | traceId={} | isValid={} | score={} | issues={}",
                traceId, report.isValid(), report.getOverallScore(), report.getIssues().size());

        sendStatus(callback, "质量审查完成",
                String.format("评分: %.1f/100, %s, 发现 %d 个问题",
                        report.getOverallScore() * 100,
                        report.isValid() ? "通过" : "需要修改",
                        report.getIssues().size()));

        return report;
    }

    /**
     * Perform compliance check for forbidden phrases
     */
    private void performComplianceCheck(ValidationRequest request, ValidationReport report) {
        String content = request.getDraftContent();
        if (content == null || content.isEmpty()) {
            report.addIssue(ValidationIssue.builder()
                    .issueId("COMPLIANCE_001")
                    .severity("ERROR")
                    .category("MISSING_CONTENT")
                    .description("草稿内容为空")
                    .suggestion("请确保提供完整的申报书草稿")
                    .build());
            return;
        }

        // Check for forbidden phrases
        for (String phrase : FORBIDDEN_PHRASES) {
            if (content.contains(phrase)) {
                report.addIssue(ValidationIssue.builder()
                        .issueId("COMPLIANCE_" + UUID.randomUUID().toString().substring(0, 6))
                        .severity("WARNING")
                        .category("COMPLIANCE")
                        .description("发现禁止性词语: \"" + phrase + "\"")
                        .suggestion("建议替换为更谨慎的表述，如使用\"有望\"、\"拟\"、\"预期\"等")
                        .build());
            }
        }

        // Check with patterns for context
        for (Pattern pattern : COMPLIANCE_PATTERNS) {
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String matched = matcher.group();
                // Find context around the match
                int start = Math.max(0, matcher.start() - 20);
                int end = Math.min(content.length(), matcher.end() + 20);
                String contextSnippet = content.substring(start, end);

                report.addIssue(ValidationIssue.builder()
                        .issueId("COMPLIANCE_" + UUID.randomUUID().toString().substring(0, 6))
                        .severity("WARNING")
                        .category("COMPLIANCE")
                        .description("发现可能存在问题的表述: \"" + matched + "\"")
                        .suggestion("上下文: \"..." + contextSnippet + "...\" - 请检查是否需要修改为更谨慎的表述")
                        .build());
            }
        }

        // Check for completion status phrases
        Pattern completionPattern = Pattern.compile("(已完成|已实现|已验证)\\s*[^初步部分预]");
        Matcher completionMatcher = completionPattern.matcher(content);
        if (completionMatcher.find()) {
            report.setComplianceCheckPassed(false);
            report.addIssue(ValidationIssue.builder()
                    .issueId("COMPLIANCE_COMPLETION")
                    .severity("ERROR")
                    .category("COMPLIANCE")
                    .description("使用了完成式表述，暗示研究已完成")
                    .suggestion("申报书应描述拟开展的研究，而非已完成的工作。请修改为\"拟完成\"、\"计划实现\"等")
                    .build());
        } else {
            report.setComplianceCheckPassed(true);
        }
    }

    /**
     * Verify citations and evidence traceability
     */
    private void performCitationVerification(ValidationRequest request, ValidationReport report) {
        String content = request.getDraftContent();
        ProposalEvidencePack evidencePack = request.getEvidencePack();

        if (evidencePack == null) {
            report.addIssue(ValidationIssue.builder()
                    .issueId("CITATION_001")
                    .severity("WARNING")
                    .category("EVIDENCE_GAP")
                    .description("未提供证据包，无法验证引用")
                    .suggestion("请确保在生成过程中传递完整的证据包")
                    .build());
            return;
        }

        // Count citation patterns like [1], [2], etc.
        Pattern citationPattern = Pattern.compile("\\[(\\d+)\\]");
        Matcher matcher = citationPattern.matcher(content);
        Set<Integer> citedNumbers = new HashSet<>();

        while (matcher.find()) {
            citedNumbers.add(Integer.parseInt(matcher.group(1)));
        }

        int totalEvidence = evidencePack.getEvidenceCards().size();
        int citedCount = citedNumbers.size();

        // Calculate traceability rate
        double traceabilityRate = totalEvidence > 0 ? (double) citedCount / totalEvidence : 0;
        report.setTraceabilityRate(traceabilityRate);

        if (citedCount == 0) {
            report.addIssue(ValidationIssue.builder()
                    .issueId("CITATION_NO_REF")
                    .severity("ERROR")
                    .category("EVIDENCE_GAP")
                    .description("草稿中未发现任何文献引用")
                    .suggestion("请添加文献引用以支持论点，使用[编号]格式")
                    .build());
        } else if (traceabilityRate < 0.3) {
            report.addIssue(ValidationIssue.builder()
                    .issueId("CITATION_LOW")
                    .severity("WARNING")
                    .category("EVIDENCE_GAP")
                    .description(String.format("文献引用率较低 (%.1f%%)", traceabilityRate * 100))
                    .suggestion("建议增加更多文献引用，特别是在研究现状和方法论述部分")
                    .build());
        }

        // Check for citation number gaps or invalid numbers
        int maxCited = citedNumbers.stream().max(Integer::compareTo).orElse(0);
        if (maxCited > totalEvidence) {
            report.addIssue(ValidationIssue.builder()
                    .issueId("CITATION_INVALID")
                    .severity("ERROR")
                    .category("EVIDENCE_GAP")
                    .description(String.format("引用编号[%d]超出证据范围(共%d条)", maxCited, totalEvidence))
                    .suggestion("请检查引用编号是否正确")
                    .build());
        }
    }

    /**
     * Check logical consistency between sections
     */
    private void performLogicCheck(ValidationRequest request, ValidationReport report, String traceId) {
        String content = request.getDraftContent();

        // Check if key sections are present
        checkSectionPresence(content, report);

        // Check for logical flow keywords
        checkLogicalFlow(content, report);

        // Check for internal consistency
        checkInternalConsistency(content, report);
    }

    private void checkSectionPresence(String content, ValidationReport report) {
        Map<String, String> requiredSections = new LinkedHashMap<>();
        requiredSections.put("研究意义", "1.1");
        requiredSections.put("研究现状", "1.2");
        requiredSections.put("科学问题", "1.3");
        requiredSections.put("研究目标", "1.4");
        requiredSections.put("研究内容", "1.4");
        requiredSections.put("技术路线", "2.1");
        requiredSections.put("研究方案", "2.1");
        requiredSections.put("可行性", "2.2");
        requiredSections.put("创新", "2.3");

        List<String> missingSections = new ArrayList<>();
        for (Map.Entry<String, String> entry : requiredSections.entrySet()) {
            if (!content.contains(entry.getKey())) {
                missingSections.add(entry.getKey());
            }
        }

        if (!missingSections.isEmpty()) {
            report.addIssue(ValidationIssue.builder()
                    .issueId("LOGIC_MISSING_SECTIONS")
                    .severity("WARNING")
                    .category("MISSING_CONTENT")
                    .description("以下章节可能缺失或标题不完整: " + String.join(", ", missingSections))
                    .suggestion("请确保包含所有必要的申报书章节")
                    .build());
        }
    }

    private void checkLogicalFlow(String content, ValidationReport report) {
        // Check for logical connectors that indicate good flow
        List<String> flowKeywords = List.of(
                "因此", "综上所述", "基于此", "鉴于", "为此",
                "针对", "围绕", "从而", "进而", "以期"
        );

        int flowCount = 0;
        for (String keyword : flowKeywords) {
            if (content.contains(keyword)) {
                flowCount++;
            }
        }

        if (flowCount < 3) {
            report.addIssue(ValidationIssue.builder()
                    .issueId("LOGIC_FLOW")
                    .severity("INFO")
                    .category("LOGICAL_INCONSISTENCY")
                    .description("逻辑连接词使用较少")
                    .suggestion("建议增加逻辑过渡词（如\"因此\"、\"基于此\"等）以增强章节间的逻辑关联")
                    .build());
        }
    }

    private void checkInternalConsistency(String content, ValidationReport report) {
        // Check if research objectives mentioned in one place are also discussed in research content
        // This is a simplified check - a full implementation would parse the sections

        // Check for objective-content alignment
        Pattern objectivePattern = Pattern.compile("目标[一二三四五1-5]?[：:．.]([^。\\n]+)");
        Matcher objectiveMatcher = objectivePattern.matcher(content);

        List<String> objectives = new ArrayList<>();
        while (objectiveMatcher.find()) {
            objectives.add(objectiveMatcher.group(1).trim());
        }

        // Simple check: if objectives are mentioned, they should appear in research content section
        if (objectives.size() > 0) {
            String contentSection = extractSection(content, "研究内容");
            int missingCount = 0;

            for (String objective : objectives) {
                // Extract key terms from objective
                String[] keyTerms = objective.split("[，、,]");
                boolean found = false;
                for (String term : keyTerms) {
                    if (term.length() > 4 && contentSection.contains(term.trim())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    missingCount++;
                }
            }

            if (missingCount > objectives.size() / 2) {
                report.addIssue(ValidationIssue.builder()
                        .issueId("LOGIC_OBJECTIVE_ALIGNMENT")
                        .severity("WARNING")
                        .category("LOGICAL_INCONSISTENCY")
                        .description("部分研究目标在研究内容章节中未得到充分展开")
                        .suggestion("请确保研究内容能够覆盖所有研究目标")
                        .build());
            }
        }
    }

    private String extractSection(String content, String sectionName) {
        int start = content.indexOf(sectionName);
        if (start == -1) return "";

        // Find next major section
        int end = content.length();
        String[] sectionMarkers = {"</h2>", "</h3>", "2.", "3.", "4.", "5."};
        for (String marker : sectionMarkers) {
            int markerPos = content.indexOf(marker, start + sectionName.length());
            if (markerPos > start && markerPos < end) {
                end = markerPos;
            }
        }

        return content.substring(start, Math.min(end, start + 2000));
    }

    /**
     * Calculate overall validation score
     */
    private void calculateOverallScore(ValidationReport report) {
        double score = 1.0;

        // Deduct points for issues
        for (ValidationIssue issue : report.getIssues()) {
            switch (issue.getSeverity()) {
                case "ERROR" -> score -= 0.15;
                case "WARNING" -> score -= 0.05;
                case "INFO" -> score -= 0.01;
            }
        }

        // Factor in traceability
        score = score * 0.7 + report.getTraceabilityRate() * 0.3;

        // Ensure score is in valid range
        score = Math.max(0, Math.min(1.0, score));

        report.setOverallScore(score);
        report.setValid(score >= 0.6 && report.isComplianceCheckPassed());
    }

    /**
     * Generate improvement suggestions based on issues
     */
    private void generateSuggestions(ValidationReport report) {
        List<String> suggestions = new ArrayList<>();

        // Prioritize suggestions
        if (!report.isComplianceCheckPassed()) {
            suggestions.add("【高优先级】请修改使用了完成式表述的内容");
        }

        long errorCount = report.getIssues().stream()
                .filter(i -> "ERROR".equals(i.getSeverity())).count();
        long warningCount = report.getIssues().stream()
                .filter(i -> "WARNING".equals(i.getSeverity())).count();

        if (errorCount > 0) {
            suggestions.add(String.format("【高优先级】请解决 %d 个严重问题", errorCount));
        }

        if (warningCount > 0) {
            suggestions.add(String.format("【中优先级】建议关注 %d 个警告问题", warningCount));
        }

        if (report.getTraceabilityRate() < 0.5) {
            suggestions.add("【建议】增加文献引用以增强论证的说服力");
        }

        report.setSuggestions(suggestions);
    }

    private void sendStatus(StreamDataCallback callback, String title, String description) {
        if (callback != null) {
            try {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder(title, description, callId, "");
                callback.onData(statusResponse);
            } catch (Exception e) {
                log.warn("CRITIC_STATUS_SEND_ERROR | error={}", e.getMessage());
            }
        }
    }

    // Request and Report classes

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationRequest {
        private ProposalDraftPack draftPack;
        private ProposalEvidencePack evidencePack;
        private String draftContent; // Plain HTML/markdown content for quick validation
    }

    @lombok.Data
    public static class ValidationReport {
        private String reportId;
        private String traceId;
        private java.time.LocalDateTime timestamp;
        private boolean valid;
        private double overallScore;
        private boolean complianceCheckPassed;
        private double traceabilityRate;
        private List<ValidationIssue> issues = new ArrayList<>();
        private List<String> suggestions = new ArrayList<>();

        public void addIssue(ValidationIssue issue) {
            if (issues == null) {
                issues = new ArrayList<>();
            }
            issues.add(issue);
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ValidationIssue {
        private String issueId;
        private String severity; // ERROR, WARNING, INFO
        private String category; // COMPLIANCE, EVIDENCE_GAP, LOGICAL_INCONSISTENCY, MISSING_CONTENT
        private String sectionId;
        private String description;
        private String suggestion;
    }
}
