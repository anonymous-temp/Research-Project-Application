//package com.faers.agent.tools;
//
//import com.faers.agent.agent.context.AnalysisContext;
//import com.faers.agent.agent.impl.TopicESQueryAgent;
//import com.faers.agent.pojo.ProjectApplicationLiteratureDTO;
//import com.faers.agent.pojo.StreamDataCallback;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.elasticsearch.action.search.SearchRequest;
//import org.elasticsearch.action.search.SearchResponse;
//import org.elasticsearch.client.RequestOptions;
//import org.elasticsearch.client.RestHighLevelClient;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//import retrofit2.Call;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.ExecutionException;
//
///**
// * 并行查询工具类
// * 职责：同时执行ES查询和BochaWebSearch，提高查询效率
// * 特性：
// * - 使用CompletableFuture实现并行异步处理
// * - 支持ES多索引查询
// * - 支持BochaWebSearch网页搜索
// * - 统一结果处理和存储
// */
//@Component
//public class ParallelQueryTool {
//
//    private static final Logger log = LoggerFactory.getLogger(ParallelQueryTool.class);
//
//    @Autowired
//    @Qualifier("agent1ElasticsearchClient")
//    private RestHighLevelClient elasticsearchClient;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @Value("${search-api.bo-cha.key}")
//    private String bochaApiKey;
//
//    @Autowired
//    private TopicESQueryAgent topicESQueryAgent;
//
//    /**
//     * 执行并行查询
//     * @param context 分析上下文
//     * @return 查询结果包装类
//     * @throws ExecutionException 执行异常
//     * @throws InterruptedException 中断异常
//     */
//    public ParallelQueryResult executeParallelQuery(AnalysisContext context, StreamDataCallback callback) throws ExecutionException, InterruptedException {
//        String traceId = context.getTraceId();
//        log.info("PARALLEL_QUERY_START | traceId={}", traceId);
//
//        // 从上下文获取课题名称
//        String topicName = getTopicNameFromContext(context);
//        if (topicName == null || topicName.isEmpty()) {
//            throw new IllegalArgumentException("课题名称不能为空");
//        }
//
//        // 创建BochaWebSearchTool实例
//        BochaWebSearchTool bochaWebSearchTool = new BochaWebSearchTool(bochaApiKey);
//        bochaWebSearchTool.
//
//        // 获取查询结果
//        List<ProjectApplicationLiteratureDTO> esResults = esFuture.get();
//        String webSearchResults = webSearchFuture.get();
//
//        log.info("PARALLEL_QUERY_COMPLETED | traceId={} | esResultsSize={} | webSearchResultsSize={}",
//                traceId, esResults.size(), webSearchResults.length());
//
//        // 将结果存储到上下文中
//        try {
//            storeResultsToContext(context, esResults, webSearchResults);
//        } catch (Exception e) {
//            log.error("PARALLEL_QUERY_STORE_ERROR | traceId={}", traceId, e);
//            throw new RuntimeException("存储查询结果失败", e);
//        }
//
//        // 返回结果包装类
//        return new ParallelQueryResult(esResults, webSearchResults);
//    }
//
//    /**
//     * 从上下文中获取课题名称
//     * @param context 分析上下文
//     * @return 课题名称
//     */
//    private String getTopicNameFromContext(AnalysisContext context) {
//        // 优先从queryParams中获取
//        if (context.getQueryParams() != null && context.getQueryParams().containsKey("topicName")) {
//            return context.getQueryParams().get("topicName").toString();
//        }
//        // 其次从userMessage中提取
//        return context.getUserMessage();
//    }
//
//    /**
//     * 执行ES查询
//     * @param context 分析上下文
//     * @return ES查询结果
//     * @throws Exception 执行异常
//     */
//    private List<ProjectApplicationLiteratureDTO> executeESQuery(AnalysisContext context) throws Exception {
//        String traceId = context.getTraceId();
//
//        // 分别查询文献和说明书索引
//        List<ProjectApplicationLiteratureDTO> literatureResults = topicESQueryAgent.queryLiteratureIndices(context.getQueryParams(), traceId);
//        List<ProjectApplicationLiteratureDTO> instructionsResults = topicESQueryAgent.queryInstructionsIndices(context.getQueryParams(), traceId);
//
//        // 合并结果
//        List<ProjectApplicationLiteratureDTO> allResults = new ArrayList<>();
//        allResults.addAll(literatureResults);
//        allResults.addAll(instructionsResults);
//
//        // 按相关性排序
//        allResults.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
//
//        return allResults;
//    }
//
//    /**
//     * 将查询结果存储到上下文中
//     * @param context 分析上下文
//     * @param esResults ES查询结果
//     * @param webSearchResults 网页搜索结果
//     * @throws Exception 执行异常
//     */
//    private void storeResultsToContext(AnalysisContext context, List<ProjectApplicationLiteratureDTO> esResults, String webSearchResults) throws Exception {
//        // 存储ES查询结果
//        String esJson = objectMapper.writeValueAsString(esResults);
//        context.setDataInfo(esJson);
//        context.setCleanJson(esJson);
//
//        // 存储网页搜索结果
//        context.getQueryParams().put("webSearchResults", webSearchResults);
//    }
//
//    /**
//     * 并行查询结果包装类
//     */
//    public static class ParallelQueryResult {
//        private final List<ProjectApplicationLiteratureDTO> esResults;
//        private final String webSearchResults;
//
//        public ParallelQueryResult(List<ProjectApplicationLiteratureDTO> esResults, String webSearchResults) {
//            this.esResults = esResults;
//            this.webSearchResults = webSearchResults;
//        }
//
//        public List<ProjectApplicationLiteratureDTO> getEsResults() {
//            return esResults;
//        }
//
//        public String getWebSearchResults() {
//            return webSearchResults;
//        }
//    }
//}