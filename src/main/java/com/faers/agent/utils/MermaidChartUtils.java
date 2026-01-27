package com.faers.agent.utils;

import com.faers.agent.pojo.MermaidPromptConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Mermaid图表工具类
 * 用于生成研究总体框架图、技术路线图和可行性分析表格
 * 支持在Markdown报告中直接渲染图表
 * 支持动态图表生成和图片格式导出
 *
 * @author AI Assistant
 */
@Component
public class MermaidChartUtils {

    private static final Logger log = LoggerFactory.getLogger(MermaidChartUtils.class);

    /**
     * 从报告中提取关键信息
     *
     * @param report 报告内容
     * @return 提取的关键信息
     */
    public ReportKeyInfo extractKeyInfoFromReport(String report) {
        if (report == null || report.isEmpty()) {
            return new ReportKeyInfo();
        }

        ReportKeyInfo keyInfo = new ReportKeyInfo();

        // 提取研究内容
        Pattern researchContentPattern = Pattern.compile("（一）研究内容\\s*[\\s\\S]*?(?=（二）|（三）|四、|$)");
        Matcher researchContentMatcher = researchContentPattern.matcher(report);
        if (researchContentMatcher.find()) {
            keyInfo.setResearchContent(researchContentMatcher.group(0));
        }

        // 提取研究方案
        Pattern researchPlanPattern = Pattern.compile("（一）研究方案\\s*[\\s\\S]*?(?=（二）|（三）|四、|$)");
        Matcher researchPlanMatcher = researchPlanPattern.matcher(report);
        if (researchPlanMatcher.find()) {
            keyInfo.setResearchPlan(researchPlanMatcher.group(0));
        }

        // 提取可行性分析
        Pattern feasibilityPattern = Pattern.compile("（二）可行性分析\\s*[\\s\\S]*?(?=（三）|四、|$)");
        Matcher feasibilityMatcher = feasibilityPattern.matcher(report);
        if (feasibilityMatcher.find()) {
            keyInfo.setFeasibilityAnalysis(feasibilityMatcher.group(0));
        }

        // 提取关键词
        Pattern keywordsPattern = Pattern.compile("关键词：([\\s\\S]*?)\\n");
        Matcher keywordsMatcher = keywordsPattern.matcher(report);
        if (keywordsMatcher.find()) {
            String[] keywords = keywordsMatcher.group(1).split("、|,|");
            for (String keyword : keywords) {
                if (!keyword.trim().isEmpty()) {
                    keyInfo.addKeyword(keyword.trim());
                }
            }
        }

        return keyInfo;
    }

    /**
     * 生成研究框架图
     *
     * @param report 报告内容
     * @return Mermaid图表代码
     */
    public String generateResearchFrameworkChart(String report) {
        try {
            // 使用LLM从报告中提取患者价值观相关的内容要素和特征要素
            String extractedInfo = Model.getModel(
                    MermaidPromptConstants.RESEARCH_FRAMEWORK_PROMPT,
                    "请从以下报告中提取患者价值观相关的内容要素和特征要素:\n" + report,
                    "qwen-plus"
            );

            // 解析LLM返回的JSON内容
            Map<String, Object> extractedParams = parseExtractedParams(extractedInfo);

            // 构建研究框架图
            StringBuilder mermaidCode = new StringBuilder();
            mermaidCode.append("```mermaid\n");
            mermaidCode.append("flowchart LR\n"); // 使用左右布局
            mermaidCode.append("    %% 设置图表样式\n");
            mermaidCode.append("    classDef nodeStyle fill:white,stroke:#333,stroke-width:2px,rx:3px\n");
            mermaidCode.append("    classDef subgraphStyle stroke:#333,stroke-width:2px,stroke-dasharray:5,5\n");
            mermaidCode.append("\n");
            mermaidCode.append("    subgraph 患者价值观报告框架\n");
            mermaidCode.append("    style 患者价值观报告框架 fill:white,stroke:#333,stroke-width:2px,rx:5px\n");

            // 基于提取的参数生成框架图
            if (extractedParams.containsKey("contentElements")) {
                List<String> contentElements = (List<String>) extractedParams.get("contentElements");
                List<String> featureElements = (List<String>) extractedParams.getOrDefault("featureElements", new ArrayList<>());

                // 创建内容要素子图（左侧）
                mermaidCode.append("        subgraph 患者价值观的内容要素\n");
                mermaidCode.append("        style 患者价值观的内容要素 fill:white,stroke:#333,stroke-width:1px,stroke-dasharray:5,5\n");

                // 生成内容要素节点
                for (int i = 0; i < contentElements.size(); i++) {
                    String element = contentElements.get(i);
                    mermaidCode.append("            C").append(i + 1).append("[\"").append(element).append("\"]:::nodeStyle\n");
                    // 垂直连接内容要素
                    if (i > 0) {
                        mermaidCode.append("            C").append(i).append(" --> C").append(i + 1).append(":::nodeStyle\n");
                    }
                }

                // 添加省略号（如果有内容）
                if (!contentElements.isEmpty()) {
                    mermaidCode.append("            C").append(contentElements.size()).append(" --> C_ellipsis[\"、、、、、\"]:::nodeStyle\n");
                }

                mermaidCode.append("        end\n");

                // 创建特征要素子图（右侧）
                mermaidCode.append("        subgraph 患者价值观的特征要素\n");
                mermaidCode.append("        style 患者价值观的特征要素 fill:white,stroke:#333,stroke-width:1px,stroke-dasharray:5,5\n");

                // 生成特征要素节点
                for (int i = 0; i < featureElements.size(); i++) {
                    String element = featureElements.get(i);
                    mermaidCode.append("            F").append(i + 1).append("[\"").append(element).append("\"]:::nodeStyle\n");
                    // 垂直连接特征要素
                    if (i > 0) {
                        mermaidCode.append("            F").append(i).append(" --> F").append(i + 1).append(":::nodeStyle\n");
                    }
                }

                // 添加省略号（如果有内容）
                if (!featureElements.isEmpty()) {
                    mermaidCode.append("            F").append(featureElements.size()).append(" --> F_ellipsis[\"、、、、、\"]:::nodeStyle\n");
                }

                mermaidCode.append("        end\n");

            } else {
                // 默认双列结构
                mermaidCode.append("        subgraph 患者价值观的内容要素\n");
                mermaidCode.append("        style 患者价值观的内容要素 fill:white,stroke:#333,stroke-width:1px,stroke-dasharray:5,5\n");
                mermaidCode.append("            C1[\"关注的亚组人群\\n（如证型）\"]:::nodeStyle --> C2[\"关注的干预措施\\n（如中药）\"]:::nodeStyle\n");
                mermaidCode.append("            C2 --> C3[\"关注的结局指标\"]:::nodeStyle\n");
                mermaidCode.append("            C3 --> C_ellipsis[\"、、、、、\"]:::nodeStyle\n");
                mermaidCode.append("        end\n");

                mermaidCode.append("        subgraph 患者价值观的特征要素\n");
                mermaidCode.append("        style 患者价值观的特征要素 fill:white,stroke:#333,stroke-width:1px,stroke-dasharray:5,5\n");
                mermaidCode.append("            F1[\"关注的频次\"]:::nodeStyle --> F2[\"情感倾向\"]:::nodeStyle\n");
                mermaidCode.append("            F2 --> F3[\"不同年龄关注点\\n的差异\"]:::nodeStyle\n");
                mermaidCode.append("            F3 --> F_ellipsis[\"、、、、、\"]:::nodeStyle\n");
                mermaidCode.append("        end\n");
            }

            mermaidCode.append("    end\n");
            mermaidCode.append("```\n");
            mermaidCode.append("<center><strong>图2：患者价值观报告框架（中医指南问题构建阶段）示意图</strong></center>\n");

            return mermaidCode.toString();
        } catch (Exception e) {
            log.error("生成研究框架图失败: {}", e.getMessage());
            // 优雅降级：返回基于关键词的默认图表
            return generateDefaultResearchFrameworkChart(report);
        }
    }

    /**
     * 生成基于中医指南临床问题结构的患者价值观报告框架图（扁平图样式）
     *
     * @return Mermaid图表代码
     */
//    public String generateClinicalProblemFrameworkChart() {
//        StringBuilder mermaidCode = new StringBuilder();
//        mermaidCode.append("```mermaid\n");
//        mermaidCode.append("flowchart TD\n");
//        mermaidCode.append("    %% 外层虚线框\n");
//        mermaidCode.append("    subgraph outer[基于中医指南临床问题结构形成患者价值观的报告框架]\n");
//        mermaidCode.append("        style outer stroke-dasharray: 5 5, stroke:#333, stroke-width:2px, fill:none\n");
//        mermaidCode.append("        \n");
//        mermaidCode.append("        %% 左侧垂直文字\n");
//        mermaidCode.append("        数据分析框架[数据分析框架]\n");
//        mermaidCode.append("        style 数据分析框架 font-size:14px, font-weight:bold, writing-mode: vertical-lr, text-align:center, width:30px\n");
//        mermaidCode.append("        \n");
//        mermaidCode.append("        %% 左侧子图 - 文献研究与专家共识\n");
//        mermaidCode.append("        文献研究与专家共识[文献研究与专家共识]\n");
//        mermaidCode.append("        style 文献研究与专家共识 fill:#d9e6f5, stroke:#333, stroke-width:1px\n");
//        mermaidCode.append("        A[文献研究]\n");
//        mermaidCode.append("        B[专家共识]\n");
//        mermaidCode.append("        A --> B\n");
//        mermaidCode.append("        B --> 文献研究与专家共识\n");
//        mermaidCode.append("        \n");
//        mermaidCode.append("        %% 右侧子图 - 患者价值观分析\n");
//        mermaidCode.append("        患者价值观分析[患者价值观分析]\n");
//        mermaidCode.append("        style 患者价值观分析 fill:#d9e6f5, stroke:#333, stroke-width:1px\n");
//        mermaidCode.append("        C[患者价值观内容要素]\n");
//        mermaidCode.append("        D[患者价值观特征要素]\n");
//        mermaidCode.append("        C --> D\n");
//        mermaidCode.append("        D --> 患者价值观分析\n");
//        mermaidCode.append("        \n");
//        mermaidCode.append("        %% 连接线\n");
//        mermaidCode.append("        文献研究与专家共识 -->|形成| 患者价值观分析\n");
//        mermaidCode.append("        数据分析框架 --- 文献研究与专家共识\n");
//        mermaidCode.append("        数据分析框架 --- 患者价值观分析\n");
//        mermaidCode.append("    end\n");
//        mermaidCode.append("    \n");
//        mermaidCode.append("    %% 样式定义\n");
//        mermaidCode.append("    classDef nodeStyle fill:white,stroke:#333,stroke-width:1px,rx:3px\n");
//        mermaidCode.append("    class A,B,C,D nodeStyle\n");
//        mermaidCode.append("```\n");
//        mermaidCode.append("<center><strong>图：基于中医指南临床问题结构形成患者价值观的报告框架示意图</strong></center>\n");
//        return mermaidCode.toString();
//    }

    /**
     * 生成默认研究框架图（降级方案）
     *
     * @param report 报告内容
     * @return Mermaid图表代码
     */
    private String generateDefaultResearchFrameworkChart(String report) {
        try {
            ReportKeyInfo keyInfo = extractKeyInfoFromReport(report);
            List<String> keywords = keyInfo.getKeywords();

            // 构建研究框架图
            StringBuilder mermaidCode = new StringBuilder();
            mermaidCode.append("```mermaid\n");
            mermaidCode.append("flowchart LR\n"); // 使用左右布局
            mermaidCode.append("    %% 设置图表样式\n");
            mermaidCode.append("    classDef nodeStyle fill:white,stroke:#333,stroke-width:2px,rx:3px\n");
            mermaidCode.append("\n");
            mermaidCode.append("    subgraph 患者价值观报告框架\n");
            mermaidCode.append("    style 患者价值观报告框架 fill:white,stroke:#333,stroke-width:2px,rx:5px\n");

            // 根据报告内容动态生成框架图
            if (keywords.contains("患者价值观") || keywords.contains("中医指南")) {
                // 使用双列结构
                mermaidCode.append("        subgraph 患者价值观的内容要素\n");
                mermaidCode.append("        style 患者价值观的内容要素 fill:white,stroke:#333,stroke-width:1px,stroke-dasharray:5,5\n");
                mermaidCode.append("            C1[\"关注的亚组人群\\n（如证型）\"]:::nodeStyle --> C2[\"关注的干预措施\\n（如中药）\"]:::nodeStyle\n");
                mermaidCode.append("            C2 --> C3[\"关注的结局指标\"]:::nodeStyle\n");
                mermaidCode.append("            C3 --> C_ellipsis[\"、、、、、\"]:::nodeStyle\n");
                mermaidCode.append("        end\n");

                mermaidCode.append("        subgraph 患者价值观的特征要素\n");
                mermaidCode.append("        style 患者价值观的特征要素 fill:white,stroke:#333,stroke-width:1px,stroke-dasharray:5,5\n");
                mermaidCode.append("            F1[\"关注的频次\"]:::nodeStyle --> F2[\"情感倾向\"]:::nodeStyle\n");
                mermaidCode.append("            F2 --> F3[\"不同年龄关注点\\n的差异\"]:::nodeStyle\n");
                mermaidCode.append("            F3 --> F_ellipsis[\"、、、、、\"]:::nodeStyle\n");
                mermaidCode.append("        end\n");
            } else {
                // 通用双列结构
                mermaidCode.append("        subgraph 研究内容维度\n");
                mermaidCode.append("        style 研究内容维度 fill:white,stroke:#333,stroke-width:1px,stroke-dasharray:5,5\n");
                mermaidCode.append("            C1[\"研究问题提出\"]:::nodeStyle --> C2[\"文献综述分析\"]:::nodeStyle\n");
                mermaidCode.append("            C2 --> C3[\"研究设计制定\"]:::nodeStyle\n");
                mermaidCode.append("            C3 --> C_ellipsis[\"、、、、、\"]:::nodeStyle\n");
                mermaidCode.append("        end\n");

                mermaidCode.append("        subgraph 研究实施特征\n");
                mermaidCode.append("        style 研究实施特征 fill:white,stroke:#333,stroke-width:1px,stroke-dasharray:5,5\n");
                mermaidCode.append("            F1[\"研究周期\"]:::nodeStyle --> F2[\"数据来源\"]:::nodeStyle\n");
                mermaidCode.append("            F2 --> F3[\"分析方法\"]:::nodeStyle\n");
                mermaidCode.append("            F3 --> F_ellipsis[\"、、、、、\"]:::nodeStyle\n");
                mermaidCode.append("        end\n");
            }

            mermaidCode.append("    end\n");
            mermaidCode.append("```\n");
            mermaidCode.append("<center><strong>图2：患者价值观报告框架（中医指南问题构建阶段）示意图</strong></center>\n");

            return mermaidCode.toString();
        } catch (Exception e) {
            log.error("生成默认研究框架图失败: {}", e.getMessage());
            return "**研究框架图生成失败**：无法提取报告关键信息。";
        }
    }

    /**
     * 生成技术路线图（新版本 - 严格按照图片格式）
     *
     * @param report 报告内容
     * @return Mermaid图表代码
     */
//    public String generateTechnologyRoadmap(String report) {
//        return generateTechnologyRoadmap(report, true);
//    }

    /**
     * 生成技术路线图（新版本 - 严格按照图片格式）
     *
     * @param report 报告内容
     * @param includeCaption 是否包含图片标题
     * @return Mermaid图表代码
     */
    public String generateTechnologyRoadmap(String report, boolean includeCaption) {
        try {
            // 使用LLM从报告中提取技术路线的层次化结构
            String extractedInfo = Model.getModel(
                    MermaidPromptConstants.TECHNICAL_ROUTE_PROMPT,
                    "请从以下报告中提取技术路线的主要阶段、子任务和阶段关系:\n" + report,
                    "qwen-plus"
            );

            // 解析LLM返回的JSON内容
            Map<String, Object> extractedParams = parseExtractedParams(extractedInfo);

            // 构建技术路线图
            StringBuilder mermaidCode = new StringBuilder();
            mermaidCode.append("```mermaid\n");
            mermaidCode.append("flowchart TD\n");
            mermaidCode.append("    %% 图例说明：阶段-任务-成果\n");
            mermaidCode.append("    %% 紫色框=研究阶段 | 蓝色框=任务节点 | 橙色框=重点阶段 | 白色框=成果产出\n");
            mermaidCode.append("\n");
            mermaidCode.append("    %% 样式定义\n");
            mermaidCode.append("    classDef dataSourceStyle fill:#F5F5F5,stroke:#666,stroke-width:2px,rx:5px\n");
            mermaidCode.append("    classDef phaseStyle fill:#E1BEE7,stroke:#333,stroke-width:2px,rx:5px\n");
            mermaidCode.append("    classDef phase2Style fill:#FFCCBC,stroke:#333,stroke-width:2px,rx:5px\n");
            mermaidCode.append("    classDef taskStyle fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,rx:5px\n");
            mermaidCode.append("    classDef outcomeStyle fill:#FFFFFF,stroke:#333,stroke-width:2px,rx:5px\n");
            mermaidCode.append("\n");

            // 基于提取的参数生成路线图
            if (extractedParams.containsKey("mainPhases")) {
                List<Map<String, Object>> mainPhases = (List<Map<String, Object>>) extractedParams.get("mainPhases");
                List<Map<String, Object>> subTasks = (List<Map<String, Object>>) extractedParams.getOrDefault("subTasks", new ArrayList<>());
                List<Map<String, Object>> relationships = (List<Map<String, Object>>) extractedParams.getOrDefault("relationships", new ArrayList<>());

                // 用于存储节点ID的映射
                Map<String, String> nodeIdMap = new HashMap<>();
                
                // 1. 生成数据源区域（如果有）
                if (extractedParams.containsKey("dataSource")) {
                    Map<String, Object> dataSource = (Map<String, Object>) extractedParams.get("dataSource");
                    String dataSourceName = (String) dataSource.getOrDefault("name", "数据源");
                    List<Map<String, Object>> dataSourceNodes = (List<Map<String, Object>>) dataSource.getOrDefault("subNodes", new ArrayList<>());
                    
                    mermaidCode.append("    %% 数据源层\n");
                    // 创建数据源容器和子节点
                    if (!dataSourceNodes.isEmpty()) {
                        for (int i = 0; i < dataSourceNodes.size(); i++) {
                            Map<String, Object> node = dataSourceNodes.get(i);
                            String nodeName = (String) node.getOrDefault("name", "数据" + (i + 1));
                            String nodeId = "DataSource" + i;
                            mermaidCode.append("    ").append(nodeId).append("[\"").append(nodeName).append("\"]:::dataSourceStyle\n");
                            nodeIdMap.put("dataSource_" + i, nodeId);
                        }
                        mermaidCode.append("\n");
                    }
                }
                
                // 2. 生成主要阶段
                mermaidCode.append("    %% 研究阶段与任务\n");
                for (int phaseIdx = 0; phaseIdx < mainPhases.size(); phaseIdx++) {
                    final int finalPhaseIdx = phaseIdx;
                    Map<String, Object> phase = mainPhases.get(phaseIdx);
                    String phaseName = (String) phase.getOrDefault("name", "阶段" + (phaseIdx + 1));
                    String phaseType = (String) phase.getOrDefault("type", "phase");
                    
                    // 确定阶段样式
                    String phaseClass = ":::phaseStyle";
                    if ("phase2".equals(phaseType)) {
                        phaseClass = ":::phase2Style"; // 橙色阶段（重点/系统开发）
                    } else if ("outcome".equals(phaseType)) {
                        phaseClass = ":::outcomeStyle"; // 白色成果节点
                    } else if ("task".equals(phaseType)) {
                        phaseClass = ":::taskStyle"; // 蓝色任务节点
                    }

                    // 生成主阶段节点
                    String mainNodeId = "Phase" + phaseIdx;
                    mermaidCode.append("    ").append(mainNodeId).append("[\"").append(phaseName).append("\"]").append(phaseClass).append("\n");
                    nodeIdMap.put("phase_" + phaseIdx, mainNodeId);

                    // 获取并生成该阶段的子任务
                    List<Map<String, Object>> phaseSubTasks = subTasks.stream()
                            .filter(task -> {
                                Object phaseIndexObj = task.get("phaseIndex");
                                if (phaseIndexObj instanceof Integer) {
                                    return (int) phaseIndexObj == finalPhaseIdx;
                                } else if (phaseIndexObj instanceof Double) {
                                    return ((Double) phaseIndexObj).intValue() == finalPhaseIdx;
                                }
                                return false;
                            })
                            .collect(Collectors.toList());

                    if (!phaseSubTasks.isEmpty()) {
                        for (int i = 0; i < phaseSubTasks.size(); i++) {
                            Map<String, Object> task = phaseSubTasks.get(i);
                            String taskName = (String) task.getOrDefault("name", "任务" + (i + 1));
                            String taskId = "Phase" + phaseIdx + "_Task" + i;
                            String taskType = (String) task.getOrDefault("type", "task");
                            String taskClass = "outcome".equals(taskType) ? ":::outcomeStyle" : ":::taskStyle";
                            
                            mermaidCode.append("    ").append(taskId).append("[\"").append(taskName).append("\"]").append(taskClass).append("\n");
                            nodeIdMap.put("phase_" + phaseIdx + "_task_" + i, taskId);
                        }
                    }
                }
                mermaidCode.append("\n");
                
                // 3. 生成连接关系
                mermaidCode.append("    %% 流程连接关系\n");
                if (!relationships.isEmpty()) {
                    // 使用LLM提取的关系
                    for (Map<String, Object> rel : relationships) {
                        String source = (String) rel.get("source");
                        String target = (String) rel.get("target");
                        String label = (String) rel.getOrDefault("label", "");
                        
                        String sourceNodeId = nodeIdMap.get(source);
                        String targetNodeId = nodeIdMap.get(target);
                        
                        if (sourceNodeId != null && targetNodeId != null) {
                            if (label != null && !label.isEmpty()) {
                                mermaidCode.append("    ").append(sourceNodeId).append(" -->|").append(label).append("| ").append(targetNodeId).append("\n");
                            } else {
                                mermaidCode.append("    ").append(sourceNodeId).append(" --> ").append(targetNodeId).append("\n");
                            }
                        }
                    }
                } else {
                    // 默认连接逻辑：确保所有节点都连接，无孤立节点
                    // 从数据源连接到第一个阶段
                    if (extractedParams.containsKey("dataSource")) {
                        Map<String, Object> dataSource = (Map<String, Object>) extractedParams.get("dataSource");
                        List<Map<String, Object>> dataSourceNodes = (List<Map<String, Object>>) dataSource.getOrDefault("subNodes", new ArrayList<>());
                        
                        if (!dataSourceNodes.isEmpty() && !mainPhases.isEmpty()) {
                            for (int i = 0; i < dataSourceNodes.size(); i++) {
                                mermaidCode.append("    DataSource").append(i).append(" --> Phase0\n");
                            }
                        }
                    }
                    
                    // 连接主阶段到子任务和下一阶段
                    for (int phaseIdx = 0; phaseIdx < mainPhases.size(); phaseIdx++) {
                        final int finalPhaseIdx = phaseIdx;
                        List<Map<String, Object>> phaseSubTasks = subTasks.stream()
                                .filter(task -> {
                                    Object phaseIndexObj = task.get("phaseIndex");
                                    if (phaseIndexObj instanceof Integer) {
                                        return (int) phaseIndexObj == finalPhaseIdx;
                                    } else if (phaseIndexObj instanceof Double) {
                                        return ((Double) phaseIndexObj).intValue() == finalPhaseIdx;
                                    }
                                    return false;
                                })
                                .collect(Collectors.toList());
                        
                        if (!phaseSubTasks.isEmpty()) {
                            // 主阶段连接到第一个子任务
                            mermaidCode.append("    Phase").append(phaseIdx).append(" --> Phase").append(phaseIdx).append("_Task0\n");
                            
                            // 子任务之间的连接
                            for (int i = 1; i < phaseSubTasks.size(); i++) {
                                mermaidCode.append("    Phase").append(phaseIdx).append("_Task").append(i-1).append(" --> Phase").append(phaseIdx).append("_Task").append(i).append("\n");
                            }
                            
                            // 最后一个子任务和主阶段都连接到下一阶段（确保关联性）
                            if (phaseIdx < mainPhases.size() - 1) {
                                mermaidCode.append("    Phase").append(phaseIdx).append("_Task").append(phaseSubTasks.size() - 1).append(" --> Phase").append(phaseIdx + 1).append("\n");
                                // 如果主阶段没有直接连接到下一阶段，添加连接
                                mermaidCode.append("    Phase").append(phaseIdx).append(" -.-> Phase").append(phaseIdx + 1).append("\n");
                            }
                        } else {
                            // 无子任务，直接连接到下一阶段
                            if (phaseIdx < mainPhases.size() - 1) {
                                mermaidCode.append("    Phase").append(phaseIdx).append(" --> Phase").append(phaseIdx + 1).append("\n");
                            }
                        }
                    }
                }
                
                // 4. 图例说明已移除（根据用户需求）
                // 注释：如需恢复图例，取消注释以下代码
                // mermaidCode.append("\n");
                // mermaidCode.append("    %% 图例\n");
                // mermaidCode.append("    Legend1[\"<b>图例说明</b>\"]\n");
                // mermaidCode.append("    Legend2[\"紫色框=研究阶段\"]:::phaseStyle\n");
                // mermaidCode.append("    Legend3[\"蓝色框=任务节点\"]:::taskStyle\n");
                // mermaidCode.append("    Legend4[\"橙色框=重点阶段\"]:::phase2Style\n");
                // mermaidCode.append("    Legend5[\"白色框=成果产出\"]:::outcomeStyle\n");
                // mermaidCode.append("    Legend1 -.-> Legend2\n");
                // mermaidCode.append("    Legend1 -.-> Legend3\n");
                // mermaidCode.append("    Legend1 -.-> Legend4\n");
                // mermaidCode.append("    Legend1 -.-> Legend5\n");
                // mermaidCode.append("    style Legend1 fill:#FFFFCC,stroke:#333,stroke-width:2px,rx:5px\n");
            } else {
                // 降级方案：使用默认路线图
                return generateDefaultTechnologyRoadmap(report, includeCaption);
            }

            mermaidCode.append("```\n");
            
            // 添加标题（如果需要）
            if (includeCaption) {
                mermaidCode.append("<center><strong>图1：技术路线图</strong></center>\n");
            }

            return mermaidCode.toString();
        } catch (Exception e) {
            log.error("生成技术路线图失败: {}", e.getMessage());
            // 优雅降级：返回基于关键词的默认图表
            return generateDefaultTechnologyRoadmap(report, includeCaption);
        }
    }

    /**
     * 生成技术路线图（旧版本 - 已注释）
     *
     * @param report 报告内容
     * @return Mermaid图表代码
     */
//    public String generateTechnologyRoadmapOld(String report) {
//        try {
//            // 使用LLM从报告中提取技术路线的层次化结构
//            String extractedInfo = Model.getModel(
//                    PromptConstants.TECHNICAL_ROUTE_PROMPT,
//                    "请从以下报告中提取技术路线的主要阶段、子任务和阶段关系:\n" + report,
//                    "qwen-plus"
//            );
//
//            // 解析LLM返回的JSON内容
//            Map<String, Object> extractedParams = parseExtractedParams(extractedInfo);
//
//            // 构建技术路线图
//            StringBuilder mermaidCode = new StringBuilder();
//            mermaidCode.append("```mermaid\n");
//            mermaidCode.append("flowchart TD\n"); // 使用自上而下的流程图布局
//            mermaidCode.append("    %% 设置图表样式\n");
//            mermaidCode.append("    classDef phaseStyle fill:white,stroke:#333,stroke-width:2px,rx:5px\n");
//            mermaidCode.append("    classDef taskStyle fill:#E3F2FD,stroke:#2196F3,stroke-width:1px,rx:3px\n");
//            mermaidCode.append("    classDef outcomeStyle fill:white,stroke:#333,stroke-width:1px,rx:3px\n");
//            mermaidCode.append("    classDef dashedBoxStyle fill:white,stroke:#333,stroke-width:1px,stroke-dasharray:5,5,rx:5px\n");
//            mermaidCode.append("    classDef solidBoxStyle fill:white,stroke:#333,stroke-width:1px,rx:5px\n");
//            mermaidCode.append("\n");
//
//            // 基于提取的参数生成路线图
//            if (extractedParams.containsKey("mainPhases")) {
//                List<Map<String, Object>> mainPhases = (List<Map<String, Object>>) extractedParams.get("mainPhases");
//                List<Map<String, Object>> subTasks = (List<Map<String, Object>>) extractedParams.getOrDefault("subTasks", new ArrayList<>());
//
//                // 生成每个主要阶段
//                for (int phaseIdx = 0; phaseIdx < mainPhases.size(); phaseIdx++) {
//                    final int finalPhaseIdx = phaseIdx;
//                    Map<String, Object> phase = mainPhases.get(phaseIdx);
//                    String phaseName = (String) phase.getOrDefault("name", "阶段" + (phaseIdx + 1));
//                    String phaseId = "Phase" + phaseIdx;
//
//                    // 生成阶段内的子任务
//                    List<Map<String, Object>> phaseSubTasks = subTasks.stream()
//                            .filter(task -> (int) task.getOrDefault("phaseIndex", -1) == finalPhaseIdx)
//                            .collect(Collectors.toList());
//
//                    if (!phaseSubTasks.isEmpty()) {
//                        // 将子任务分组
//                        List<Map<String, Object>> topTasks = phaseSubTasks.stream()
//                                .filter(task -> "顶部".equals(task.getOrDefault("position", "")))
//                                .collect(Collectors.toList());
//                        List<Map<String, Object>> leftTasks = phaseSubTasks.stream()
//                                .filter(task -> "左侧".equals(task.getOrDefault("position", "")))
//                                .collect(Collectors.toList());
//                        List<Map<String, Object>> rightTasks = phaseSubTasks.stream()
//                                .filter(task -> "右侧".equals(task.getOrDefault("position", "")))
//                                .collect(Collectors.toList());
//                        List<Map<String, Object>> centerTasks = phaseSubTasks.stream()
//                                .filter(task -> !"顶部".equals(task.getOrDefault("position", "")) && !"左侧".equals(task.getOrDefault("position", "")) && !"右侧".equals(task.getOrDefault("position", "")))
//                                .collect(Collectors.toList());
//
//                        // 生成顶部子任务（用于标题或概述）
//                        if (!topTasks.isEmpty()) {
//                            for (int i = 0; i < topTasks.size(); i++) {
//                                Map<String, Object> task = topTasks.get(i);
//                                String taskName = (String) task.getOrDefault("name", "子任务" + (i + 1));
//                                String taskId = phaseId + "_T" + i;
//                                String taskClass = "成果".equals(task.getOrDefault("type", "")) ? ":::outcomeStyle" : ":::taskStyle";
//                                mermaidCode.append("    " + taskId + "[" + taskName + "]" + taskClass + "\n");
//                                if (i > 0) {
//                                    mermaidCode.append("    " + phaseId + "_T" + (i - 1) + " --> " + taskId + "\n");
//                                }
//                            }
//                        }
//
//                        // 生成左侧子任务
//                        if (!leftTasks.isEmpty()) {
//                            // 直接使用子图名称，不添加标签，这样就不会显示phase0_Left等字段
//                            mermaidCode.append("    subgraph " + phaseId + "_Left\n");
//                            mermaidCode.append("    style " + phaseId + "_Left dashedBoxStyle\n");
//                            for (int i = 0; i < leftTasks.size(); i++) {
//                                Map<String, Object> task = leftTasks.get(i);
//                                String taskName = (String) task.getOrDefault("name", "子任务" + (i + 1));
//                                String taskId = phaseId + "_L" + i;
//                                String taskClass = "成果".equals(task.getOrDefault("type", "")) ? ":::outcomeStyle" : ":::taskStyle";
//                                mermaidCode.append("        " + taskId + "[" + taskName + "]" + taskClass + "\n");
//                                if (i > 0) {
//                                    mermaidCode.append("        " + phaseId + "_L" + (i - 1) + " --> " + taskId + "\n");
//                                }
//                            }
//                            mermaidCode.append("    end\n");
//
//                            // 连接到顶部任务（如果没有顶部任务，则直接生成，不需要连接）
//                            if (!topTasks.isEmpty()) {
//                                mermaidCode.append("    " + phaseId + "_T" + (topTasks.size() - 1) + " --> " + phaseId + "_L0" + "\n");
//                            }
//                        }
//
//                        // 生成右侧子任务
//                        if (!rightTasks.isEmpty()) {
//                            // 直接使用子图名称，不添加标签，这样就不会显示phase0_Right等字段
//                            mermaidCode.append("    subgraph " + phaseId + "_Right\n");
//                            mermaidCode.append("    style " + phaseId + "_Right dashedBoxStyle\n");
//                            for (int i = 0; i < rightTasks.size(); i++) {
//                                Map<String, Object> task = rightTasks.get(i);
//                                String taskName = (String) task.getOrDefault("name", "子任务" + (i + 1));
//                                String taskId = phaseId + "_R" + i;
//                                String taskClass = "成果".equals(task.getOrDefault("type", "")) ? ":::outcomeStyle" : ":::taskStyle";
//                                mermaidCode.append("        " + taskId + "[" + taskName + "]" + taskClass + "\n");
//                                if (i > 0) {
//                                    mermaidCode.append("        " + phaseId + "_R" + (i - 1) + " --> " + taskId + "\n");
//                                }
//                            }
//                            mermaidCode.append("    end\n");
//
//                            // 连接到顶部任务（如果没有顶部任务，则直接生成，不需要连接）
//                            if (!topTasks.isEmpty()) {
//                                mermaidCode.append("    " + phaseId + "_T" + (topTasks.size() - 1) + " --> " + phaseId + "_R0" + "\n");
//                            }
//                        }
//
//                        // 生成中间子任务（用于连接左右两侧或展示成果）
//                        if (!centerTasks.isEmpty()) {
//                            // 直接使用子图名称，不添加标签，这样就不会显示phase0_Center等字段
//                            mermaidCode.append("    subgraph " + phaseId + "_Center\n");
//                            mermaidCode.append("    style " + phaseId + "_Center solidBoxStyle\n");
//                            for (int i = 0; i < centerTasks.size(); i++) {
//                                Map<String, Object> task = centerTasks.get(i);
//                                String taskName = (String) task.getOrDefault("name", "子任务" + (i + 1));
//                                String taskId = phaseId + "_C" + i;
//                                String taskClass = "成果".equals(task.getOrDefault("type", "")) ? ":::outcomeStyle" : ":::taskStyle";
//                                mermaidCode.append("        " + taskId + "[" + taskName + "]" + taskClass + "\n");
//                                if (i > 0) {
//                                    mermaidCode.append("        " + phaseId + "_C" + (i - 1) + " --> " + taskId + "\n");
//                                }
//                            }
//                            mermaidCode.append("    end\n");
//
//                            // 连接到左侧和右侧任务（如果存在）
//                            if (!leftTasks.isEmpty()) {
//                                mermaidCode.append("    " + phaseId + "_L" + (leftTasks.size() - 1) + " --> " + phaseId + "_C0" + "\n");
//                            }
//                            if (!rightTasks.isEmpty()) {
//                                mermaidCode.append("    " + phaseId + "_R" + (rightTasks.size() - 1) + " --> " + phaseId + "_C0" + "\n");
//                            }
//
//                            // 如果没有左右任务，直接连接到顶部任务（如果有的话）
//                            if (leftTasks.isEmpty() && rightTasks.isEmpty() && !topTasks.isEmpty()) {
//                                mermaidCode.append("    " + phaseId + "_T" + (topTasks.size() - 1) + " --> " + phaseId + "_C0" + "\n");
//                            }
//                        }
//                    }
//
//                    // 生成阶段之间的连接
//                    if (phaseIdx < mainPhases.size() - 1) {
//                        String nextPhaseId = "Phase" + (phaseIdx + 1);
//                        String currentEndId = null;
//                        String nextStartId = null;
//
//                        // 确定当前阶段的结束节点
//                        if (!phaseSubTasks.isEmpty()) {
//                            // 检查是否有中间任务
//                            List<Map<String, Object>> centerTasks = phaseSubTasks.stream()
//                                    .filter(task -> !"左侧".equals(task.getOrDefault("position", "")) && !"右侧".equals(task.getOrDefault("position", "")))
//                                    .collect(Collectors.toList());
//
//                            if (!centerTasks.isEmpty()) {
//                                currentEndId = "Phase" + phaseIdx + "_C" + (centerTasks.size() - 1);
//                            } else {
//                                // 检查是否有左侧或右侧任务
//                                List<Map<String, Object>> leftTasks = phaseSubTasks.stream()
//                                        .filter(task -> "左侧".equals(task.getOrDefault("position", "")))
//                                        .collect(Collectors.toList());
//                                List<Map<String, Object>> rightTasks = phaseSubTasks.stream()
//                                        .filter(task -> "右侧".equals(task.getOrDefault("position", "")))
//                                        .collect(Collectors.toList());
//
//                                if (!leftTasks.isEmpty()) {
//                                    currentEndId = "Phase" + phaseIdx + "_L" + (leftTasks.size() - 1);
//                                } else if (!rightTasks.isEmpty()) {
//                                    currentEndId = "Phase" + phaseIdx + "_R" + (rightTasks.size() - 1);
//                                } else {
//                                    // 只有顶部任务
//                                    List<Map<String, Object>> localTopTasks = phaseSubTasks.stream()
//                                            .filter(task -> "顶部".equals(task.getOrDefault("position", "")))
//                                            .collect(Collectors.toList());
//                                    currentEndId = "Phase" + phaseIdx + "_T" + (localTopTasks.size() - 1);
//                                }
//                            }
//                        } else {
//                            // 没有子任务，跳过连接
//                            continue;
//                        }
//
//                        // 确定下一阶段的开始节点
//                        final int nextPhaseIndex = phaseIdx + 1;
//                        Map<String, Object> nextPhase = mainPhases.get(nextPhaseIndex);
//                        List<Map<String, Object>> nextPhaseSubTasks = subTasks.stream()
//                                .filter(task -> (int) task.getOrDefault("phaseIndex", -1) == nextPhaseIndex)
//                                .collect(Collectors.toList());
//
//                        if (!nextPhaseSubTasks.isEmpty()) {
//                            // 检查是否有顶部任务
//                            List<Map<String, Object>> nextTopTasks = nextPhaseSubTasks.stream()
//                                    .filter(task -> "顶部".equals(task.getOrDefault("position", "")))
//                                    .collect(Collectors.toList());
//
//                            if (!nextTopTasks.isEmpty()) {
//                                nextStartId = "Phase" + nextPhaseIndex + "_T0";
//                            } else {
//                                // 检查是否有左侧任务
//                                List<Map<String, Object>> nextLeftTasks = nextPhaseSubTasks.stream()
//                                        .filter(task -> "左侧".equals(task.getOrDefault("position", "")))
//                                        .collect(Collectors.toList());
//
//                                if (!nextLeftTasks.isEmpty()) {
//                                    nextStartId = "Phase" + nextPhaseIndex + "_L0";
//                                } else {
//                                    // 检查是否有右侧任务
//                                    List<Map<String, Object>> nextRightTasks = nextPhaseSubTasks.stream()
//                                            .filter(task -> "右侧".equals(task.getOrDefault("position", "")))
//                                            .collect(Collectors.toList());
//
//                                    if (!nextRightTasks.isEmpty()) {
//                                        nextStartId = "Phase" + nextPhaseIndex + "_R0";
//                                    } else {
//                                        // 只有中间任务
//                                        nextStartId = "Phase" + nextPhaseIndex + "_C0";
//                                    }
//                                }
//                            }
//                        } else {
//                            // 下一阶段没有子任务，跳过连接
//                            continue;
//                        }
//
//                        // 连接当前阶段的结束节点到下一阶段的开始节点
//                        mermaidCode.append("    " + currentEndId + " --> " + nextStartId + "\n");
//                    }
//                }
//            } else {
//                // 降级方案：使用默认路线图
//                return generateDefaultTechnologyRoadmap(report);
//            }
//
//            mermaidCode.append("```\n");
//            mermaidCode.append("<center><strong>图1：技术路线图</strong></center>\n");
//
//            return mermaidCode.toString();
//        } catch (Exception e) {
//            log.error("生成技术路线图失败: {}", e.getMessage());
//            // 优雅降级：返回基于关键词的默认图表
//            return generateDefaultTechnologyRoadmap(report);
//        }
//    }

    /**
     * 生成默认技术路线图（降级方案 - 符合图片格式）
     *
     * @param report 报告内容
     * @param includeCaption 是否包含图片标题
     * @return Mermaid图表代码
     */
    private String generateDefaultTechnologyRoadmap(String report, boolean includeCaption) {
        try {
            ReportKeyInfo keyInfo = extractKeyInfoFromReport(report);
            List<String> keywords = keyInfo.getKeywords();

            // 构建技术路线图
            StringBuilder mermaidCode = new StringBuilder();
            mermaidCode.append("```mermaid\n");
            mermaidCode.append("flowchart TD\n");
//            mermaidCode.append("    %% 图例说明：阶段-任务-成果\n");
//            mermaidCode.append("    %% 紫色框=研究阶段 | 蓝色框=任务节点 | 橙色框=重点阶段 | 白色框=成果产出\n");
            mermaidCode.append("\n");
            mermaidCode.append("    %% 样式定义\n");
            mermaidCode.append("    classDef dataSourceStyle fill:#F5F5F5,stroke:#666,stroke-width:2px,rx:5px\n");
            mermaidCode.append("    classDef phaseStyle fill:#E1BEE7,stroke:#333,stroke-width:2px,rx:5px\n");
            mermaidCode.append("    classDef phase2Style fill:#FFCCBC,stroke:#333,stroke-width:2px,rx:5px\n");
            mermaidCode.append("    classDef taskStyle fill:#BBDEFB,stroke:#1976D2,stroke-width:2px,rx:5px\n");
            mermaidCode.append("    classDef outcomeStyle fill:#FFFFFF,stroke:#333,stroke-width:2px,rx:5px\n");
            mermaidCode.append("\n");

            // 数据源
            mermaidCode.append("    %% 数据源层\n");
            mermaidCode.append("    DataSource0[\"结构化数据：医疗/检验\"]:::dataSourceStyle\n");
            mermaidCode.append("    DataSource1[\"非结构化数据：病程记录\"]:::dataSourceStyle\n");
            mermaidCode.append("\n");

            // 研究内容1：数据治理与知识库构建
            mermaidCode.append("    %% 研究阶段与任务\n");
            mermaidCode.append("    Phase0[\"研究内容1：数据治理与知识库构建\"]:::phaseStyle\n");
            mermaidCode.append("\n");

            // 研究内容2和并列任务
            mermaidCode.append("    Phase1[\"研究内容2：ADR事件智能识别\"]:::phaseStyle\n");
            mermaidCode.append("    Phase1_Task0[\"知识图谱\"]:::taskStyle\n");
            mermaidCode.append("    Phase1_Task1[\"知识结果\"]:::taskStyle\n");
            mermaidCode.append("\n");

            // 研究内容3：ADR风险预测与因果
            mermaidCode.append("    Phase2[\"研究内容3：ADR风险预测与因果\"]:::phaseStyle\n");
            mermaidCode.append("\n");

            // 研究内容4：智能预警系统开发与验证
            mermaidCode.append("    Phase3[\"研究内容4：智能预警系统开发与验证\"]:::phase2Style\n");
            mermaidCode.append("\n");

            // 成果产出
            mermaidCode.append("    Phase4[\"成果产出：论文/专利/系统\"]:::outcomeStyle\n");
            mermaidCode.append("\n");

            // 连接关系
            mermaidCode.append("    %% 流程连接关系\n");
            mermaidCode.append("    DataSource0 --> Phase0\n");
            mermaidCode.append("    DataSource1 --> Phase0\n");
            mermaidCode.append("    Phase0 --> Phase1\n");
            mermaidCode.append("    Phase0 --> Phase1_Task0\n");
            mermaidCode.append("    Phase1_Task0 --> Phase1_Task1\n");
            mermaidCode.append("    Phase1 -.-> Phase2\n");
            mermaidCode.append("    Phase1_Task1 --> Phase2\n");
            mermaidCode.append("    Phase2 --> Phase3\n");
            mermaidCode.append("    Phase3 --> Phase4\n");
            mermaidCode.append("\n");

            // 图例
//            mermaidCode.append("    %% 图例\n");
//            mermaidCode.append("    Legend1[\"<b>图例说明</b>\"]\n");
//            mermaidCode.append("    Legend2[\"紫色框=研究阶段\"]:::phaseStyle\n");
//            mermaidCode.append("    Legend3[\"蓝色框=任务节点\"]:::taskStyle\n");
//            mermaidCode.append("    Legend4[\"橙色框=重点阶段\"]:::phase2Style\n");
//            mermaidCode.append("    Legend5[\"白色框=成果产出\"]:::outcomeStyle\n");
//            mermaidCode.append("    Legend1 -.-> Legend2\n");
//            mermaidCode.append("    Legend1 -.-> Legend3\n");
//            mermaidCode.append("    Legend1 -.-> Legend4\n");
//            mermaidCode.append("    Legend1 -.-> Legend5\n");
//            mermaidCode.append("    style Legend1 fill:#FFFFCC,stroke:#333,stroke-width:2px,rx:5px\n");

            mermaidCode.append("```\n");
            
            // 添加标题（如果需要）
            if (includeCaption) {
                mermaidCode.append("<center><strong>图1：技术路线图</strong></center>\n");
            }

            return mermaidCode.toString();
        } catch (Exception e) {
            log.error("生成默认技术路线图失败: {}", e.getMessage());
            return "**技术路线图生成失败**：无法提取报告关键信息。";
        }
    }

    /**
     * 生成默认技术路线图（降级方案 - 向后兼容）
     *
     * @param report 报告内容
     * @return Mermaid图表代码
     */
    private String generateDefaultTechnologyRoadmap(String report) {
        return generateDefaultTechnologyRoadmap(report, true);
    }


    /**
     * 生成可行性分析表格
     * 将可行性分析具体化为详细的管理计划
     *
     * @param report 报告内容，用于动态生成表格
     * @return 可行性分析表格的Markdown代码
     */
    public String generateFeasibilityTable(String report) {
        try {
            ReportKeyInfo keyInfo = extractKeyInfoFromReport(report);
            List<String> keywords = keyInfo.getKeywords();

            // 构建表格头部
            StringBuilder table = new StringBuilder();
            table.append("| 可行性维度 | 具体内容 | 时间节点 | 负责人 | 预期成果 |\n");
            table.append("| --- | --- | --- | --- | --- |\n");

            // 技术可行性
            if (keywords.contains("机器学习") || keywords.contains("深度学习")) {
                table.append("| 技术可行性 | 完成机器学习模型开发 | 第1-3个月 | 算法工程师 | 模型原型 |\n");
                table.append("| 技术可行性 | 实现模型优化与验证 | 第4-6个月 | 算法工程师 | 优化后的模型 |\n");
            } else if (keywords.contains("知识图谱")) {
                table.append("| 技术可行性 | 完成本体构建 | 第1-2个月 | 图谱工程师 | 本体模型 |\n");
                table.append("| 技术可行性 | 实现知识图谱构建 | 第3-6个月 | 图谱工程师 | 知识图谱系统 |\n");
            } else {
                table.append("| 技术可行性 | 完成核心技术开发 | 第1-4个月 | 技术负责人 | 技术原型 |\n");
                table.append("| 技术可行性 | 实现技术优化 | 第5-6个月 | 技术团队 | 优化后的技术方案 |\n");
            }

            // 数据可行性
            if (keywords.contains("ADR") || keywords.contains("电子病历")) {
                table.append("| 数据可行性 | 完成ADR数据采集 | 第1-2个月 | 数据工程师 | ADR数据集 |\n");
                table.append("| 数据可行性 | 完成数据预处理 | 第3-4个月 | 数据工程师 | 清洗后的数据集 |\n");
            } else {
                table.append("| 数据可行性 | 完成数据采集 | 第1-2个月 | 数据工程师 | 原始数据集 |\n");
                table.append("| 数据可行性 | 完成数据清洗与标注 | 第3-4个月 | 数据工程师 | 标注后的数据集 |\n");
            }

            // 人员可行性
            table.append("| 人员可行性 | 组建研究团队 | 第1个月 | 项目负责人 | 团队名单 |\n");
            table.append("| 人员可行性 | 定期技术培训 | 每月 | 技术负责人 | 培训记录 |\n");

            // 时间可行性
            table.append("| 时间可行性 | 完成系统开发 | 第1-8个月 | 开发团队 | 原型系统 |\n");
            table.append("| 时间可行性 | 系统测试与优化 | 第9-12个月 | 测试团队 | 上线系统 |\n");

            // 资金可行性
            table.append("| 资金可行性 | 设备采购 | 第1个月 | 财务负责人 | 设备清单 |\n");
            table.append("| 资金可行性 | 软件采购 | 第2个月 | 财务负责人 | 软件清单 |\n");

            // 添加表格标题
            table.append("\n");
            table.append("<center><strong>表1：可行性分析与管理计划表</strong></center>\n");

            return table.toString();
        } catch (Exception e) {
            log.error("生成可行性分析表格失败: {}", e.getMessage());
            return "";
        }
    }

//    /**
//     * 根据内容生成对应的Mermaid图表
//     *
//     * @param content 输入内容
//     * @param chartType 图表类型
//     * @return Mermaid图表代码
//     */
//    public String generateMermaidFromContent(String content, String chartType) {
//        try {
//            // 提取关键信息
//            ReportKeyInfo keyInfo = extractKeyInfoFromReport(content);
//
//            switch (chartType.toLowerCase()) {
//                case "tech_roadmap":
//                    return generateTechnologyRoadmap(content);
//                case "research_framework":
//                    return generateResearchFrameworkChart(content);
//                case "architecture_diagram":
//                    return generateArchitectureDiagram(content);
//                case "data_retrieval":
//                    return generateDataRetrievalChart(content);
//                case "data_analysis":
//                    return generateDataAnalysisChart(content);
//                case "analysis_evaluation":
//                    return generateAnalysisEvaluationChart(content);
//                default:
//                    throw new IllegalArgumentException("Unsupported chart type: " + chartType);
//            }
//        } catch (Exception e) {
//            log.error("Failed to generate Mermaid chart from content", e);
//            return "**图表生成失败**：" + e.getMessage();
//        }
//    }

    /**
     * 解析LLM提取的参数
     *
     * @param extractedInfo LLM返回的提取信息
     * @return 解析后的参数Map
     */
    private Map<String, Object> parseExtractedParams(String extractedInfo) {
        try {
            // 提取JSON部分
            Pattern pattern = Pattern.compile("\\{[\\s\\S]*\\}");
            Matcher matcher = pattern.matcher(extractedInfo);
            if (matcher.find()) {
                String jsonStr = matcher.group(0);
                return com.alibaba.fastjson.JSONObject.parseObject(jsonStr, Map.class);
            }
            return new HashMap<>();
        } catch (Exception e) {
            log.error("解析提取的参数失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 生成数据检索图
     *
     * @param report 报告内容
     * @return Mermaid图表代码
     */
    public String generateDataRetrievalChart(String report) {
        try {
            // 调用大模型获取参数
            String extractedParams = Model.getModel(
                    MermaidPromptConstants.DATA_RETRIEVAL_PROMPT,
                    "请根据以下报告内容，" + MermaidPromptConstants.DATA_RETRIEVAL_PROMPT + "\n\n" + report,
                    "qwen-plus"
            );
            Map<String, Object> params = parseExtractedParams(extractedParams);

            // 从参数中获取动态关键词
            List<String> entityRecognitionAndKeywordSearch = (List<String>) params.getOrDefault("entityRecognitionAndKeywordSearch", Arrays.asList("实体识别模型", "检索式构建"));
            List<String> vectorSearch = (List<String>) params.getOrDefault("vectorSearch", Arrays.asList("文本向量生成", "相似度计算"));
            List<String> vectorRanking = (List<String>) params.getOrDefault("vectorRanking", Arrays.asList("精准率", "召回率", "MRR"));

            // 确保关键词数量足够
            while (entityRecognitionAndKeywordSearch.size() < 2) {
                entityRecognitionAndKeywordSearch.add("默认关键词");
            }
            while (vectorSearch.size() < 2) {
                vectorSearch.add("默认关键词");
            }
            while (vectorRanking.size() < 3) {
                vectorRanking.add("默认指标");
            }

            // 生成数据检索图Mermaid代码
            StringBuilder mermaidCode = new StringBuilder("```mermaid\ngraph TD\n    %% 最外层虚线框\n    subgraph outer [基于关键词与向量搜索的中医患者自述数据自动检索方案]\n        direction TB\n        style outer stroke-dasharray: 5 5, stroke:#333, stroke-width:2px, fill:none\n        \n        %% 左侧垂直文字\n        数据检索[数据检索]\n        style 数据检索 font-size:24px, font-weight:bold, text-align:center\n        \n        %% 第一行：两个子图并列\n        subgraph 中医药医学实体识别和关键词搜索\n            direction TB\n            A[" + entityRecognitionAndKeywordSearch.get(0) + "]\n            B[" + entityRecognitionAndKeywordSearch.get(1) + "]\n            style 中医药医学实体识别和关键词搜索 fill:#d9e6f5,stroke:#333,stroke-width:1px\n        end\n        \n        subgraph 向量搜索\n            direction TB\n            C[" + vectorSearch.get(0) + "]\n            D[" + vectorSearch.get(1) + "]\n            style 向量搜索 fill:#d9e6f5,stroke:#333,stroke-width:1px\n        end\n        \n        %% 第二行：向量排序\n        ARROW[向量排序]\n        style ARROW font-size:16px, font-weight:bold\n        \n        %% 第三行：效果评估\n        subgraph 混合式中医患者自述数据搜索方案的效果评估\n            direction LR\n            E[" + vectorRanking.get(0) + "]\n            F[" + vectorRanking.get(1) + "]\n            G[" + vectorRanking.get(2) + "]\n            style 混合式中医患者自述数据搜索方案的效果评估 fill:#d9e6f5,stroke:#333,stroke-width:1px\n        end\n        \n        %% 连接线 - 所有连接线都在虚线框内\n        A --> B\n        C --> D\n        B --> ARROW\n        D --> ARROW\n        ARROW --> E\n        ARROW --> F\n        ARROW --> G\n        \n        %% 合并连接点（"+"符号的替代方案，更美观）\n        B --- MERGE_POINT[ ]\n        D --- MERGE_POINT\n        MERGE_POINT --> ARROW\n        style MERGE_POINT opacity:0\n    end\n    \n    %% 样式定义\n    classDef section fill:#d9e6f5,stroke:#333,stroke-width:1px\n    class 中医药医学实体识别和关键词搜索,向量搜索,混合式中医患者自述数据搜索方案的效果评估 section\n    classDef nodeStyle fill:white,stroke:#333,stroke-width:1px,rx:3px\n    class A,B,C,D,ARROW,E,F,G nodeStyle\n```");

//            mermaidCode.append("    end\n");
            mermaidCode.append("```\n");
            mermaidCode.append("<center><strong>图2：数据检索图</strong></center>\n");
            return mermaidCode.toString();
        } catch (Exception e) {
            log.error("生成数据检索图失败", e);
            return "**数据检索图生成失败**：" + e.getMessage();
        }
    }

    /**
     * 生成数据分析图
     *
     * @param report 报告内容
     * @return Mermaid图表代码
     */
    public String generateDataAnalysisChart(String report) {
        try {
            // 调用大模型获取参数
            String extractedParams = Model.getModel(
                    MermaidPromptConstants.DATA_ANALYSIS_PROMPT,
                    "请根据以下报告内容，" + MermaidPromptConstants.DATA_ANALYSIS_PROMPT + "\n\n" + report,
                    "qwen-plus"
            );
            Map<String, Object> params = parseExtractedParams(extractedParams);

            // 从参数中获取动态关键词
            List<String> textClassificationAndExtraction = (List<String>) params.getOrDefault("textClassificationAndExtraction", Arrays.asList("文本分类及序列标注模型", "患者价值观提取规则", "中医实体相结合"));
            Map<String, List<String>> reportGeneration = (Map<String, List<String>>) params.getOrDefault("reportGeneration", new HashMap<>());

            // 确保关键词数量足够
            while (textClassificationAndExtraction.size() < 3) {
                textClassificationAndExtraction.add("默认过程");
            }

            // 获取报告生成的子参数集合
            List<String> subParam1 = new ArrayList<>();
            List<String> subParam2 = new ArrayList<>();

            if (!reportGeneration.isEmpty()) {
                List<String> keys = new ArrayList<>(reportGeneration.keySet());
                if (keys.size() >= 1) {
                    subParam1 = reportGeneration.get(keys.get(0));
                    if (subParam1 == null) subParam1 = new ArrayList<>();
                }
                if (keys.size() >= 2) {
                    subParam2 = reportGeneration.get(keys.get(1));
                    if (subParam2 == null) subParam2 = new ArrayList<>();
                }
            }

            // 确保子参数集合有足够的关键词
            while (subParam1.size() < 2) {
                subParam1.add("默认关键词");
            }
            while (subParam2.size() < 2) {
                subParam2.add("默认关键词");
            }

            // 生成数据分析图Mermaid代码
            StringBuilder mermaidCode = new StringBuilder("```mermaid\ngraph TD\n    subgraph outer[基于生成式人工智能技术构建智能认知的患者价值观生成模型]\n        direction TB\n        style outer fill:#d9e6f5,stroke:#000,stroke-width:1px,stroke-dasharray:5 5,padding:20\n        \n        data_analysis_text[数据分析]\n        style data_analysis_text font-size:16px,font-weight:bold,text-align:center\n        \n        subgraph dataExtraction[基于患者价值观报告框架的文本分类、筛选与关键信息提取]\n            direction LR\n            style dataExtraction fill:#d9e6f5,stroke:#333,stroke-width:1px\n            \n            A[" + textClassificationAndExtraction.get(0) + "] --> B[" + textClassificationAndExtraction.get(1) + "] --> C[" + textClassificationAndExtraction.get(2) + "]\n        end\n        \n        D[文本分类及序列标注模型效果评估: AUC、困惑度、精准率]\n        style D font-size:11px\n        \n        subgraph reportGeneration[基于患者价值观报告框架的价值观报告生成]\n            direction TB\n            style reportGeneration fill:#d9e6f5,stroke:#333,stroke-width:1px\n            \n            E[" + subParam1.get(0) + "] --> F[" + subParam2.get(0) + "]\n        end\n        \n        G[联合混合RAG的方法生成文本效果评估]\n        style G font-size:11px\n        \n        H[BLEU]\n        I[METEOR]\n    end\n    \n    A --> D\n    E --> G\n    F --> G\n    G --> H\n    G --> I\n    \n    classDef section fill:#d9e6f5,stroke:#333,stroke-width:1px\n    class dataExtraction,reportGeneration section\n    %% 调整图表尺寸\n```");

            mermaidCode.append("```\n");
            mermaidCode.append("<center><strong>图4：数据分析图</strong></center>\n");
            return mermaidCode.toString();
        } catch (Exception e) {
            log.error("生成数据分析图失败", e);
            return "**数据分析图生成失败**：" + e.getMessage();
        }
    }

    /**
     * 生成分析结果评价图
     *
     * @param report 报告内容
     * @return Mermaid图表代码
     */
    public String generateAnalysisEvaluationChart(String report) {
        try {
            // 调用大模型获取参数
            String extractedParams = Model.getModel(
                    MermaidPromptConstants.ANALYSIS_EVALUATION_PROMPT,
                    "请根据以下报告内容，" + MermaidPromptConstants.ANALYSIS_EVALUATION_PROMPT + "\n\n" + report,
                    "qwen-plus"
            );
            Map<String, Object> params = parseExtractedParams(extractedParams);

            // 从参数中获取动态关键词
            List<String> intelligentReportSystem = (List<String>) params.getOrDefault("intelligentReportSystem", Arrays.asList("自动检索", "自动分析", "自动生成"));
            List<String> effectEvaluation = (List<String>) params.getOrDefault("effectEvaluation", Arrays.asList("价值观报告", "指南共识组", "临床问题清单"));

            // 确保关键词数量足够
            while (intelligentReportSystem.size() < 3) {
                intelligentReportSystem.add("默认模块");
            }
            while (effectEvaluation.size() < 3) {
                effectEvaluation.add("默认评价项");
            }

            // 生成分析结果评价图Mermaid代码
            StringBuilder mermaidCode = new StringBuilder("```mermaid\ngraph TB\n    subgraph outer[中医指南患者价值观智能报告系统构建与效果评价]\n        direction TB\n        style outer fill:#d9e6f5,stroke:#000,stroke-width:1px,stroke-dasharray:5 5,padding:20\n        \n        analysis_text[分析结果评价]\n        style analysis_text font-size:16px,font-weight:bold,width:120px,text-align:center\n        \n        subgraph intelligentReportSystem[智能报告系统]\n            direction TB\n            style intelligentReportSystem fill:#d9e6f5,stroke:#333,stroke-width:1px\n            \n            A[" + intelligentReportSystem.get(0) + "]\n            B[" + intelligentReportSystem.get(1) + "]\n            C[" + intelligentReportSystem.get(2) + "]\n        end\n        \n        subgraph systemEvaluation[" + effectEvaluation.get(0) + "为例进行效果评价]\n            direction LR\n            style systemEvaluation fill:#d9e6f5,stroke:#333,stroke-width:1px\n            \n            subgraph reportComparison\n                direction TB\n                style reportComparison fill:none,stroke:none\n                D[" + effectEvaluation.get(0) + "1]\n                VS1[VS]\n                E[" + effectEvaluation.get(0) + "2]\n            end\n            \n            subgraph consensusComparison\n                direction TB\n                style consensusComparison fill:none,stroke:none\n                F[" + effectEvaluation.get(1) + "1]\n                RANDOM[随机]\n                G[" + effectEvaluation.get(1) + "2]\n            end\n            \n            subgraph clinicalListComparison\n                direction TB\n                style clinicalListComparison fill:none,stroke:none\n                H[" + effectEvaluation.get(2) + "1]\n                VS3[VS]\n                I[" + effectEvaluation.get(2) + "2]\n            end\n        end\n        \n        J[焦点小组访谈]\n        style J fill:#d9e6f5,stroke:#333,stroke-width:1px\n    end\n    \n    A --> D\n    B --> E\n    C --> VS1\n    D --> VS1\n    E --> VS1\n    VS1 --> J\n    F --> RANDOM\n    G --> RANDOM\n    RANDOM --> J\n    H --> VS3\n    I --> VS3\n    VS3 --> J\n    \n    classDef section fill:#d9e6f5,stroke:#333,stroke-width:1px\n    class intelligentReportSystem,systemEvaluation,J section\n    %% 调整图表尺寸\n ```");

            mermaidCode.append("```\n");
            mermaidCode.append("<center><strong>图5：分析结果评价图</strong></center>\n");
            return mermaidCode.toString();
        } catch (Exception e) {
            log.error("生成分析结果评价图失败", e);
            return "**分析结果评价图生成失败**：" + e.getMessage();
        }
    }

    /**
     * 生成技术架构图
     *
     * @param report 报告内容
     * @return Mermaid图表代码
     */
    public String generateArchitectureDiagram(String report) {
        ReportKeyInfo keyInfo = extractKeyInfoFromReport(report);

        // 生成架构图Mermaid代码
        return "```mermaid\ngraph TD\n    A[数据层] --> B[数据治理与清洗]\n    B --> C[特征工程]\n    C --> D[模型层]\n    D --> E[应用层]\n    \n    A1[临床数据] --> A\n    A2[药物数据] --> A\n    A3[文献数据] --> A\n    \n    B1[标准化] --> B\n    B2[去重] --> B\n    B3[缺失值处理] --> B\n    \n    C1[文本特征] --> C\n    C2[结构化特征] --> C\n    C3[时序特征] --> C\n    \n    D1[NLP模型] --> D\n    D2[机器学习模型] --> D\n    D3[深度学习模型] --> D\n    \n    E1[智能监测] --> E\n    E2[风险预警] --> E\n    E3[因果分析] --> E\n    E4[报告生成] --> E\n    \n    style A fill:#f9f,stroke:#333,stroke-width:2px\n    style B fill:#bbf,stroke:#333,stroke-width:2px\n    style C fill:#bfb,stroke:#333,stroke-width:2px\n    style D fill:#ff9,stroke:#333,stroke-width:2px\n    style E fill:#f9f,stroke:#333,stroke-width:2px\n```";
    }

    /**
     * 将数据转换为Markdown表格格式
     *
     * @param data 表格数据，格式为：Map<String, Map<String, String>>，其中key是行标题，value是列数据
     * @return Markdown表格字符串
     */
    public String generateMarkdownTable(Map<String, Map<String, String>> data) {
        if (data == null || data.isEmpty()) {
            return "**表格数据为空**";
        }

        // 获取所有列名
        Set<String> columns = new HashSet<>();
        for (Map<String, String> row : data.values()) {
            columns.addAll(row.keySet());
        }

        // 将列名转换为列表，确保排序一致
        List<String> columnList = new ArrayList<>(columns);
        Collections.sort(columnList);

        // 生成表格
        StringBuilder table = new StringBuilder();

        // 表头
        table.append("|");
        table.append(" 行标题 ").append("|");
        for (String column : columnList) {
            table.append(" ").append(column).append(" |");
        }
        table.append("\n");

        // 分隔线
        table.append("|");
        table.append("----------|");
        for (int i = 0; i < columnList.size(); i++) {
            table.append("----------|");
        }
        table.append("\n");

        // 数据行
        for (Map.Entry<String, Map<String, String>> rowEntry : data.entrySet()) {
            String rowTitle = rowEntry.getKey();
            Map<String, String> rowData = rowEntry.getValue();

            table.append("|");
            table.append(" ").append(rowTitle).append(" |");

            for (String column : columnList) {
                String cellValue = rowData.getOrDefault(column, "");
                table.append(" ").append(cellValue).append(" |");
            }

            table.append("\n");
        }

        return table.toString();
    }

    /**
     * 生成图片格式的图表
     * 支持PNG和SVG格式
     *
     * @param mermaidCode Mermaid图表代码
     * @param format 图片格式，支持png和svg
     * @return 图片的Base64编码
     */
    public String generateChartImage(String mermaidCode, String format) {
        try {
            // 这里使用Mermaid.js的API来生成图片
            // 实际项目中需要集成Mermaid.js的Java实现或调用外部服务
            // 暂时返回一个占位符
            log.info("生成{}格式图表图片", format);
            return "<img src=\"data:image/" + format + ";base64,占位符\" alt=\"图表\" />";
        } catch (Exception e) {
            log.error("生成图表图片失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 报告关键信息类
     * 用于存储从报告中提取的关键信息
     */
    private static class ReportKeyInfo {
        private String researchContent;
        private String researchPlan;
        private String feasibilityAnalysis;
        private List<String> keywords;

        public ReportKeyInfo() {
            this.keywords = new ArrayList<>();
        }

        public String getResearchContent() {
            return researchContent;
        }

        public void setResearchContent(String researchContent) {
            this.researchContent = researchContent;
        }

        public String getResearchPlan() {
            return researchPlan;
        }

        public void setResearchPlan(String researchPlan) {
            this.researchPlan = researchPlan;
        }

        public String getFeasibilityAnalysis() {
            return feasibilityAnalysis;
        }

        public void setFeasibilityAnalysis(String feasibilityAnalysis) {
            this.feasibilityAnalysis = feasibilityAnalysis;
        }

        public List<String> getKeywords() {
            return keywords;
        }

        public void addKeyword(String keyword) {
            this.keywords.add(keyword);
        }
    }
}
