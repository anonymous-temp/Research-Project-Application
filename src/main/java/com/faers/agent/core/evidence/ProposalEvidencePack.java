package com.faers.agent.core.evidence;

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
 * ProposalEvidencePack - Core intermediate object for evidence collection
 *
 * This class encapsulates all evidence collected from various sources (ES, web search,
 * previous proposals) into a standardized, immutable package that can be passed to the
 * LLM for holistic proposal generation.
 *
 * Design principles:
 * 1. Standardization: All evidence follows a uniform structure
 * 2. Immutability: Once built, the evidence pack should not be modified
 * 3. Traceability: Each piece of evidence has a unique ID and source attribution
 * 4. Statistics: Provides summary statistics for validation and debugging
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProposalEvidencePack {

    /**
     * Unique identifier for this evidence pack
     */
    private String packId;

    /**
     * Session/trace ID for correlation
     */
    private String traceId;

    /**
     * Topic name for this proposal
     */
    private String topicName;

    /**
     * Timestamp when this pack was created
     */
    private LocalDateTime createdAt;

    /**
     * List of evidence cards from all sources
     */
    @Builder.Default
    private List<EvidenceCard> evidenceCards = new ArrayList<>();

    /**
     * Summary statistics about the evidence
     */
    @Builder.Default
    private EvidenceStats evidenceStats = new EvidenceStats();

    /**
     * Digest of previous proposal (if any)
     */
    private PreviousProposalDigest previousProposalDigest;

    /**
     * Query parameters used to collect this evidence
     */
    @Builder.Default
    private Map<String, Object> queryParams = new HashMap<>();

    /**
     * EvidenceCard - Individual piece of evidence with metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceCard {
        /**
         * Unique evidence ID for citation (e.g., "E001", "E002")
         */
        private String evidenceId;

        /**
         * Source type: "ES_LITERATURE", "WEB_SEARCH", "GUIDELINE", "CLINICAL_TRIAL"
         */
        private String sourceType;

        /**
         * Source name (e.g., "PubMed", "Bocha", "CNKI")
         */
        private String sourceName;

        /**
         * Title of the evidence document
         */
        private String title;

        /**
         * Authors (if applicable)
         */
        private String authors;

        /**
         * Publication date or retrieval date
         */
        private String date;

        /**
         * URL or DOI for reference
         */
        private String url;

        /**
         * Credibility hint: "HIGH", "MEDIUM", "LOW"
         * Based on source reputation, journal impact factor, guideline level, etc.
         */
        private String credibilityHint;

        /**
         * Relevance score (0.0 to 1.0)
         */
        private Double relevanceScore;

        /**
         * Main content/abstract of the evidence
         */
        private String content;

        /**
         * Key findings or conclusions
         */
        private String keyFindings;

        /**
         * Tags for categorization (e.g., "mechanism", "clinical_trial", "meta_analysis")
         */
        @Builder.Default
        private List<String> tags = new ArrayList<>();

        /**
         * Additional metadata
         */
        @Builder.Default
        private Map<String, Object> metadata = new HashMap<>();
    }

    /**
     * EvidenceStats - Summary statistics about collected evidence
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceStats {
        /**
         * Total number of evidence cards
         */
        @Builder.Default
        private int totalCount = 0;

        /**
         * Count by source type
         */
        @Builder.Default
        private Map<String, Integer> countBySourceType = new HashMap<>();

        /**
         * Count by credibility level
         */
        @Builder.Default
        private Map<String, Integer> countByCredibility = new HashMap<>();

        /**
         * Date range of evidence
         */
        private String dateRangeStart;
        private String dateRangeEnd;

        /**
         * Top keywords/themes identified
         */
        @Builder.Default
        private List<String> topKeywords = new ArrayList<>();

        /**
         * Average relevance score
         */
        @Builder.Default
        private double averageRelevanceScore = 0.0;

        /**
         * Whether the evidence pack meets minimum requirements
         */
        @Builder.Default
        private boolean meetsMinimumRequirements = false;

        /**
         * Validation messages
         */
        @Builder.Default
        private List<String> validationMessages = new ArrayList<>();
    }

    /**
     * PreviousProposalDigest - Summarized version of a previous proposal
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviousProposalDigest {
        /**
         * Original proposal ID
         */
        private String originalProposalId;

        /**
         * Main research objectives from previous proposal
         */
        @Builder.Default
        private List<String> mainObjectives = new ArrayList<>();

        /**
         * Key scientific problems identified
         */
        @Builder.Default
        private List<String> scientificProblems = new ArrayList<>();

        /**
         * Research methods used
         */
        @Builder.Default
        private List<String> researchMethods = new ArrayList<>();

        /**
         * Innovation points claimed
         */
        @Builder.Default
        private List<String> innovationPoints = new ArrayList<>();

        /**
         * Keywords from previous proposal
         */
        @Builder.Default
        private List<String> keywords = new ArrayList<>();

        /**
         * Summary of the previous proposal
         */
        private String summary;

        /**
         * Reviewer feedback (if available)
         */
        private String reviewerFeedback;

        /**
         * Suggested improvements
         */
        @Builder.Default
        private List<String> suggestedImprovements = new ArrayList<>();
    }

    /**
     * Add an evidence card to the pack
     */
    public void addEvidenceCard(EvidenceCard card) {
        if (evidenceCards == null) {
            evidenceCards = new ArrayList<>();
        }
        // Assign evidence ID if not present
        if (card.getEvidenceId() == null || card.getEvidenceId().isEmpty()) {
            card.setEvidenceId("E" + String.format("%03d", evidenceCards.size() + 1));
        }
        evidenceCards.add(card);
        updateStats();
    }

    /**
     * Update statistics after adding evidence
     */
    private void updateStats() {
        if (evidenceStats == null) {
            evidenceStats = new EvidenceStats();
        }

        evidenceStats.setTotalCount(evidenceCards.size());

        // Count by source type
        Map<String, Integer> bySource = new HashMap<>();
        Map<String, Integer> byCredibility = new HashMap<>();
        double totalRelevance = 0.0;

        for (EvidenceCard card : evidenceCards) {
            // By source type
            String sourceType = card.getSourceType() != null ? card.getSourceType() : "UNKNOWN";
            bySource.put(sourceType, bySource.getOrDefault(sourceType, 0) + 1);

            // By credibility
            String credibility = card.getCredibilityHint() != null ? card.getCredibilityHint() : "UNKNOWN";
            byCredibility.put(credibility, byCredibility.getOrDefault(credibility, 0) + 1);

            // Relevance score
            if (card.getRelevanceScore() != null) {
                totalRelevance += card.getRelevanceScore();
            }
        }

        evidenceStats.setCountBySourceType(bySource);
        evidenceStats.setCountByCredibility(byCredibility);

        if (!evidenceCards.isEmpty()) {
            evidenceStats.setAverageRelevanceScore(totalRelevance / evidenceCards.size());
        }

        // Check minimum requirements
        evidenceStats.setMeetsMinimumRequirements(evidenceCards.size() >= 5);

        // Add validation messages
        List<String> messages = new ArrayList<>();
        if (evidenceCards.size() < 5) {
            messages.add("Warning: Less than 5 evidence cards collected");
        }
        if (bySource.getOrDefault("ES_LITERATURE", 0) < 3) {
            messages.add("Warning: Less than 3 literature sources");
        }
        evidenceStats.setValidationMessages(messages);
    }

    /**
     * Get evidence cards filtered by source type
     */
    public List<EvidenceCard> getCardsBySourceType(String sourceType) {
        return evidenceCards.stream()
                .filter(card -> sourceType.equals(card.getSourceType()))
                .toList();
    }

    /**
     * Get evidence cards filtered by credibility
     */
    public List<EvidenceCard> getCardsByCredibility(String credibility) {
        return evidenceCards.stream()
                .filter(card -> credibility.equals(card.getCredibilityHint()))
                .toList();
    }

    /**
     * Convert to JSON-friendly format for LLM prompt
     */
    public String toPromptFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Evidence Pack Summary ===\n");
        sb.append("Topic: ").append(topicName).append("\n");
        sb.append("Total Evidence: ").append(evidenceStats.getTotalCount()).append("\n");
        sb.append("Sources: ").append(evidenceStats.getCountBySourceType()).append("\n\n");

        sb.append("=== Evidence Cards ===\n");
        for (EvidenceCard card : evidenceCards) {
            sb.append("[").append(card.getEvidenceId()).append("] ");
            sb.append(card.getTitle()).append("\n");
            sb.append("  Source: ").append(card.getSourceType()).append(" / ").append(card.getSourceName()).append("\n");
            sb.append("  Credibility: ").append(card.getCredibilityHint()).append("\n");
            sb.append("  Content: ").append(truncate(card.getContent(), 500)).append("\n");
            if (card.getKeyFindings() != null && !card.getKeyFindings().isEmpty()) {
                sb.append("  Key Findings: ").append(card.getKeyFindings()).append("\n");
            }
            sb.append("\n");
        }

        if (previousProposalDigest != null) {
            sb.append("=== Previous Proposal Summary ===\n");
            sb.append(previousProposalDigest.getSummary()).append("\n");
            if (previousProposalDigest.getReviewerFeedback() != null) {
                sb.append("Reviewer Feedback: ").append(previousProposalDigest.getReviewerFeedback()).append("\n");
            }
        }

        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
