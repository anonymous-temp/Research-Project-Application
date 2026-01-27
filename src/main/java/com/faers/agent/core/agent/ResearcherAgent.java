package com.faers.agent.core.agent;

import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.agent.impl.TopicESQueryAgent;
import com.faers.agent.core.evidence.EvidencePackBuilder;
import com.faers.agent.core.evidence.ProposalEvidencePack;
import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.tools.BochaWebSearchTool;
import com.faers.agent.tools.FallbackWebSearchTool;
import cn.hutool.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * ResearcherAgent - Evidence Collection Specialist
 *
 * Responsible for:
 * 1. Searching literature databases (ES)
 * 2. Searching web resources
 * 3. Integrating results into a standardized EvidencePack
 * 4. Assigning credibility hints to evidence
 *
 * This agent is the first in the pipeline and produces the EvidencePack
 * that all subsequent agents will use.
 */
@Service
public class ResearcherAgent implements ProposalAgent<ResearcherAgent.ResearchRequest, ProposalEvidencePack> {

    private static final Logger log = LoggerFactory.getLogger(ResearcherAgent.class);

    @Autowired
    private TopicESQueryAgent topicESQueryAgent;

    @Autowired
    private EvidencePackBuilder evidencePackBuilder;

    @Value("${search-api.bo-cha.key:}")
    private String bochaApiKey;

    @Override
    public String getAgentName() {
        return "ResearcherAgent";
    }

    @Override
    public String getAgentDescription() {
        return "Evidence collection specialist: searches databases and web, builds standardized EvidencePack";
    }

    @Override
    public ProposalEvidencePack execute(ResearchRequest request, AnalysisContext context,
                                         StreamDataCallback callback, String traceId) throws Exception {
        log.info("RESEARCHER_AGENT_START | traceId={} | topic={}", traceId, request.getTopicName());

        // Notify start
        sendStatus(callback, "开始收集研究证据...", "正在搜索文献数据库和网络资源");

        // Step 1: Search literature databases (ES)
        sendStatus(callback, "正在搜索文献数据库", "检索相关学术文献");
        searchLiterature(request, context, callback, traceId);

        // Step 2: Search web resources
        sendStatus(callback, "正在搜索网络资源", "检索相关网页和政策文件");
        searchWeb(request, context, callback, traceId);

        // Step 3: Build EvidencePack
        sendStatus(callback, "正在整合搜索结果", "构建标准化证据包");
        ProposalEvidencePack evidencePack = evidencePackBuilder.buildFromContext(context, traceId);

        // Step 4: Validate evidence pack
        validateEvidencePack(evidencePack, callback, traceId);

        log.info("RESEARCHER_AGENT_COMPLETE | traceId={} | evidenceCount={} | stats={}",
                traceId, evidencePack.getEvidenceCards().size(), evidencePack.getEvidenceStats());

        sendStatus(callback, "证据收集完成",
                String.format("共收集 %d 条证据", evidencePack.getEvidenceCards().size()));

        return evidencePack;
    }

    /**
     * Search literature databases
     */
    private void searchLiterature(ResearchRequest request, AnalysisContext context,
                                   StreamDataCallback callback, String traceId) {
        try {
            // Set topic name in query params
            Map<String, Object> params = context.getQueryParams();
            if (params == null) {
                params = new java.util.HashMap<>();
                context.setQueryParams(params);
            }
            params.put("topicName", request.getTopicName());

            // Execute ES query
            topicESQueryAgent.process(context, null, null, null, callback);

            log.info("RESEARCHER_ES_SEARCH_COMPLETE | traceId={} | hasResults={}",
                    traceId, context.getCleanJson() != null && !context.getCleanJson().isEmpty());

        } catch (Exception e) {
            log.error("RESEARCHER_ES_SEARCH_ERROR | traceId={} | error={}", traceId, e.getMessage(), e);
            // Don't fail completely, continue with web search
        }
    }

    /**
     * Search web resources
     */
    private void searchWeb(ResearchRequest request, AnalysisContext context,
                            StreamDataCallback callback, String traceId) {
        try {
            List<JSONObject> webResults = null;
            String webSearchSource = "none";

            // Try Bocha first if API key is available
            if (bochaApiKey != null && !bochaApiKey.isEmpty() && !bochaApiKey.startsWith("${")) {
                try {
                    BochaWebSearchTool bochaWebSearchTool = new BochaWebSearchTool(bochaApiKey);
                    webResults = bochaWebSearchTool.searchWeb(request.getTopicName(), callback);
                    if (webResults != null && !webResults.isEmpty()) {
                        webSearchSource = "bocha";
                    }
                } catch (Exception e) {
                    log.warn("RESEARCHER_BOCHA_SEARCH_FAILED | traceId={} | error={}", traceId, e.getMessage());
                }
            }

            // Fallback to free search if needed
            if (webResults == null || webResults.isEmpty()) {
                try {
                    FallbackWebSearchTool fallbackTool = new FallbackWebSearchTool();
                    webResults = fallbackTool.searchWeb(request.getTopicName(), callback);
                    if (webResults != null && !webResults.isEmpty()) {
                        webSearchSource = webResults.get(0).getStr("source", "fallback");
                    }
                } catch (Exception e) {
                    log.warn("RESEARCHER_FALLBACK_SEARCH_FAILED | traceId={} | error={}", traceId, e.getMessage());
                }
            }

            // Store results in context
            if (webResults != null && !webResults.isEmpty()) {
                context.setRawJson(webResults.toString());
                context.setWebSearchSource(webSearchSource);
            }

            log.info("RESEARCHER_WEB_SEARCH_COMPLETE | traceId={} | source={} | count={}",
                    traceId, webSearchSource, webResults != null ? webResults.size() : 0);

        } catch (Exception e) {
            log.error("RESEARCHER_WEB_SEARCH_ERROR | traceId={} | error={}", traceId, e.getMessage(), e);
        }
    }

    /**
     * Validate the evidence pack
     */
    private void validateEvidencePack(ProposalEvidencePack pack, StreamDataCallback callback, String traceId) {
        ProposalEvidencePack.EvidenceStats stats = pack.getEvidenceStats();

        if (!stats.isMeetsMinimumRequirements()) {
            log.warn("RESEARCHER_EVIDENCE_INSUFFICIENT | traceId={} | count={} | messages={}",
                    traceId, stats.getTotalCount(), stats.getValidationMessages());

            sendStatus(callback, "⚠️ 证据收集警告",
                    String.format("收集到的证据数量较少（%d条），可能影响报告质量", stats.getTotalCount()));
        }

        // Log validation messages
        for (String message : stats.getValidationMessages()) {
            log.info("RESEARCHER_VALIDATION | traceId={} | message={}", traceId, message);
        }
    }

    /**
     * Send status update to callback
     */
    private void sendStatus(StreamDataCallback callback, String title, String description) {
        if (callback != null) {
            try {
                String callId = java.util.UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder(title, description, callId, "");
                callback.onData(statusResponse);
            } catch (Exception e) {
                log.warn("RESEARCHER_STATUS_SEND_ERROR | error={}", e.getMessage());
            }
        }
    }

    /**
     * Research request input class
     */
    public static class ResearchRequest {
        private String topicName;
        private List<String> keywords;
        private String dateRangeStart;
        private String dateRangeEnd;
        private List<String> databases;
        private String previousProposal;

        // Builder pattern
        public static ResearchRequest of(String topicName) {
            ResearchRequest request = new ResearchRequest();
            request.setTopicName(topicName);
            return request;
        }

        // Getters and Setters
        public String getTopicName() {
            return topicName;
        }

        public void setTopicName(String topicName) {
            this.topicName = topicName;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public void setKeywords(List<String> keywords) {
            this.keywords = keywords;
        }

        public String getDateRangeStart() {
            return dateRangeStart;
        }

        public void setDateRangeStart(String dateRangeStart) {
            this.dateRangeStart = dateRangeStart;
        }

        public String getDateRangeEnd() {
            return dateRangeEnd;
        }

        public void setDateRangeEnd(String dateRangeEnd) {
            this.dateRangeEnd = dateRangeEnd;
        }

        public List<String> getDatabases() {
            return databases;
        }

        public void setDatabases(List<String> databases) {
            this.databases = databases;
        }

        public String getPreviousProposal() {
            return previousProposal;
        }

        public void setPreviousProposal(String previousProposal) {
            this.previousProposal = previousProposal;
        }
    }
}
