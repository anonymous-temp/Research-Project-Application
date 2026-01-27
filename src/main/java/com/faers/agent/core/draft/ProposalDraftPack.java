package com.faers.agent.core.draft;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ProposalDraftPack - Core intermediate object for proposal draft
 *
 * This class represents the structured output from the LLM generation process.
 * It contains all sections of the proposal in a structured format, along with
 * the logical chain (scientific questions, hypotheses, innovations) that ensures
 * consistency across sections.
 *
 * Design principles:
 * 1. Structural completeness: All proposal sections in one object
 * 2. Logical coherence: Core logical chain linking all sections
 * 3. Evidence traceability: Each claim references evidence IDs
 * 4. Validation support: Can be verified for completeness and consistency
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposalDraftPack {

    /**
     * Unique identifier for this draft pack
     */
    private String draftId;

    /**
     * Reference to the evidence pack used for generation
     */
    private String evidencePackId;

    /**
     * Session/trace ID for correlation
     */
    private String traceId;

    /**
     * Topic/project name
     */
    private String topicName;

    /**
     * Timestamp when this draft was created
     */
    private LocalDateTime createdAt;

    /**
     * Core logical chain - the backbone of the proposal
     */
    @Builder.Default
    private LogicalChain logicalChain = new LogicalChain();

    /**
     * All proposal sections
     */
    @Builder.Default
    private ProposalSections sections = new ProposalSections();

    /**
     * Generated diagrams and charts
     */
    @Builder.Default
    private List<DiagramSpec> diagrams = new ArrayList<>();

    /**
     * Reference list with citations
     */
    @Builder.Default
    private List<Reference> references = new ArrayList<>();

    /**
     * Validation results
     */
    @Builder.Default
    private ValidationResult validationResult = new ValidationResult();

    /**
     * Generation metadata
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * LogicalChain - Core logical structure ensuring consistency
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogicalChain {
        /**
         * Core scientific questions to be addressed
         */
        @Builder.Default
        private List<ScientificQuestion> scientificQuestions = new ArrayList<>();

        /**
         * Research hypotheses
         */
        @Builder.Default
        private List<Hypothesis> hypotheses = new ArrayList<>();

        /**
         * Innovation points
         */
        @Builder.Default
        private List<InnovationPoint> innovationPoints = new ArrayList<>();

        /**
         * Research objectives
         */
        @Builder.Default
        private List<ResearchObjective> researchObjectives = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScientificQuestion {
        private String questionId;
        private String question;
        private String background;
        private List<String> relatedEvidenceIds;
        private String expectedOutcome;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Hypothesis {
        private String hypothesisId;
        private String statement;
        private String rationale;
        private List<String> relatedQuestionIds;
        private List<String> relatedEvidenceIds;
        private String verificationApproach;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InnovationPoint {
        private String innovationId;
        private String title;
        private String description;
        private String type; // "THEORETICAL", "METHODOLOGICAL", "TECHNICAL", "APPLICATION"
        private List<String> relatedEvidenceIds;
        private String differentiationFromExisting;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResearchObjective {
        private String objectiveId;
        private String objective;
        private List<String> relatedQuestionIds;
        private List<String> relatedHypothesisIds;
        private String measurableOutcome;
    }

    /**
     * ProposalSections - All sections of the proposal
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProposalSections {
        // Basic information
        private SectionContent projectTitle;
        private SectionContent abstractSection;
        private SectionContent keywords;
        private SectionContent englishKeywords;

        // Part 1: Basis and Content
        private SectionContent researchSignificance;
        private SectionContent researchStatus;
        private SectionContent keyScientificProblems;
        private SectionContent researchObjectives;
        private SectionContent researchContent;

        // Part 2: Research Plan and Feasibility
        private SectionContent technicalRoute;
        private SectionContent researchPlan;
        private SectionContent feasibilityAnalysis;

        // Part 3: Innovation
        private SectionContent ideaInnovation;
        private SectionContent technologyInnovation;

        // Part 4: Research Foundation
        private SectionContent researchBasis;
        private SectionContent workConditions;

        // Part 5: Schedule and Outcomes
        private SectionContent scheduleAndPlan;
        private SectionContent expectedOutcomes;

        // Part 6: Risk Analysis
        private SectionContent riskAnalysis;
    }

    /**
     * SectionContent - Content for a single section
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionContent {
        private String sectionId;
        private String title;
        private String htmlContent;
        private String markdownContent;
        private List<String> referencedEvidenceIds;
        private int wordCount;
        private String summary;
        @Builder.Default
        private List<SubSection> subSections = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubSection {
        private String subSectionId;
        private String title;
        private String content;
        private List<String> referencedEvidenceIds;
    }

    /**
     * DiagramSpec - Specification for diagrams/charts
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiagramSpec {
        private String diagramId;
        private String title;
        private String type; // "FLOWCHART", "GANTT", "MINDMAP", "SEQUENCE"
        private String mermaidCode;
        private String svgContent;
        private String imageUrl;
        private String placement; // Section where this diagram should appear
    }

    /**
     * Reference - Citation reference
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reference {
        private int referenceNumber;
        private String evidenceId;
        private String citation;
        private String doi;
        private String url;
    }

    /**
     * ValidationResult - Results of draft validation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        @Builder.Default
        private boolean isValid = false;

        @Builder.Default
        private double overallScore = 0.0;

        @Builder.Default
        private List<ValidationIssue> issues = new ArrayList<>();

        @Builder.Default
        private Map<String, Double> sectionScores = new HashMap<>();

        @Builder.Default
        private LogicalConsistencyCheck logicalConsistency = new LogicalConsistencyCheck();

        @Builder.Default
        private EvidenceTraceabilityCheck evidenceTraceability = new EvidenceTraceabilityCheck();

        @Builder.Default
        private ComplianceCheck complianceCheck = new ComplianceCheck();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationIssue {
        private String issueId;
        private String severity; // "ERROR", "WARNING", "INFO"
        private String category; // "MISSING_CONTENT", "LOGICAL_INCONSISTENCY", "EVIDENCE_GAP", "COMPLIANCE"
        private String sectionId;
        private String description;
        private String suggestion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogicalConsistencyCheck {
        @Builder.Default
        private boolean questionsAddressed = false;
        @Builder.Default
        private boolean hypothesesSupported = false;
        @Builder.Default
        private boolean objectivesAligned = false;
        @Builder.Default
        private boolean methodsMatchObjectives = false;
        @Builder.Default
        private List<String> inconsistencies = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceTraceabilityCheck {
        @Builder.Default
        private int totalClaims = 0;
        @Builder.Default
        private int claimsWithEvidence = 0;
        @Builder.Default
        private double traceabilityRate = 0.0;
        @Builder.Default
        private List<String> unsubstantiatedClaims = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceCheck {
        @Builder.Default
        private boolean noCompletedStatus = true; // No "已完成", "已验证" etc.
        @Builder.Default
        private boolean noAbsoluteClaims = true; // No "首次", "首个", "唯一" etc.
        @Builder.Default
        private boolean wordCountCompliant = true;
        @Builder.Default
        private List<String> complianceViolations = new ArrayList<>();
    }

    /**
     * Assemble the full proposal as HTML
     */
    public String assembleFullProposalHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>").append(topicName).append("</title>\n");
        sb.append("<style>\n");
        sb.append(getDefaultStyles());
        sb.append("</style>\n</head>\n<body>\n");

        // Add sections in order
        appendSection(sb, sections.getProjectTitle());
        appendSection(sb, sections.getAbstractSection());
        appendSection(sb, sections.getKeywords());
        appendSection(sb, sections.getResearchSignificance());
        appendSection(sb, sections.getResearchStatus());
        appendSection(sb, sections.getKeyScientificProblems());
        appendSection(sb, sections.getResearchObjectives());
        appendSection(sb, sections.getResearchContent());
        appendSection(sb, sections.getTechnicalRoute());
        appendSection(sb, sections.getResearchPlan());
        appendSection(sb, sections.getFeasibilityAnalysis());
        appendSection(sb, sections.getIdeaInnovation());
        appendSection(sb, sections.getTechnologyInnovation());
        appendSection(sb, sections.getResearchBasis());
        appendSection(sb, sections.getWorkConditions());
        appendSection(sb, sections.getScheduleAndPlan());
        appendSection(sb, sections.getExpectedOutcomes());
        appendSection(sb, sections.getRiskAnalysis());

        // Add references
        if (!references.isEmpty()) {
            sb.append("<h2>参考文献</h2>\n<div class=\"references\">\n");
            for (Reference ref : references) {
                sb.append("<p>[").append(ref.getReferenceNumber()).append("] ")
                        .append(ref.getCitation()).append("</p>\n");
            }
            sb.append("</div>\n");
        }

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, SectionContent section) {
        if (section != null && section.getHtmlContent() != null) {
            sb.append(section.getHtmlContent()).append("\n");
        }
    }

    private String getDefaultStyles() {
        return """
            body { font-family: 'SimSun', serif; font-size: 12pt; line-height: 1.5; margin: 2cm; }
            h1 { font-family: 'SimHei', sans-serif; font-size: 22pt; text-align: center; margin-top: 24pt; margin-bottom: 18pt; }
            h2 { font-family: 'SimHei', sans-serif; font-size: 16pt; margin-top: 18pt; margin-bottom: 12pt; }
            h3 { font-family: 'SimHei', sans-serif; font-size: 14pt; margin-top: 12pt; margin-bottom: 10pt; }
            h4 { font-family: 'SimHei', sans-serif; font-size: 12pt; margin-top: 10pt; margin-bottom: 8pt; }
            p { text-indent: 2em; margin-bottom: 6pt; }
            table { border-collapse: collapse; width: 100%; margin: 12pt 0; }
            table th, table td { border: 1px solid #000; padding: 6pt; }
            .references p { text-indent: 0; }
            """;
    }

    /**
     * Get total word count of the proposal
     */
    public int getTotalWordCount() {
        int total = 0;
        ProposalSections s = sections;
        total += getWordCount(s.getAbstractSection());
        total += getWordCount(s.getResearchSignificance());
        total += getWordCount(s.getResearchStatus());
        total += getWordCount(s.getKeyScientificProblems());
        total += getWordCount(s.getResearchObjectives());
        total += getWordCount(s.getResearchContent());
        total += getWordCount(s.getTechnicalRoute());
        total += getWordCount(s.getResearchPlan());
        total += getWordCount(s.getFeasibilityAnalysis());
        total += getWordCount(s.getIdeaInnovation());
        total += getWordCount(s.getTechnologyInnovation());
        total += getWordCount(s.getResearchBasis());
        total += getWordCount(s.getWorkConditions());
        total += getWordCount(s.getScheduleAndPlan());
        total += getWordCount(s.getExpectedOutcomes());
        total += getWordCount(s.getRiskAnalysis());
        return total;
    }

    private int getWordCount(SectionContent section) {
        return section != null ? section.getWordCount() : 0;
    }
}
