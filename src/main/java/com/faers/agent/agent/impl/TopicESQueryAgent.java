// com/faers/agent/agent/impl/TopicESQueryAgent.java
package com.faers.agent.agent.impl;

import com.faers.agent.agent.Agent;
import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.config.AnalysisState;
import com.faers.agent.exception.AgentException;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.pojo.ProjectApplicationLiteratureDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 项目申报文献ES查询智能体（Project Application Literature Query Agent）
 * 职责：根据项目申报参数执行精准文献检索，为报告撰写提供高质量证据支撑
 * 
 * 核心查询参数：
 * - topicName（课题名称）：最高权重，匹配 title, summary, keywords, tldr
 * - researchFocus（研究重点）：高权重，匹配 title, keywords, summary, outcome
 * - disciplineField（学科领域）：中高权重，匹配 keywords, themWord, summary
 * - priorResearch（前期研究）：中等权重，匹配 summary, result, conclusion, researchGap
 * - clinicalData（临床数据）：中等权重，匹配 population, summary, result
 * - teamBackground（团队背景）：中低权重，匹配 keywords, themWord, summary
 * - outputLanguage（输出语言）：硬性过滤，精确匹配 language 字段
 * 
 * 查询策略：
 * - 核心参数使用 should 组合，要求至少匹配一个（minimumShouldMatch=1）
 * - 语言、年份使用 filter 硬性过滤（默认近3年）
 * - 结果按相关性得分 > 年份 > JCR影响因子排序
 * - 返回 Top 50 结果，包含完整的 summary, result, conclusion, researchGap 等字段
 * 
 * 特性：
 * - 多维度权重匹配，提高查询精准度
 * - 支持短语匹配和模糊匹配
 * - 提供高亮显示，标记匹配片段
 * - 支持超时重试机制（3次重试，25秒超时）
 * - 自动限制近3年文献，保证时效性
 */
@Component
public class TopicESQueryAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(TopicESQueryAgent.class);
    // 项目申报书相关证据使用的 ES 索引（对应 pojo.index 下的四个实体）
    private static final String LITERATURE_INDEX_ZGM = "literature_index_zgm_20251001";

    // 重试配置
    private static final int MAX_RETRY_ATTEMPTS = 3;  // 最大重试次数
    private static final long RETRY_DELAY_MS = 1000;  // 重试延迟（毫秒）
    private static final long QUERY_TIMEOUT_MS = 25000; // 单次查询超时时间（25秒）

    @Autowired
    @Qualifier("agent1ElasticsearchClient")
    private RestHighLevelClient elasticsearchClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.faers.agent.service.KeywordExtractionService keywordExtractionService;

    @Override
    public void process(AnalysisContext context, String id,
                        String parentId,
                        String userId,
                        StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("TOPIC_ES_QUERY_START | traceId={} | dialogId={}", traceId, id);

        try {
            // 1. 带超时和重试的查询文献索引
            List<ProjectApplicationLiteratureDTO> literatureResults = 
                executeWithRetry(() -> queryLiteratureIndices(context.getQueryParams(), traceId), 
                    "文献查询", traceId, callback);
            
            if (literatureResults == null) {
                // 查询失败，已经在executeWithRetry中设置了错误响应
                throw new AgentException("文献查询失败：所有重试均超时");
            }

            // 2. 合并结果并按相关性排序，取前10条
            List<ProjectApplicationLiteratureDTO> mergedResults = new ArrayList<>();
            mergedResults.addAll(literatureResults);
            // 按相关性分数降序排序
            mergedResults.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            
            // 取前50条
            if (mergedResults.size() > 50) {
                mergedResults = mergedResults.subList(0, 50);
            }

            // 3. 存储结果到上下文（使用严格 JSON，而不是 Map#toString）
            String esJson = objectMapper.writeValueAsString(mergedResults);
            context.setDataInfo(esJson);
            context.setCleanJson(esJson);

            // 4. 更新状态
            context.getSession().setAnalysisState(AnalysisState.ANALYSIS_DONE);

            log.info("TOPIC_ES_QUERY_COMPLETED | traceId={} | literatureResults={} | mergedResults={}",
                traceId, literatureResults.size(), mergedResults.size());

        } catch (Exception e) {
            log.error("TOPIC_ES_QUERY_ERROR | traceId={} | error={}", traceId, e.getMessage(), e);
            String errorMsg = "❌ 文献检索失败：" + e.getMessage();
            context.setResponseContent(errorMsg);
            
            // 发送错误响应到前端
            if (callback != null) {
                try {
                    com.faers.agent.dto.responseDto.Response errorResponse = 
                        com.faers.agent.dto.responseDto.Response.ResponseContentChatBuilder(errorMsg, "agent");
                    callback.onData(errorResponse);
                } catch (Exception ex) {
                    log.error("SEND_ERROR_RESPONSE_FAILED | traceId={}", traceId, ex);
                }
            }
            
            throw new AgentException("ES查询失败", e);
        }
    }
    
    /**
     * 带超时和重试机制的查询执行器
     * 
     * @param queryFunction 查询函数
     * @param queryName 查询名称（用于日志）
     * @param traceId 追踪ID
     * @param callback 回调函数（用于发送进度通知）
     * @return 查询结果，失败返回null
     */
    private <T> T executeWithRetry(QueryFunction<T> queryFunction, String queryName, 
                                    String traceId, StreamDataCallback callback) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                log.info("ES_QUERY_ATTEMPT | traceId={} | queryName={} | attempt={}/{}", 
                    traceId, queryName, attempt, MAX_RETRY_ATTEMPTS);
                
                // 发送进度通知到前端
                if (callback != null && attempt > 1) {
                    String progressMsg = String.format("正在重试%s（第%d次尝试）...", queryName, attempt);
                    try {
//                        com.faers.agent.dto.responseDto.Response progressResponse =
//                            com.faers.agent.dto.responseDto.Response.ResponseContentChatBuilder(progressMsg, "agent");
//                        callback.onData(progressResponse);
                    } catch (Exception e) {
                        log.warn("SEND_PROGRESS_FAILED | traceId={}", traceId, e);
                    }
                }
                
                // 使用Future实现超时控制
                java.util.concurrent.ExecutorService executor = 
                    java.util.concurrent.Executors.newSingleThreadExecutor();
                java.util.concurrent.Future<T> future = executor.submit(() -> {
                    try {
                        return queryFunction.execute();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                
                try {
                    // 等待结果，超时抛出TimeoutException
                    T result = future.get(QUERY_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
                    log.info("ES_QUERY_SUCCESS | traceId={} | queryName={} | attempt={}", 
                        traceId, queryName, attempt);
                    executor.shutdown();
                    return result;
                } catch (java.util.concurrent.TimeoutException e) {
                    log.warn("ES_QUERY_TIMEOUT | traceId={} | queryName={} | attempt={} | timeoutMs={}", 
                        traceId, queryName, attempt, QUERY_TIMEOUT_MS);
                    future.cancel(true);  // 取消超时的任务
                    executor.shutdownNow();
                    
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        // 等待后重试
                        Thread.sleep(RETRY_DELAY_MS);
                        continue;
                    } else {
                        // 最后一次尝试也超时，返回失败
                        String timeoutMsg = String.format("⚠️ %s超时（已重试%d次），请稍后再试", 
                            queryName, MAX_RETRY_ATTEMPTS);
                        if (callback != null) {
                            try {
                                com.faers.agent.dto.responseDto.Response timeoutResponse = 
                                    com.faers.agent.dto.responseDto.Response.ResponseContentChatBuilder(timeoutMsg, "agent");
                                callback.onData(timeoutResponse);
                            } catch (Exception ex) {
                                log.error("SEND_TIMEOUT_RESPONSE_FAILED | traceId={}", traceId, ex);
                            }
                        }
                        return null;
                    }
                } catch (Exception e) {
                    executor.shutdown();
                    throw e;
                }
                
            } catch (InterruptedException e) {
                log.error("ES_QUERY_INTERRUPTED | traceId={} | queryName={} | attempt={}", 
                    traceId, queryName, attempt, e);
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("ES_QUERY_ERROR | traceId={} | queryName={} | attempt={} | error={}", 
                    traceId, queryName, attempt, e.getMessage(), e);
                
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    // 等待后重试
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                } else {
                    // 最后一次尝试也失败
                    String errorMsg = String.format("❌ %s失败：%s", queryName, e.getMessage());
                    if (callback != null) {
                        try {
                            com.faers.agent.dto.responseDto.Response errorResponse = 
                                com.faers.agent.dto.responseDto.Response.ResponseContentChatBuilder(errorMsg, "agent");
                            callback.onData(errorResponse);
                        } catch (Exception ex) {
                            log.error("SEND_ERROR_RESPONSE_FAILED | traceId={}", traceId, ex);
                        }
                    }
                    return null;
                }
            }
        }
        return null;
    }
    
    /**
     * 查询函数接口（用于lambda表达式）
     */
    @FunctionalInterface
    private interface QueryFunction<T> {
        T execute() throws Exception;
    }

    /**
     * 查询文献索引（LITERATURE_INDEX_ZGM）
     * 根据课题申报参数进行多维度文献检索
     */
    public List<ProjectApplicationLiteratureDTO> queryLiteratureIndices(Map<String, Object> params, String traceId) throws Exception {
        SearchRequest searchRequest = new SearchRequest(LITERATURE_INDEX_ZGM);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        // 构建布尔查询
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 构建项目申报文献查询条件
        buildProjectApplicationLiteratureQuery(boolQuery, params, traceId);

        // 设置查询
        searchSourceBuilder.query(boolQuery);

        // 排序策略：相关性得分 > 年份 > JCR影响因子
        searchSourceBuilder.sort(SortBuilders.scoreSort().order(SortOrder.DESC));
        searchSourceBuilder.sort(SortBuilders.fieldSort("year").order(SortOrder.DESC));
        searchSourceBuilder.sort(SortBuilders.fieldSort("jcr").order(SortOrder.DESC));

        // 高亮设置
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.field("summary");
        highlightBuilder.field("conclusion");
        highlightBuilder.field("result");
        highlightBuilder.field("keywords");
        highlightBuilder.preTags("<em>");
        highlightBuilder.postTags("</em>");
        searchSourceBuilder.highlighter(highlightBuilder);

        // 返回Top 50结果
        searchSourceBuilder.size(50);

        // 构建并执行请求
        searchRequest.source(searchSourceBuilder);
        log.info("ES_LITERATURE_QUERY | traceId={} | index={} | params={}", 
            traceId, LITERATURE_INDEX_ZGM, params.keySet());

        // 执行查询
        SearchResponse searchResponse = elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT);
        log.info("ES_LITERATURE_RESPONSE | traceId={} | totalHits={} | maxScore={}", 
            traceId, searchResponse.getHits().getTotalHits().value, 
            searchResponse.getHits().getMaxScore());

        // 处理结果
        return processSearchResponse(searchResponse);
    }

    /**
     * 构建项目申报文献查询条件
     * 策略：核心参数高权重匹配 + 辅助参数提升相关性 + 硬性条件过滤
     */
    private void buildProjectApplicationLiteratureQuery(BoolQueryBuilder boolQuery, 
                                                        Map<String, Object> params, 
                                                        String traceId) {
        BoolQueryBuilder shouldQuery = QueryBuilders.boolQuery();
        int paramCount = 0;

        // ===== 1. 核心参数：课题名称（topicName）- 使用分层关键词提取 =====
        if (params.containsKey("topicName")) {
            String topicName = params.get("topicName").toString();
            log.debug("QUERY_PARAM | traceId={} | topicName={}", traceId, topicName);
            
            // 提取分层关键词
            com.faers.agent.service.KeywordExtractionService.KeywordExtractionResult keywords = 
                keywordExtractionService.extractKeywords(topicName, traceId);
            
            BoolQueryBuilder topicQuery = QueryBuilders.boolQuery();
            
            // 1.1 核心关键词 - 最高权重（10.0-12.0）
            for (String coreKeyword : keywords.getCoreKeywords()) {
                log.debug("CORE_KEYWORD | traceId={} | keyword={}", traceId, coreKeyword);
                
                // 短语匹配 - 最高权重
                topicQuery.should(QueryBuilders.matchPhraseQuery("title", coreKeyword).boost(12.0f));
                topicQuery.should(QueryBuilders.matchPhraseQuery("keywords", coreKeyword).boost(10.0f));
                topicQuery.should(QueryBuilders.matchPhraseQuery("summary", coreKeyword).boost(8.0f));
                
                // 普通匹配 - 高权重
                topicQuery.should(QueryBuilders.matchQuery("title", coreKeyword).boost(10.0f));
                topicQuery.should(QueryBuilders.matchQuery("keywords", coreKeyword).boost(8.0f));
                topicQuery.should(QueryBuilders.matchQuery("summary", coreKeyword).boost(6.0f));
                topicQuery.should(QueryBuilders.matchQuery("allKeyword", coreKeyword).boost(7.0f));
            }
            
            // 1.2 重要关键词 - 中等权重（4.0-6.0）
            for (String importantKeyword : keywords.getImportantKeywords()) {
                log.debug("IMPORTANT_KEYWORD | traceId={} | keyword={}", traceId, importantKeyword);
                
                topicQuery.should(QueryBuilders.matchPhraseQuery("title", importantKeyword).boost(6.0f));
                topicQuery.should(QueryBuilders.matchQuery("title", importantKeyword).boost(5.0f));
                topicQuery.should(QueryBuilders.matchQuery("keywords", importantKeyword).boost(4.0f));
                topicQuery.should(QueryBuilders.matchQuery("summary", importantKeyword).boost(3.0f));
            }
            
            // 1.3 普通关键词 - 低权重（1.0-2.0）
            for (String commonKeyword : keywords.getCommonKeywords()) {
                log.debug("COMMON_KEYWORD | traceId={} | keyword={}", traceId, commonKeyword);
                
                topicQuery.should(QueryBuilders.matchQuery("title", commonKeyword).boost(2.0f));
                topicQuery.should(QueryBuilders.matchQuery("summary", commonKeyword).boost(1.0f));
            }
            
            // 1.4 原始课题名称作为兜底（短语匹配，中等权重）
            topicQuery.should(QueryBuilders.matchPhraseQuery("title", topicName).boost(7.0f));
            topicQuery.should(QueryBuilders.matchQuery("tldr", topicName).boost(3.0f));
            
            // 至少匹配一个关键词
            topicQuery.minimumShouldMatch(1);
            
            shouldQuery.should(topicQuery);
            paramCount++;
            
            log.info("KEYWORD_EXTRACTION_RESULT | traceId={} | core={} | important={} | common={}", 
                traceId,
                keywords.getCoreKeywords(),
                keywords.getImportantKeywords(),
                keywords.getCommonKeywords());
        }

        // ===== 2. 研究重点方向（researchFocus）- 使用分层关键词 =====
        if (params.containsKey("researchFocus")) {
            String researchFocus = params.get("researchFocus").toString();
            log.debug("QUERY_PARAM | traceId={} | researchFocus={}", traceId, researchFocus);
            
            // 提取关键词
            com.faers.agent.service.KeywordExtractionService.KeywordExtractionResult focusKeywords = 
                keywordExtractionService.extractKeywords(researchFocus, traceId);
            
            BoolQueryBuilder focusQuery = QueryBuilders.boolQuery();
            
            // 核心关键词 - 高权重
            for (String coreKeyword : focusKeywords.getCoreKeywords()) {
                focusQuery.should(QueryBuilders.matchPhraseQuery("title", coreKeyword).boost(8.0f));
                focusQuery.should(QueryBuilders.matchQuery("title", coreKeyword).boost(7.0f));
                focusQuery.should(QueryBuilders.matchQuery("keywords", coreKeyword).boost(6.0f));
                focusQuery.should(QueryBuilders.matchQuery("summary", coreKeyword).boost(4.0f));
                focusQuery.should(QueryBuilders.matchQuery("o", coreKeyword).boost(5.0f));
            }
            
            // 重要关键词 - 中等权重
            for (String importantKeyword : focusKeywords.getImportantKeywords()) {
                focusQuery.should(QueryBuilders.matchQuery("title", importantKeyword).boost(4.0f));
                focusQuery.should(QueryBuilders.matchQuery("keywords", importantKeyword).boost(3.0f));
                focusQuery.should(QueryBuilders.matchQuery("summary", importantKeyword).boost(2.0f));
            }
            
            // 原始文本兜底
            focusQuery.should(QueryBuilders.matchPhraseQuery("title", researchFocus).boost(5.0f));
            focusQuery.should(QueryBuilders.matchQuery("tldr", researchFocus).boost(2.0f));
            
            focusQuery.minimumShouldMatch(1);
            
            shouldQuery.should(focusQuery);
            paramCount++;
        }

        // ===== 3. 申报学科领域（disciplineField）- 中高权重 =====
        if (params.containsKey("disciplineField")) {
            String disciplineField = params.get("disciplineField").toString();
            log.debug("QUERY_PARAM | traceId={} | disciplineField={}", traceId, disciplineField);
            
            BoolQueryBuilder fieldQuery = QueryBuilders.boolQuery();
            fieldQuery.should(QueryBuilders.matchQuery("keywords", disciplineField).boost(3.0f));
            fieldQuery.should(QueryBuilders.matchQuery("themWord", disciplineField).boost(2.0f));
            fieldQuery.should(QueryBuilders.matchQuery("summary", disciplineField).boost(1.5f));
            fieldQuery.should(QueryBuilders.matchQuery("allKeyword", disciplineField).boost(2.5f));
            
            shouldQuery.should(fieldQuery);
            paramCount++;
        }

        // ===== 4. 前期研究成果（priorResearch）- 中等权重 =====
        if (params.containsKey("priorResearch")) {
            String priorResearch = params.get("priorResearch").toString();
            log.debug("QUERY_PARAM | traceId={} | priorResearch={}", traceId, priorResearch);
            
            BoolQueryBuilder priorQuery = QueryBuilders.boolQuery();
            priorQuery.should(QueryBuilders.matchQuery("summary", priorResearch).boost(2.5f));
            priorQuery.should(QueryBuilders.matchQuery("result", priorResearch).boost(2.0f));
            priorQuery.should(QueryBuilders.matchQuery("conclusion", priorResearch).boost(2.0f));
            priorQuery.should(QueryBuilders.matchQuery("keywords", priorResearch).boost(1.5f));
            priorQuery.should(QueryBuilders.matchQuery("researchGap", priorResearch).boost(1.8f));
            
            shouldQuery.should(priorQuery);
            paramCount++;
        }

        // ===== 5. 临床数据资源（clinicalData）- 中等权重 =====
        if (params.containsKey("clinicalData")) {
            String clinicalData = params.get("clinicalData").toString();
            log.debug("QUERY_PARAM | traceId={} | clinicalData={}", traceId, clinicalData);
            
            BoolQueryBuilder clinicalQuery = QueryBuilders.boolQuery();
            clinicalQuery.should(QueryBuilders.matchQuery("p", clinicalData).boost(2.5f));
            clinicalQuery.should(QueryBuilders.matchQuery("summary", clinicalData).boost(2.0f));
            clinicalQuery.should(QueryBuilders.matchQuery("result", clinicalData).boost(1.5f));
            clinicalQuery.should(QueryBuilders.matchQuery("ic", clinicalData).boost(1.5f));
            
            shouldQuery.should(clinicalQuery);
            paramCount++;
        }

        // ===== 6. 团队成员专业背景（teamBackground）- 中低权重 =====
        if (params.containsKey("teamBackground")) {
            String teamBackground = params.get("teamBackground").toString();
            log.debug("QUERY_PARAM | traceId={} | teamBackground={}", traceId, teamBackground);
            
            BoolQueryBuilder teamQuery = QueryBuilders.boolQuery();
            teamQuery.should(QueryBuilders.matchQuery("keywords", teamBackground).boost(2.0f));
            teamQuery.should(QueryBuilders.matchQuery("themWord", teamBackground).boost(1.5f));
            teamQuery.should(QueryBuilders.matchQuery("summary", teamBackground).boost(1.0f));
            teamQuery.should(QueryBuilders.matchQuery("allKeyword", teamBackground).boost(1.8f));
            
            shouldQuery.should(teamQuery);
            paramCount++;
        }

        // ===== 7. 添加 should 查询到主查询（要求至少匹配一个核心参数）=====
        if (paramCount > 0) {
            // 至少匹配一个参数，提高查询精准度
            shouldQuery.minimumShouldMatch(1);
            boolQuery.must(shouldQuery);
            log.info("QUERY_SHOULD_CLAUSES | traceId={} | paramCount={}", traceId, paramCount);
        }

        // ===== 8. 可选过滤条件：输出语言（outputLanguage）- 默认不限制，查询所有语言 =====
        // 注释掉硬性过滤，支持中英文混合查询
        /*
        if (params.containsKey("outputLanguage")) {
            String outputLanguage = params.get("outputLanguage").toString().toLowerCase();
            log.debug("QUERY_FILTER | traceId={} | language={}", traceId, outputLanguage);
            
            // 语言映射：中文->zh, 英文->en
            String langCode = outputLanguage;
            if (outputLanguage.contains("中文") || outputLanguage.contains("chinese")) {
                langCode = "zh";
            } else if (outputLanguage.contains("英文") || outputLanguage.contains("english")) {
                langCode = "en";
            }
            
            boolQuery.filter(QueryBuilders.termQuery("language", langCode));
        }
//        */
//        log.debug("QUERY_LANGUAGE | traceId={} | No language filter applied, querying all languages", traceId);

        // ===== 9. 年份范围过滤：默认近3年 =====
        int currentYear = java.time.Year.now().getValue();
        int startYear = currentYear - 2; // 近3年
        boolQuery.filter(QueryBuilders.rangeQuery("year").gte(startYear).lte(currentYear));
        log.debug("QUERY_FILTER | traceId={} | yearRange={}-{}", traceId, startYear, currentYear);

        // ===== 10. 保留原有兼容性参数（可选）=====
        // JCR影响因子
        if (params.containsKey("jcrMin")) {
            boolQuery.filter(QueryBuilders.rangeQuery("jcr").gte(params.get("jcrMin")));
        }
        if (params.containsKey("jcrMax")) {
            boolQuery.filter(QueryBuilders.rangeQuery("jcr").lte(params.get("jcrMax")));
        }

        // 研究类型
        if (params.containsKey("researchType")) {
            @SuppressWarnings("unchecked")
            List<String> researchTypes = (List<String>) params.get("researchType");
            boolQuery.filter(QueryBuilders.termsQuery("type", researchTypes));
        }

        // 样本量
        if (params.containsKey("sampleSizeMin")) {
            boolQuery.filter(QueryBuilders.rangeQuery("sampleSize").gte(params.get("sampleSizeMin")));
        }
        if (params.containsKey("sampleSizeMax")) {
            boolQuery.filter(QueryBuilders.rangeQuery("sampleSize").lte(params.get("sampleSizeMax")));
        }

        // 期刊
        if (params.containsKey("journal")) {
            String journal = params.get("journal").toString();
            if (journal.contains("*") || journal.contains("?")) {
                boolQuery.filter(QueryBuilders.wildcardQuery("journal.keyword", journal.toLowerCase()));
            } else {
                boolQuery.filter(QueryBuilders.matchQuery("journal", journal));
            }
        }

        // 作者
        if (params.containsKey("author")) {
            boolQuery.filter(QueryBuilders.matchQuery("author", params.get("author")));
        }

        // JCR分区
        if (params.containsKey("journalDivision")) {
            boolQuery.filter(QueryBuilders.matchQuery("journalDivision", params.get("journalDivision")));
        }

        // 引用计数
        if (params.containsKey("referencedCountMin")) {
            boolQuery.filter(QueryBuilders.rangeQuery("referencedCount").gte(params.get("referencedCountMin")));
        }
        
        log.info("QUERY_BUILD_COMPLETE | traceId={} | coreParams={} | filters={}", 
            traceId, paramCount, boolQuery.filter().size());
    }



    /**
     * 处理ES查询响应，映射为项目申报文献DTO
     * 返回所有用于报告撰写的关键字段
     */
    public List<ProjectApplicationLiteratureDTO> processSearchResponse(SearchResponse searchResponse) {
        List<ProjectApplicationLiteratureDTO> results = new ArrayList<>();

        searchResponse.getHits().forEach(hit -> {
            Map<String, Object> sourceAsMap = hit.getSourceAsMap();

            ProjectApplicationLiteratureDTO dto = new ProjectApplicationLiteratureDTO();
            
            // 基础信息
            dto.setId(hit.getId());
            dto.setTitle(getString(sourceAsMap, "title"));
            dto.setAuthor(getString(sourceAsMap, "author"));
            dto.setYear(getString(sourceAsMap, "year"));
            dto.setJournal(getString(sourceAsMap, "journal"));
            dto.setType(getString(sourceAsMap, "type"));
            dto.setLanguage(getString(sourceAsMap, "language"));
            dto.setPages(getString(sourceAsMap, "pages"));

            // 作者列表
            Object authorObj = sourceAsMap.get("author");
            if (authorObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> authorList = (List<String>) authorObj;
                dto.setAuthorList(authorList);
            } else if (authorObj != null) {
                // 如果是字符串，尝试分割
                List<String> authorList = new java.util.ArrayList<>();
                authorList.add(authorObj.toString());
                dto.setAuthorList(authorList);
            }

            // 关键词列表
            Object kws = sourceAsMap.get("keywords");
            if (kws instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> kwList = (List<String>) kws;
                dto.setKeywords(kwList);
            }

            // 卷
            Object volumeObj = sourceAsMap.get("volume");
            if (volumeObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> volumeList = (List<String>) volumeObj;
                dto.setVolume(volumeList);
            }

            // 期
            Object issueObj = sourceAsMap.get("issue");
            if (issueObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> issueList = (List<String>) issueObj;
                dto.setIssue(issueList);
            }

            // DOI
            Object doiObj = sourceAsMap.get("doi");
            if (doiObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> doiList = (List<String>) doiObj;
                dto.setDoi(doiList);
            }

            // 核心内容字段（用于报告撰写）
            dto.setSummary(getString(sourceAsMap, "summary"));          // 摘要（核心）
            dto.setConclusion(getString(sourceAsMap, "conclusion"));    // 结论（核心）
            dto.setResult(getString(sourceAsMap, "result"));            // 研究结果（核心）
            dto.setResearchGap(getString(sourceAsMap, "researchGap"));  // 研究空白（核心）
            dto.setTldr(getString(sourceAsMap, "tldr"));                // 简短总结（辅助理解）

            // 质量指标
            dto.setSampleSize(getLong(sourceAsMap, "sampleSize"));
            dto.setJcr(getDouble(sourceAsMap, "jcr"));
            dto.setReferencedCount(getLong(sourceAsMap, "referencedCount"));

            // 高亮字段（展示匹配度）
            if (hit.getHighlightFields() != null && !hit.getHighlightFields().isEmpty()) {
                if (hit.getHighlightFields().get("title") != null) {
                    dto.setHighlightTitle(hit.getHighlightFields().get("title").getFragments()[0].toString());
                }
                if (hit.getHighlightFields().get("summary") != null) {
                    dto.setHighlightSummary(hit.getHighlightFields().get("summary").getFragments()[0].toString());
                }
                if (hit.getHighlightFields().get("conclusion") != null) {
                    dto.setHighlightConclusion(hit.getHighlightFields().get("conclusion").getFragments()[0].toString());
                }
                if (hit.getHighlightFields().get("result") != null) {
                    dto.setHighlightConclusion(hit.getHighlightFields().get("result").getFragments()[0].toString());
                }
                if (hit.getHighlightFields().get("keywords") != null) {
                    dto.setHighlightTitle(hit.getHighlightFields().get("keywords").getFragments()[0].toString());
                }
            }

            // 相关性得分
            dto.setScore(hit.getScore());
            results.add(dto);
        });

        return results;
    }

    private String getString(Map<String, Object> source, String key) {
        Object v = source.get(key);
        return v != null ? v.toString() : null;
    }

    private Long getLong(Map<String, Object> source, String key) {
        Object v = source.get(key);
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return v != null ? Long.parseLong(v.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double getDouble(Map<String, Object> source, String key) {
        Object v = source.get(key);
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return v != null ? Double.parseDouble(v.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}