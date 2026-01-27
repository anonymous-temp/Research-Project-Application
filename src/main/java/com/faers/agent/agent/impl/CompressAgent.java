package com.faers.agent.agent.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.faers.agent.agent.prompt.AiSearchPromptEnum;
import com.faers.agent.utils.FastjsonDeltaExtractor;
import com.faers.agent.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class CompressAgent {
    private ChatClient chatClient;
    public CompressAgent() {
        DashScopeChatModel dashScopeChatModel = SpringContextUtil.getBean(DashScopeChatModel.class);
        ChatClient.Builder builder = ChatClient.builder(dashScopeChatModel);
        this.chatClient = builder.defaultOptions(ChatOptions.builder().model("qwen3-next-80b-a3b-instruct").build()).build();
    }

    public String history(String history) {
        if (StringUtils.isBlank(history)) {
            log.info("历史记录为空，直接返回");
            return "";
        }
        log.info("开始压缩历史记录，原始内容长度: {}", history.length());
        
        // 先记录原始历史记录的前100个字符，便于调试
        String debugHistory = history.length() > 100 ? history.substring(0, 100) + "..." : history;
        log.info("原始历史记录前100字符: {}", debugHistory);
        
        history = organizeData(history);
        log.info("经过organizeData处理后的历史记录长度: {}", history.length());
        
        StringBuilder builder = new StringBuilder();
        try {
            // 检查是否为JSON数组格式
            if (!history.startsWith("[")) {
                log.info("历史记录不是JSON数组格式，直接使用原始内容");
                builder.append(history);
            } else {
                JSONArray array = JSONArray.parseArray(history);
                log.info("成功解析JSON数组，包含{}个元素", array.size());
                for (Object object : array) {
                    List<FastjsonDeltaExtractor.DeltaEntry> results = new ArrayList<>();
                    FastjsonDeltaExtractor.findAllDeltaFields(object, "", results);
                    for (FastjsonDeltaExtractor.DeltaEntry result : results) {
                        builder.append(result.getValue());
                    }
                }
            }
        } catch (JSONException e) {
            log.error("历史信息JSON解析异常: {}", e.getMessage());
            log.info("使用原始历史记录进行压缩");
            builder.append(history);
        }
        
        Map<String, String> promptMap = new HashMap<>();
        String processedContent = builder.toString();
        log.info("处理后的历史记录长度: {}", processedContent.length());
        
        if (StringUtils.isBlank(processedContent)) {
            log.info("处理后的历史记录为空，直接返回");
            return "";
        }
        
        promptMap.put("history", processedContent);
        String messagePrompt = AiSearchPromptEnum.HISTORY_COMPRESS.getMessage(promptMap);
        try {
            log.info("开始调用AI模型进行历史记录压缩");
            String compressedResult = chatClient.prompt(messagePrompt).call().content();
            log.info("历史记录压缩完成，压缩后长度: {}", compressedResult.length());
            return compressedResult;
        } catch (Exception e) {
            log.error("历史信息压缩异常: {}", e.getMessage());
            log.info("压缩失败，返回原始处理后的内容");
            return processedContent;
        }
    }

    public String query(String query, String history, String file) {
        if (StringUtils.isBlank(history) && StringUtils.isBlank(file)) {
            log.info("历史记录和文件都为空，直接返回原始查询");
            return query;
        }
        log.info("开始压缩查询内容，原始查询长度: {}, 历史记录长度: {}, 文件长度: {}", 
                query.length(), StringUtils.isBlank(history) ? 0 : history.length(), 
                StringUtils.isBlank(file) ? 0 : file.length());
        
        if (StringUtils.isNotBlank(history)) {
            history = organizeData(history);
            log.info("经过organizeData处理后的历史记录长度: {}", history.length());
        }
        
        Map<String, String> promptMap = new HashMap<>();
        promptMap.put("history", history);
        promptMap.put("query", query);
        promptMap.put("file", file);
        
        String messagePrompt = AiSearchPromptEnum.QUERY_COMPRESS.getMessage(promptMap);
        try {
            log.info("开始调用AI模型进行查询压缩");
            String compressedResult = chatClient.prompt(messagePrompt).call().content();
            log.info("查询压缩完成，原始长度: {}, 压缩后长度: {}", query.length(), compressedResult.length());
            return compressedResult;
        } catch (Exception e) {
            log.error("查询压缩异常: {}", e.getMessage());
            log.info("压缩失败，返回原始查询");
            return query;
        }
    }

    private String organizeData(String join) {
        if (StringUtils.isBlank(join)) {
            return join;
        }
        Pattern r3 = Pattern.compile("jumpFun\\('([^']+)'\\,'([^']+)'\\)");
        Pattern r2 = Pattern.compile("/au0s8.*?/au0s8");
        Pattern r1 = Pattern.compile("<[^>]+>");
        String regex = "\\[下载链接]\\(https?://[^)]+\\)";
        join = join.replaceAll(regex, "");
        Matcher m3 = r3.matcher(join);
        join = m3.replaceAll("");
        Matcher m = r1.matcher(join);
        join = m.replaceAll("");
        Matcher m2 = r2.matcher(join);
        join = m2.replaceAll("");
        join = join.replaceAll("\\[END]", "");
        join = join.replaceAll("\\[START]", "");
        join = join.replaceAll("\\[DISTHINK]", "");
        join = join.replaceAll("\\[THINK]", "");
        join = join.replaceAll("\\[ANALYSIS_NUM]", "");
        join = join.replaceAll("\\[ANALYSIS_SELECT]", "");
        // 去除新版本标签
        join = join.replaceAll("\\[ANALYSIS_MODELING]", "");
        join = join.replaceAll("\\[ANALYSIS_DIVERSITY_INQUIRY]", "");
        join = join.replaceAll("\\[ANALYSIS_NUM]", "");
        join = join.replaceAll("\\[ANALYSIS_SELECT]", "");
        return join.replaceAll("/au0s8 @click=\"\">", "");
    }
}
