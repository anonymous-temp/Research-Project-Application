package com.faers.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.faers.agent.utils.Model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 关键词提取服务
 * 使用LLM从课题名称中提取核心关键词，并分配重要性权重
 * 解决ES分词导致常见词淹没关键词的问题
 */
@Service
public class KeywordExtractionService {

    private static final Logger log = LoggerFactory.getLogger(KeywordExtractionService.class);

    @Autowired
    private ObjectMapper objectMapper;

    // 可注入LLM服务（如OpenAI、通义千问等）
    // @Autowired
    // private LLMService llmService;

    /**
     * 从课题名称中提取分层关键词
     * 
     * @param topicName 课题名称
     * @param traceId 追踪ID
     * @return 分层关键词结果
     */
    public KeywordExtractionResult extractKeywords(String topicName, String traceId) {
        log.info("KEYWORD_EXTRACTION_START | traceId={} | topicName={}", traceId, topicName);

        try {
            // 方案1: 使用LLM提取（推荐）
            KeywordExtractionResult result = extractWithLLM(topicName, traceId);
            
            // 方案2: 如果LLM不可用，使用规则提取（备用）
            if (result == null || result.getCoreKeywords().isEmpty()) {
                log.warn("LLM_EXTRACTION_FAILED | traceId={} | fallback to rule-based", traceId);
                result = extractWithRules(topicName, traceId);
            }

            log.info("KEYWORD_EXTRACTION_COMPLETE | traceId={} | core={} | important={} | common={}", 
                traceId, 
                result.getCoreKeywords().size(),
                result.getImportantKeywords().size(),
                result.getCommonKeywords().size());

            return result;

        } catch (Exception e) {
            log.error("KEYWORD_EXTRACTION_ERROR | traceId={} | error={}", traceId, e.getMessage(), e);
            // 返回原始文本作为单个关键词
            return KeywordExtractionResult.fallback(topicName);
        }
    }

    /**
     * 使用LLM提取关键词（推荐方案）
     */
    private KeywordExtractionResult extractWithLLM(String topicName, String traceId) {
        try {
            // 构建prompt
            String prompt = buildExtractionPrompt(topicName);
            
            // 使用Model类调用大模型
            String llmResponse = Model.getModel("", prompt, "qwen-plus");
            
            // 解析LLM返回的JSON
            KeywordExtractionResult result = parseLLMResponse(llmResponse, traceId);
            
            log.debug("LLM_EXTRACTION_SUCCESS | traceId={} | result={}", traceId, llmResponse);
            return result;

        } catch (Exception e) {
            log.error("LLM_EXTRACTION_ERROR | traceId={} | error={}", traceId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建LLM提取prompt
     */
    private String buildExtractionPrompt(String topicName) {
        return String.format("""
            请从以下课题名称中提取关键词，并按重要性分为三个层级：
            
            课题名称：%s
            
            分类标准：
            1. 核心关键词（core）：最重要的专业术语、研究对象、核心技术（权重10）
               - 例如：特定疾病名、专有技术名、研究主体
               - 特点：专业性强、不可替代、匹配度直接决定相关性
            
            2. 重要关键词（important）：重要的研究方向、方法、领域（权重5）
               - 例如：研究方法、应用领域、技术类别
               - 特点：对理解研究有帮助，但相对通用
            
            3. 普通关键词（common）：常见的背景词、通用词（权重1）
               - 例如：研究、方法、分析、患者等
               - 特点：高频词，区分度低
            
            请返回JSON格式：
            {
              "core": ["核心关键词1", "核心关键词2"],
              "important": ["重要关键词1", "重要关键词2"],
              "common": ["普通关键词1", "普通关键词2"]
            }
            
            注意：
            - 核心关键词应该是2-4个词组（非单字）
            - 优先提取专业术语和技术名词
            - 避免过度分词，保持词组完整性
            """, topicName);
    }

    /**
     * 模拟LLM响应（实际使用时删除此方法，直接调用LLM）
     */
    private String simulateLLMResponse(String topicName) {
        // 这里是示例响应，实际应该调用真实的LLM服务
        if (topicName.contains("自述数据") && topicName.contains("智能认知")) {
            return """
                {
                  "core": ["自述数据", "智能认知", "患者价值观获取"],
                  "important": ["报告方法", "医学指南"],
                  "common": ["研究", "方法", "患者"]
                }
                """;
        } else if (topicName.contains("人工智能") && topicName.contains("医疗")) {
            return """
                {
                  "core": ["人工智能", "医疗诊断", "深度学习"],
                  "important": ["医学影像", "辅助诊断"],
                  "common": ["研究", "应用", "系统"]
                }
                """;
        } else {
            // 默认：简单分词
            return String.format("""
                {
                  "core": ["%s"],
                  "important": [],
                  "common": []
                }
                """, topicName);
        }
    }

    /**
     * 解析LLM返回的JSON
     */
    private KeywordExtractionResult parseLLMResponse(String llmResponse, String traceId) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonMap = objectMapper.readValue(llmResponse, Map.class);

        List<String> coreKeywords = extractListFromMap(jsonMap, "core");
        List<String> importantKeywords = extractListFromMap(jsonMap, "important");
        List<String> commonKeywords = extractListFromMap(jsonMap, "common");

        return new KeywordExtractionResult(coreKeywords, importantKeywords, commonKeywords);
    }

    @SuppressWarnings("unchecked")
    private List<String> extractListFromMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof List) {
            return ((List<?>) value).stream()
                .map(Object::toString)
                .filter(s -> s != null && !s.trim().isEmpty())
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    /**
     * 基于规则的关键词提取（备用方案）
     * 当LLM不可用时使用
     */
    private KeywordExtractionResult extractWithRules(String topicName, String traceId) {
        log.info("RULE_BASED_EXTRACTION | traceId={} | topicName={}", traceId, topicName);

        List<String> coreKeywords = new ArrayList<>();
        List<String> importantKeywords = new ArrayList<>();
        List<String> commonKeywords = new ArrayList<>();

        // 规则1: 识别专业术语（基于词典）
        Set<String> technicalTerms = identifyTechnicalTerms(topicName);
        coreKeywords.addAll(technicalTerms);

        // 规则2: 识别研究方法
        Set<String> methods = identifyResearchMethods(topicName);
        importantKeywords.addAll(methods);

        // 规则3: 识别常见词
        Set<String> commonWords = identifyCommonWords(topicName);
        commonKeywords.addAll(commonWords);

        // 规则4: 如果没有提取到核心关键词，将整个标题作为核心关键词
        if (coreKeywords.isEmpty()) {
            coreKeywords.add(topicName);
        }

        return new KeywordExtractionResult(coreKeywords, importantKeywords, commonKeywords);
    }

    /**
     * 识别专业术语（基于医学词典）
     */
    private Set<String> identifyTechnicalTerms(String text) {
        Set<String> terms = new HashSet<>();
        
        // 医学专业术语词典（示例，实际应该加载完整词典）
        String[] technicalPatterns = {
            "智能认知", "自述数据", "患者价值观", "深度学习", "神经网络",
            "机器学习", "数据挖掘", "生物信息", "基因测序", "蛋白质组",
            "影像组学", "精准医疗", "临床决策", "风险预测", "预后评估",
            "糖尿病", "高血压", "肿瘤", "心血管", "神经退行性疾病"
        };

        for (String pattern : technicalPatterns) {
            if (text.contains(pattern)) {
                terms.add(pattern);
            }
        }

        return terms;
    }

    /**
     * 识别研究方法
     */
    private Set<String> identifyResearchMethods(String text) {
        Set<String> methods = new HashSet<>();
        
        String[] methodPatterns = {
            "深度学习", "机器学习", "统计分析", "数据分析", "Meta分析",
            "随机对照", "队列研究", "病例对照", "横断面研究", "系统评价",
            "卷积神经网络", "循环神经网络", "注意力机制", "迁移学习"
        };

        for (String pattern : methodPatterns) {
            if (text.contains(pattern)) {
                methods.add(pattern);
            }
        }

        return methods;
    }

    /**
     * 识别常见词（应该降低权重）
     */
    private Set<String> identifyCommonWords(String text) {
        Set<String> commonWords = new HashSet<>();
        
        String[] commonPatterns = {
            "研究", "分析", "方法", "系统", "应用", "技术",
            "患者", "临床", "医学", "医院", "治疗", "诊断",
            "数据", "模型", "算法", "评价", "报告", "指南"
        };

        for (String pattern : commonPatterns) {
            if (text.contains(pattern)) {
                commonWords.add(pattern);
            }
        }

        return commonWords;
    }

    /**
     * 关键词提取结果
     */
    public static class KeywordExtractionResult {
        private List<String> coreKeywords;       // 核心关键词（权重10）
        private List<String> importantKeywords;  // 重要关键词（权重5）
        private List<String> commonKeywords;     // 普通关键词（权重1）

        public KeywordExtractionResult() {
            this.coreKeywords = new ArrayList<>();
            this.importantKeywords = new ArrayList<>();
            this.commonKeywords = new ArrayList<>();
        }

        public KeywordExtractionResult(List<String> coreKeywords, 
                                      List<String> importantKeywords,
                                      List<String> commonKeywords) {
            this.coreKeywords = coreKeywords != null ? coreKeywords : new ArrayList<>();
            this.importantKeywords = importantKeywords != null ? importantKeywords : new ArrayList<>();
            this.commonKeywords = commonKeywords != null ? commonKeywords : new ArrayList<>();
        }

        public static KeywordExtractionResult fallback(String originalText) {
            return new KeywordExtractionResult(
                Arrays.asList(originalText),
                new ArrayList<>(),
                new ArrayList<>()
            );
        }

        // Getters and Setters
        public List<String> getCoreKeywords() {
            return coreKeywords;
        }

        public void setCoreKeywords(List<String> coreKeywords) {
            this.coreKeywords = coreKeywords;
        }

        public List<String> getImportantKeywords() {
            return importantKeywords;
        }

        public void setImportantKeywords(List<String> importantKeywords) {
            this.importantKeywords = importantKeywords;
        }

        public List<String> getCommonKeywords() {
            return commonKeywords;
        }

        public void setCommonKeywords(List<String> commonKeywords) {
            this.commonKeywords = commonKeywords;
        }

        public List<String> getAllKeywords() {
            List<String> all = new ArrayList<>();
            all.addAll(coreKeywords);
            all.addAll(importantKeywords);
            all.addAll(commonKeywords);
            return all;
        }
    }
}

