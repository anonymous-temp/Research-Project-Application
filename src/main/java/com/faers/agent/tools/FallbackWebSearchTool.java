package com.faers.agent.tools;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import com.faers.agent.pojo.StreamDataCallback;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 免费网页搜索工具 - 作为降级方案
 * 使用 DuckDuckGo HTML 搜索（无需 API key）
 */
public class FallbackWebSearchTool {
    
    private static final Logger log = LoggerFactory.getLogger(FallbackWebSearchTool.class);
    
    // 百度搜索接口（国内可访问）
    private static final String BAIDU_URL = "https://www.baidu.com/s";
    
    // 备用：使用 Bing 搜索（学术相关）
    private static final String BING_SCHOLAR_URL = "https://www.bing.com/search";
    
    // 连接超时时间（毫秒）- 调整超时时间以适应不同搜索引擎
    private static final int CONNECT_TIMEOUT = 10000; // 10秒
    private static final int READ_TIMEOUT = 15000; // 15秒
    
    /**
     * 使用免费搜索引擎进行网页搜索（支持国内网络环境）
     * 
     * @param query 搜索关键词
     * @param callback 回调函数
     * @return 搜索结果列表
     */
    public List<JSONObject> searchWeb(String query, StreamDataCallback callback) {
        log.info("FALLBACK_SEARCH_START | query={}", query);
        
        List<JSONObject> results = new ArrayList<>();
        
        // 搜索引擎尝试顺序（优先国内可访问的，确保baidu优先）
        String[] searchEngines = {"baidu", "bing"};
        
        // 增强搜索查询，添加药学相关关键词
        String pharmacyQuery = enhanceQueryForPharmacy(query);
        
        // 记录所有结果，最后合并处理
        List<JSONObject> allResults = new ArrayList<>();
        
        for (String engine : searchEngines) {
            try {
                log.info("Trying search engine: {}", engine);
                
                List<JSONObject> engineResults = new ArrayList<>();
                
                switch (engine) {
                    case "baidu":
                        engineResults = searchBaidu(pharmacyQuery);
                        break;
                    case "bing":
                        engineResults = searchBingScholar(pharmacyQuery);
                        break;
                }
                
                // 无论结果多少，都添加到总结果中
                if (!engineResults.isEmpty()) {
                    log.info("SEARCH_ENGINE_SUCCESS | engine={} | query={} | resultCount={}", 
                            engine, query, engineResults.size());
                    allResults.addAll(engineResults);
                } else {
                    log.warn("Search engine {} returned empty results", engine);
                }
                
            } catch (Exception e) {
                log.warn("FALLBACK_SEARCH_ERROR | engine={} | query={} | error={}", 
                        engine, query, e.getMessage());
                // 继续尝试下一个搜索引擎，不要中断
            }
        }
        
        // 简化结果处理：如果没有足够结果，使用原始查询直接搜索
        if (allResults.isEmpty()) {
            log.info("Trying direct search with original query: {}", query);
            try {
                // 直接使用百度搜索原始查询
                List<JSONObject> directResults = searchBaidu(query);
                if (!directResults.isEmpty()) {
                    allResults.addAll(directResults);
                    log.info("Direct search succeeded with {} results", directResults.size());
                }
            } catch (Exception e) {
                log.warn("Direct search failed: {}", e.getMessage());
            }
        }
        
        // 始终处理结果，即使结果较少
        if (!allResults.isEmpty()) {
            results = filterAndSortPharmacyResults(allResults, query);
            log.info("FALLBACK_SEARCH_SUCCESS | query={} | totalResultCount={} | filteredResultCount={}", 
                    query, allResults.size(), results.size());
        } else {
            // 所有搜索引擎都失败，返回空列表
            log.error("ALL_FALLBACK_SEARCHES_FAILED | query={}", query);
        }
        
        return results;
    }
    
    /**
     * 增强搜索查询，添加药学相关关键词
     * 
     * @param originalQuery 原始查询
     * @return 增强后的查询
     */
    private String enhanceQueryForPharmacy(String originalQuery) {
        // 简化查询增强，只在原始查询较短时添加关键词，避免过度复杂化
        if (originalQuery.length() < 10 && !originalQuery.toLowerCase().contains("药") && 
            !originalQuery.toLowerCase().contains("pharm") && !originalQuery.toLowerCase().contains("medic")) {
            // 只添加一个通用关键词，避免查询过于复杂
            return originalQuery + " 药物";
        }
        return originalQuery;
    }
    
    /**
     * 过滤并排序药学相关搜索结果
     * 
     * @param results 原始搜索结果
     * @param query 搜索查询
     * @return 过滤并排序后的结果
     */
    private List<JSONObject> filterAndSortPharmacyResults(List<JSONObject> results, String query) {
        // 简化过滤逻辑，降低相关性要求
        
        // 计算每个结果的相关性得分（更宽松的评分规则）
        Map<JSONObject, Integer> resultScores = new HashMap<>();
        
        for (JSONObject result : results) {
            try {
                int score = 1; // 默认给基础分，确保所有结果至少有1分
                String url = result.getStr("url");
                String title = result.getStr("name");
                String snippet = result.getStr("snippet");
                
                // 只检查最基础的相关性
                String lowerQuery = query.toLowerCase();
                String lowerTitle = title.toLowerCase();
                String lowerSnippet = snippet.toLowerCase();
                
                // 标题包含查询关键词，增加权重
                if (lowerTitle.contains(lowerQuery)) {
                    score += 5;
                }
                
                // 摘要包含查询关键词，增加权重
                if (lowerSnippet.contains(lowerQuery)) {
                    score += 3;
                }
                
                // URL包含查询关键词，增加权重
                if (url.toLowerCase().contains(lowerQuery)) {
                    score += 2;
                }
                
                // 简化域名权重，只保留政府和教育域名
                if (url.endsWith(".gov") || url.endsWith(".gov.cn")) {
                    score += 4;
                } else if (url.endsWith(".edu") || url.endsWith(".edu.cn")) {
                    score += 3;
                }
                
                resultScores.put(result, score);
            } catch (Exception e) {
                log.debug("Error processing result: {}", e.getMessage());
                // 跳过无效结果
            }
        }
        
        // 不过滤低分结果，保留所有有效结果
        List<JSONObject> filteredResults = new ArrayList<>(resultScores.keySet());
        
        // 根据得分排序结果（降序）
        filteredResults.sort((a, b) -> {
            int scoreA = resultScores.get(a);
            int scoreB = resultScores.get(b);
            return Integer.compare(scoreB, scoreA);
        });
        
        // 增加返回结果数量，最多返回10条
        List<JSONObject> finalResults = new ArrayList<>();
        int count = 0;
        for (JSONObject result : filteredResults) {
            if (count >= 10) break;
            finalResults.add(result);
            count++;
        }
        
        return finalResults;
    }
    
    /**
     * 使用百度搜索（国内可访问）
     */
    private List<JSONObject> searchBaidu(String query) {
        List<JSONObject> results = new ArrayList<>();
        
        try {
            // 简化查询，避免过度限制导致无结果
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            
            // 发送 GET 请求，增强请求头模拟真实浏览器，并显式启用自动重定向
            HttpResponse response = HttpRequest.get(BAIDU_URL + "?wd=" + encodedQuery + "&rn=20&ie=utf-8&oe=utf-8")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Connection", "keep-alive")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .setConnectionTimeout(CONNECT_TIMEOUT)
                    .setReadTimeout(READ_TIMEOUT)
                    .setFollowRedirects(true) // 显式启用自动重定向
                    .execute();
            
            if (response.getStatus() == 200) {
                String html = response.body();
                log.debug("Baidu response length: {}", html.length());
                
                // 解析 HTML
                Document doc = Jsoup.parse(html);
                
                // 尝试多种百度搜索结果选择器
                Elements resultElements = new Elements();
                
                // 主要选择器
                resultElements.addAll(doc.select("div.result"));
                resultElements.addAll(doc.select("div#content_left > div"));
                resultElements.addAll(doc.select("div.c-container"));
                resultElements.addAll(doc.select("div.result-op"));
                
                log.debug("Found {} potential Baidu results", resultElements.size());
                
                int count = 0;
                for (Element element : resultElements) {
                    if (count >= 20) break; // 多获取一些结果
                    
                    try {
                        // 尝试多种链接选择器
                        Element linkElement = null;
                        if (linkElement == null) linkElement = element.selectFirst("h3.t a");
                        if (linkElement == null) linkElement = element.selectFirst("h3 a");
                        if (linkElement == null) linkElement = element.selectFirst("a[href]");
                        
                        // 尝试多种摘要选择器
                        Element snippetElement = null;
                        if (snippetElement == null) snippetElement = element.selectFirst("div.c-abstract");
                        if (snippetElement == null) snippetElement = element.selectFirst("div.abstract");
                        if (snippetElement == null) snippetElement = element.selectFirst("div.content-right_8Zs40");
                        if (snippetElement == null) snippetElement = element.selectFirst("div.result-op");
                        
                        if (linkElement != null) {
                            String title = linkElement.text();
                            String url = linkElement.attr("href");
                            String snippet = snippetElement != null ? snippetElement.text() : "";
                            
                            // 处理百度跳转链接
                            if (url.contains("baidu.com/link")) {
                                // 尝试从onmousedown属性或其他地方获取真实URL
                                String onMouseDown = linkElement.attr("onmousedown");
                                if (onMouseDown.contains("http")) {
                                    // 简单提取URL
                                    int start = onMouseDown.indexOf("http");
                                    int end = onMouseDown.indexOf("'", start);
                                    if (start > 0 && end > start) {
                                        url = onMouseDown.substring(start, end);
                                    }
                                }
                            }
                            
                            // 过滤无效链接
                            if (url != null && !url.isEmpty() && 
                                !url.startsWith("javascript:") && 
                                !url.startsWith("#") && 
                                !title.isEmpty()) {
                                
                                JSONObject result = new JSONObject();
                                result.set("name", title);
                                result.set("url", url);
                                result.set("snippet", snippet);
                                result.set("source", "baidu");
                                
                                results.add(result);
                                count++;
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse individual Baidu result: {}", e.getMessage());
                    }
                }
                
                log.debug("Successfully parsed {} Baidu results", results.size());
            } else {
                log.error("Baidu search returned status: {}", response.getStatus());
            }
            
        } catch (Exception e) {
            log.error("Baidu search failed: {}", e.getMessage(), e);
        }
        
        return results;
    }
    
    /**
     * 使用 Bing 搜索作为备用
     */
    private List<JSONObject> searchBingScholar(String query) {
        List<JSONObject> results = new ArrayList<>();
        
        try {
            // 简化查询，避免过度限制导致无结果
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            
            // 发送 GET 请求，使用更简单的参数，适合国内访问
            String url = BING_SCHOLAR_URL + "?q=" + encodedQuery + "&count=20&setmkt=zh-CN&setlang=zh-CN";
            HttpResponse response = HttpRequest.get(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Connection", "keep-alive")
                    .setConnectionTimeout(CONNECT_TIMEOUT)
                    .setReadTimeout(READ_TIMEOUT)
                    .setFollowRedirects(true) // 启用自动重定向
                    .execute();
            
            if (response.getStatus() == 200) {
                String html = response.body();
                log.debug("Bing response length: {}", html.length());
                
                // 解析 HTML
                Document doc = Jsoup.parse(html);
                
                // 尝试多种Bing搜索结果选择器
                Elements resultElements = new Elements();
                
                // 主要选择器
                resultElements.addAll(doc.select("li.b_algo"));
                resultElements.addAll(doc.select("div#b_results > li"));
                // 获取所有div.b_caption元素，然后获取它们的父元素
                Elements captionElements = doc.select("div.b_caption");
                for (Element captionElement : captionElements) {
                    Element parent = captionElement.parent();
                    if (parent != null) {
                        resultElements.add(parent);
                    }
                }
                
                log.debug("Found {} potential Bing results", resultElements.size());
                
                int count = 0;
                for (Element element : resultElements) {
                    if (count >= 20) break; // 多获取一些结果
                    
                    try {
                        // 尝试多种链接选择器
                        Element linkElement = null;
                        if (linkElement == null) linkElement = element.selectFirst("h2 a");
                        if (linkElement == null) linkElement = element.selectFirst("h3 a");
                        if (linkElement == null) linkElement = element.selectFirst("a[href]");
                        
                        // 尝试多种摘要选择器
                        Element snippetElement = null;
                        if (snippetElement == null) snippetElement = element.selectFirst("div.b_caption p");
                        if (snippetElement == null) snippetElement = element.selectFirst("div.b_snippet");
                        if (snippetElement == null) snippetElement = element.selectFirst("div.snippet");
                        
                        if (linkElement != null) {
                            String title = linkElement.text();
                            String resultUrl = linkElement.attr("href");
                            String snippet = snippetElement != null ? snippetElement.text() : "";
                            
                            // 过滤无效链接和Bing内部链接
                            if (resultUrl != null && !resultUrl.isEmpty() && 
                                !resultUrl.startsWith("javascript:") && 
                                !resultUrl.startsWith("#") && 
                                !title.isEmpty()) {
                                
                                JSONObject result = new JSONObject();
                                result.set("name", title);
                                result.set("url", resultUrl);
                                result.set("snippet", snippet);
                                result.set("source", "bing");
                                
                                results.add(result);
                                count++;
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse individual Bing result: {}", e.getMessage());
                    }
                }
                
                log.debug("Successfully parsed {} Bing results", results.size());
            } else {
                log.error("Bing search returned status: {}", response.getStatus());
            }
            
        } catch (Exception e) {
            log.error("Bing search failed: {}", e.getMessage(), e);
        }
        
        return results;
    }
    
    /**
     * 简单的 Google Scholar 搜索（通过公开接口）
     * 注意：Google 有反爬虫机制，可能需要代理
     */
    private List<JSONObject> searchGoogleScholar(String query) {
        List<JSONObject> results = new ArrayList<>();
        
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://scholar.google.com/scholar?q=" + encodedQuery;
            
            HttpResponse response = HttpRequest.get(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .setConnectionTimeout(CONNECT_TIMEOUT)
                    .setReadTimeout(READ_TIMEOUT)
                    .execute();
            
            if (response.getStatus() == 200) {
                String html = response.body();
                Document doc = Jsoup.parse(html);
                Elements resultElements = doc.select("div.gs_ri");
                
                int count = 0;
                for (Element element : resultElements) {
                    if (count >= 10) break;
                    
                    try {
                        Element linkElement = element.selectFirst("h3.gs_rt a");
                        Element snippetElement = element.selectFirst("div.gs_rs");
                        
                        if (linkElement != null) {
                            String title = linkElement.text();
                            String url_scholar = linkElement.attr("href");
                            String snippet = snippetElement != null ? snippetElement.text() : "";
                            
                            if (url_scholar != null && !url_scholar.isEmpty()) {
                                JSONObject result = new JSONObject();
                                result.set("name", title);
                                result.set("url", url_scholar);
                                result.set("snippet", snippet);
                                result.set("source", "google_scholar");
                                
                                results.add(result);
                                count++;
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse Google Scholar result: {}", e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Google Scholar search failed: {}", e.getMessage(), e);
        }
        
        return results;
    }
    

}

