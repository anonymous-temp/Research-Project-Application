package com.faers.agent.core.evidence;

import cn.hutool.json.JSONObject;
import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.core.evidence.ProposalEvidencePack.EvidenceCard;
import com.faers.agent.core.evidence.ProposalEvidencePack.EvidenceStats;
import com.faers.agent.core.evidence.ProposalEvidencePack.PreviousProposalDigest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EvidencePackBuilder - Service to build standardized EvidencePack from various sources
 *
 * This service is responsible for:
 * 1. Collecting evidence from ES search results
 * 2. Collecting evidence from web search results
 * 3. Digesting previous proposals
 * 4. Assigning credibility hints based on source
 * 5. Computing statistics
 */
@Service
public class EvidencePackBuilder {

    private static final Logger log = LoggerFactory.getLogger(EvidencePackBuilder.class);

    @Autowired
    private ObjectMapper objectMapper;

    // Credibility mappings for different sources
    private static final Map<String, String> SOURCE_CREDIBILITY = Map.of(
            "PubMed", "HIGH",
            "CNKI", "HIGH",
            "WanFang", "MEDIUM",
            "Guideline", "HIGH",
            "ClinicalTrial", "HIGH",
            "Bocha", "MEDIUM",
            "Bing", "LOW",
            "DuckDuckGo", "LOW",
            "Fallback", "LOW"
    );

    // Journal impact factor thresholds for credibility
    private static final double HIGH_IMPACT_THRESHOLD = 5.0;
    private static final double MEDIUM_IMPACT_THRESHOLD = 2.0;

    /**
     * Build a complete EvidencePack from analysis context
     *
     * @param context The analysis context containing all collected data
     * @param traceId Trace ID for logging
     * @return Complete EvidencePack ready for LLM consumption
     */
    public ProposalEvidencePack buildFromContext(AnalysisContext context, String traceId) {
        log.info("EVIDENCE_PACK_BUILD_START | traceId={}", traceId);

        String topicName = getTopicNameFromContext(context);

        ProposalEvidencePack pack = ProposalEvidencePack.builder()
                .packId(UUID.randomUUID().toString())
                .traceId(traceId)
                .topicName(topicName)
                .createdAt(LocalDateTime.now())
                .queryParams(context.getQueryParams() != null ? new HashMap<>(context.getQueryParams()) : new HashMap<>())
                .build();

        // 1. Process ES literature results
        if (context.getCleanJson() != null && !context.getCleanJson().isEmpty()) {
            processESResults(pack, context.getCleanJson(), traceId);
        }

        // 2. Process web search results (stored in rawJson or dedicated field)
        if (context.getRawJson() != null && !context.getRawJson().isEmpty()) {
            processWebSearchResults(pack, context.getRawJson(), context.getWebSearchSource(), traceId);
        }

        // 3. Process previous proposal if available
        if (context.getFinalPaper() != null && !context.getFinalPaper().isEmpty()) {
            processPreviousProposal(pack, context.getFinalPaper(), traceId);
        }

        log.info("EVIDENCE_PACK_BUILD_COMPLETE | traceId={} | totalEvidence={} | stats={}",
                traceId, pack.getEvidenceCards().size(), pack.getEvidenceStats());

        return pack;
    }

    /**
     * Process ES literature search results into evidence cards
     */
    private void processESResults(ProposalEvidencePack pack, String cleanJson, String traceId) {
        try {
            JsonNode root = objectMapper.readTree(cleanJson);
            JsonNode hits;

            // Handle different JSON structures
            if (root.has("hits")) {
                hits = root.get("hits").has("hits") ? root.get("hits").get("hits") : root.get("hits");
            } else if (root.isArray()) {
                hits = root;
            } else {
                log.warn("ES_RESULTS_PARSE_UNKNOWN_STRUCTURE | traceId={}", traceId);
                return;
            }

            int count = 0;
            for (JsonNode hit : hits) {
                JsonNode source = hit.has("_source") ? hit.get("_source") : hit;

                EvidenceCard card = EvidenceCard.builder()
                        .sourceType("ES_LITERATURE")
                        .sourceName(extractString(source, "database", "PubMed"))
                        .title(extractString(source, "title", ""))
                        .authors(extractString(source, "authors", ""))
                        .date(extractString(source, "publish_date", extractString(source, "year", "")))
                        .url(extractString(source, "doi", extractString(source, "url", "")))
                        .content(extractString(source, "abstract", extractString(source, "content", "")))
                        .keyFindings(extractString(source, "conclusions", ""))
                        .tags(extractTags(source))
                        .build();

                // Assign credibility based on journal and source
                String journal = extractString(source, "journal", "");
                double impactFactor = extractDouble(source, "impact_factor", 0.0);
                card.setCredibilityHint(determineCredibility(card.getSourceName(), journal, impactFactor));

                // Calculate relevance score if available
                if (hit.has("_score")) {
                    double score = hit.get("_score").asDouble();
                    card.setRelevanceScore(normalizeScore(score));
                } else {
                    card.setRelevanceScore(0.5); // Default relevance
                }

                pack.addEvidenceCard(card);
                count++;

                // Limit to reasonable number
                if (count >= 50) {
                    log.info("ES_RESULTS_LIMIT_REACHED | traceId={} | limit=50", traceId);
                    break;
                }
            }

            log.info("ES_RESULTS_PROCESSED | traceId={} | count={}", traceId, count);

        } catch (Exception e) {
            log.error("ES_RESULTS_PARSE_ERROR | traceId={} | error={}", traceId, e.getMessage(), e);
        }
    }

    /**
     * Process web search results into evidence cards
     */
    private void processWebSearchResults(ProposalEvidencePack pack, String webResultsJson, String webSource, String traceId) {
        try {
            JsonNode results = objectMapper.readTree(webResultsJson);

            if (!results.isArray()) {
                log.warn("WEB_RESULTS_NOT_ARRAY | traceId={}", traceId);
                return;
            }

            int count = 0;
            for (JsonNode result : results) {
                String source = webSource != null ? webSource : extractString(result, "source", "Web");

                EvidenceCard card = EvidenceCard.builder()
                        .sourceType("WEB_SEARCH")
                        .sourceName(source)
                        .title(extractString(result, "name", extractString(result, "title", "")))
                        .url(extractString(result, "url", extractString(result, "link", "")))
                        .content(extractString(result, "snippet", extractString(result, "description", "")))
                        .credibilityHint(SOURCE_CREDIBILITY.getOrDefault(source, "LOW"))
                        .relevanceScore(0.4) // Web results generally lower relevance
                        .build();

                // Boost credibility for known authoritative domains
                String url = card.getUrl();
                if (url != null) {
                    if (url.contains("gov.cn") || url.contains(".gov/")) {
                        card.setCredibilityHint("HIGH");
                        card.setRelevanceScore(0.7);
                    } else if (url.contains("edu.cn") || url.contains(".edu/")) {
                        card.setCredibilityHint("MEDIUM");
                        card.setRelevanceScore(0.6);
                    } else if (url.contains("who.int") || url.contains("nih.gov")) {
                        card.setCredibilityHint("HIGH");
                        card.setRelevanceScore(0.8);
                    }
                }

                // Extract tags from title
                List<String> tags = new ArrayList<>();
                tags.add("web_search");
                card.setTags(tags);

                pack.addEvidenceCard(card);
                count++;

                // Limit web results
                if (count >= 20) {
                    break;
                }
            }

            log.info("WEB_RESULTS_PROCESSED | traceId={} | count={}", traceId, count);

        } catch (Exception e) {
            log.error("WEB_RESULTS_PARSE_ERROR | traceId={} | error={}", traceId, e.getMessage(), e);
        }
    }

    /**
     * Process previous proposal to extract digest
     */
    private void processPreviousProposal(ProposalEvidencePack pack, String previousProposal, String traceId) {
        try {
            PreviousProposalDigest digest = PreviousProposalDigest.builder()
                    .originalProposalId(UUID.randomUUID().toString())
                    .build();

            // Extract objectives (look for patterns like "研究目标" section)
            List<String> objectives = extractSectionPoints(previousProposal, "研究目标", "研究内容");
            digest.setMainObjectives(objectives);

            // Extract scientific problems
            List<String> problems = extractSectionPoints(previousProposal, "关键科学问题", "研究目标");
            digest.setScientificProblems(problems);

            // Extract innovation points
            List<String> innovations = extractSectionPoints(previousProposal, "创新", "研究基础");
            digest.setInnovationPoints(innovations);

            // Extract keywords
            List<String> keywords = extractKeywords(previousProposal);
            digest.setKeywords(keywords);

            // Create summary (first 500 chars of abstract or beginning)
            String summary = extractAbstract(previousProposal);
            digest.setSummary(summary);

            pack.setPreviousProposalDigest(digest);

            log.info("PREVIOUS_PROPOSAL_PROCESSED | traceId={} | objectives={} | problems={} | innovations={}",
                    traceId, objectives.size(), problems.size(), innovations.size());

        } catch (Exception e) {
            log.error("PREVIOUS_PROPOSAL_PARSE_ERROR | traceId={} | error={}", traceId, e.getMessage(), e);
        }
    }

    // Helper methods

    private String getTopicNameFromContext(AnalysisContext context) {
        if (context.getQueryParams() != null && context.getQueryParams().containsKey("topicName")) {
            return context.getQueryParams().get("topicName").toString();
        }
        return "Unknown Topic";
    }

    private String extractString(JsonNode node, String field, String defaultValue) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return defaultValue;
    }

    private double extractDouble(JsonNode node, String field, double defaultValue) {
        if (node != null && node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asDouble(defaultValue);
        }
        return defaultValue;
    }

    private List<String> extractTags(JsonNode source) {
        List<String> tags = new ArrayList<>();

        // Add study type as tag
        String studyType = extractString(source, "study_type", "");
        if (!studyType.isEmpty()) {
            tags.add(studyType.toLowerCase().replace(" ", "_"));
        }

        // Add keywords as tags
        if (source.has("keywords") && source.get("keywords").isArray()) {
            for (JsonNode keyword : source.get("keywords")) {
                tags.add(keyword.asText().toLowerCase());
            }
        }

        // Add mesh terms as tags
        if (source.has("mesh_terms") && source.get("mesh_terms").isArray()) {
            for (JsonNode mesh : source.get("mesh_terms")) {
                if (tags.size() < 10) {
                    tags.add(mesh.asText().toLowerCase());
                }
            }
        }

        return tags;
    }

    private String determineCredibility(String sourceName, String journal, double impactFactor) {
        // First check source-level credibility
        String baseCredibility = SOURCE_CREDIBILITY.getOrDefault(sourceName, "MEDIUM");

        // Upgrade based on impact factor
        if (impactFactor >= HIGH_IMPACT_THRESHOLD) {
            return "HIGH";
        } else if (impactFactor >= MEDIUM_IMPACT_THRESHOLD) {
            return baseCredibility.equals("LOW") ? "MEDIUM" : baseCredibility;
        }

        // Check for known high-impact journals
        if (journal != null) {
            String journalLower = journal.toLowerCase();
            if (journalLower.contains("nature") || journalLower.contains("science") ||
                    journalLower.contains("cell") || journalLower.contains("lancet") ||
                    journalLower.contains("nejm") || journalLower.contains("jama")) {
                return "HIGH";
            }
        }

        return baseCredibility;
    }

    private double normalizeScore(double esScore) {
        // Normalize ES score to 0-1 range
        // Typical ES scores range from 0 to ~20
        return Math.min(1.0, esScore / 20.0);
    }

    private List<String> extractSectionPoints(String text, String startMarker, String endMarker) {
        List<String> points = new ArrayList<>();

        try {
            // Find section content
            int startIndex = text.indexOf(startMarker);
            if (startIndex == -1) return points;

            int endIndex = endMarker != null ? text.indexOf(endMarker, startIndex) : -1;
            if (endIndex == -1) endIndex = Math.min(startIndex + 2000, text.length());

            String sectionContent = text.substring(startIndex, endIndex);

            // Extract numbered or bulleted points
            Pattern pattern = Pattern.compile("(?:^|\\n)\\s*[（(]?[1-9①②③④⑤⑥⑦⑧⑨][）)]?[.、]?\\s*(.+?)(?=\\n|$)", Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(sectionContent);

            while (matcher.find() && points.size() < 10) {
                String point = matcher.group(1).trim();
                if (point.length() > 10 && point.length() < 500) {
                    points.add(point);
                }
            }

        } catch (Exception e) {
            log.warn("EXTRACT_SECTION_POINTS_ERROR | marker={} | error={}", startMarker, e.getMessage());
        }

        return points;
    }

    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();

        try {
            // Look for keywords section
            Pattern pattern = Pattern.compile("关键词[：:](.*?)(?=\\n|英文|$)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);

            if (matcher.find()) {
                String keywordString = matcher.group(1);
                String[] parts = keywordString.split("[;；,，、]");
                for (String part : parts) {
                    String keyword = part.trim();
                    if (!keyword.isEmpty() && keyword.length() < 50) {
                        keywords.add(keyword);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("EXTRACT_KEYWORDS_ERROR | error={}", e.getMessage());
        }

        return keywords;
    }

    private String extractAbstract(String text) {
        try {
            // Look for abstract section
            int abstractStart = text.indexOf("摘要");
            if (abstractStart == -1) {
                abstractStart = text.indexOf("Abstract");
            }
            if (abstractStart == -1) {
                // Just use first 500 chars
                return text.substring(0, Math.min(500, text.length()));
            }

            int abstractEnd = Math.min(abstractStart + 800, text.length());
            return text.substring(abstractStart, abstractEnd);

        } catch (Exception e) {
            log.warn("EXTRACT_ABSTRACT_ERROR | error={}", e.getMessage());
            return "";
        }
    }
}
