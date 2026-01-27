package com.faers.agent.agent.impl;

import cn.hutool.json.JSONObject;
import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.dto.responseDto.FinishData;
import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.dto.responseDto.ResponseContent;
import com.faers.agent.model.Session;
import com.faers.agent.pojo.MongoLiterature;
import com.faers.agent.pojo.ProjectApplicationLiteratureDTO;
import com.faers.agent.pojo.ReportSectionResult;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.tools.BochaWebSearchTool;
import com.faers.agent.tools.FallbackWebSearchTool;
//import com.faers.agent.tools.ParallelQueryTool;
import com.faers.agent.utils.FileParsingUtils;
import com.faers.agent.utils.PaperUtils;
//import com.faers.agent.utils.MarkdownToPdfConverter;
//import com.faers.agent.utils.MarkdownToDocxConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static java.lang.Thread.sleep;

@Component
public class ProgressDisplayAgent {

    private static final Logger log = LoggerFactory.getLogger(ProgressDisplayAgent.class);

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TopicParamCollectionAgent topicParamCollectionAgent;
    @Autowired
    private TopicESQueryAgent topicESQueryAgent;
    @Autowired
    private ReportGenerationAgent reportGenerationAgent;
    @Autowired
    private FileParsingUtils fileParsingUtils;
//    @Autowired
//    private MarkdownToPdfConverter markdownToPdfConverter;
//    @Autowired
//    private MarkdownToDocxConverter markdownToDocxConverter;

    @Value("${search-api.bo-cha.key}")
    private String bochaApiKey;

    public void process(AnalysisContext context, String id, String userId, String parentId, StreamDataCallback callback) throws ExecutionException, InterruptedException {
        String traceId = context.getTraceId();
        String sessionId = context.getSessionId();
        String topicName = getTopicNameFromContext(context);
        if (topicName == null || topicName.isEmpty()) {
            throw new IllegalArgumentException("课题名称不能为空");
        }

        // 创建BochaWebSearchTool实例
        BochaWebSearchTool bochaWebSearchTool = new BochaWebSearchTool(bochaApiKey);

        // 任务状态跟踪
        Set<String> completedMainSteps = new HashSet<>();

        // 初始化任务列表
        ArrayList<String> todo = new ArrayList<>();
        todo.add("收集相关证据");
        todo.add("进行背景分析");
        todo.add("撰写申报书核心内容");
        todo.add("整理并生成最终申报书文档");

        // 任务与步骤的对应关系
        Map<String, List<String>> taskStepsMap = new HashMap<>();
        taskStepsMap.put("收集相关证据", Arrays.asList("search_web", "search_database", "integrate_results"));
        taskStepsMap.put("进行背景分析", Arrays.asList("analyze_research_background", "retrieve_relevant_evidence", "conduct_literature_review", "analyze_field_trends"));
//        taskStepsMap.put("研究优青基金申报要求和模板格式", Arrays.asList("write_report_outline", "write_report_structure"));
//        taskStepsMap.put("撰写申报书核心内容", Arrays.asList(
//                "write_report_abstract",
//                "write_research_significance",
//                "write_research_status",
//                "write_research_idea",
//                "write_application_prospect",
//                "write_research_content",
//                "write_research_objectives",
//                "write_key_scientific_problems",
//                "write_technical_route",
//                "write_research_plan",
//                "write_key_technologies",
//                "write_feasibility_analysis",
//                "write_idea_innovation",
//                "write_technology_innovation"
//        ));
        taskStepsMap.put("撰写申报书核心内容", Arrays.asList(
                "write_research_significance",
                "write_research_status",
                "write_key_scientific_problems",
                "write_research_objectives",
                "write_research_content",
                "write_technical_route",
                "write_research_plan",
                "write_feasibility_analysis",
                "write_idea_innovation",
                "write_technology_innovation",
                "write_research_basis",
                "write_work_conditions",
                "write_research_plan_and_schedule",
                "write_expected_research_outcomes",
                "write_risk_analysis_and_countermeasures"
//                "write_key_technologies",
//                "write_feasibility_analysis",
//                "write_idea_innovation",
//                "write_technology_innovation"
        ));
        taskStepsMap.put("整理并生成最终申报书文档", Arrays.asList("generate_research_framework", "generate_tech_roadmap","assemble_full_report", "standard_report_content",  "finalize_document"));

        // 发送任务开始通知
        if (callback != null) {
            // 发送任务列表
            String analysis = "课题分析进度";
            ArrayList<String> todo_details = new ArrayList<>();
            // 1. 收集相关证据
            todo_details.add("1.1 正在搜索相关的网页");
            todo_details.add("1.2 正在搜索相关的数据库");
            todo_details.add("1.3 正在整合搜索结果");
            // 2. 进行背景分析
            todo_details.add("2.1 正在分析研究背景");
            todo_details.add("2.2 正在检索相关证据");
            todo_details.add("2.3 正在进行文献综述");
            todo_details.add("2.4 正在分析领域发展趋势");
            // 3. 撰写申报书核心内容
            todo_details.add("3.1 撰写研究意义与研究背景");
            todo_details.add("3.2 正在分析国内外研究现状与发展趋势");
            todo_details.add("3.3 正在拟解决的关键科学问题");
            todo_details.add("3.4 正在明确研究目标");
            todo_details.add("3.5 正在明确研究内容");
            todo_details.add("3.6 正在制定技术路线");
            todo_details.add("3.7 正在设计研究方案");
            todo_details.add("3.8 正在进行可行性分析");
            todo_details.add("3.9 正在总结思路创新");
            todo_details.add("3.10 正在总结技术创新");
            todo_details.add("3.11 正在撰写研究基础");
            todo_details.add("3.12 正在撰写工作条件");
            todo_details.add("3.13 正在撰写研究计划与进度安排");
            todo_details.add("3.14 正在撰写预期研究成果");
            todo_details.add("3.15 正在撰写风险分析与对策");
            // 4. 整理并生成最终申报书文档
            todo_details.add("4.1 正在生成研究框架图");
            todo_details.add("4.2 正在生成技术路线图");
            todo_details.add("4.3 正在整理报告内容");
            todo_details.add("4.4 正在规范报告格式");
            todo_details.add("4.5 正在生成最终申报书文档");

            Response plannerData = Response.itemBuilder(analysis, todo, todo_details);
            Response plannerData2 = Response.itemBuilder2(analysis, todo, todo_details);
            callback.onData(plannerData);
            callback.onData(plannerData2);

            // 初始状态设置
            ArrayList<String> status = new ArrayList<>(Collections.nCopies(todo.size(), "todo"));
            status.set(0, "doing");
            Response initialStatus = Response.itemInfoBuilder(todo, status);
            callback.onData(initialStatus);
        }

        try {
            // =============== Step 1.1: 正在搜索相关的网页 ===============
            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder("正在搜索相关的网页", "正在搜索相关的网页", callId, "");
                callback.onData(statusResponse);
            }

            sleep(2000);
            List<JSONObject> webResult = null;
            String webSearchSource = "none"; // 记录实际使用的搜索引擎

            try {
                webResult = getWebResult(callback, bochaWebSearchTool, topicName);
                if (webResult != null && !webResult.isEmpty()) {
                    webSearchSource = "bocha";
                    // 为 bocha 搜索结果添加 source 标记
                    for (JSONObject result : webResult) {
                        if (result != null && !result.containsKey("source")) {
                            result.set("source", "bocha");
                        }
                    }
                }
            } catch (Exception e) {
                log.error("BOCHA_SEARCH_ERROR | traceId={} | error={}", traceId, e.getMessage());
                webResult = null;
            }

            // 降级策略：当 bocha 搜索结果为空时，使用免费搜索工具
            if (webResult == null || webResult.isEmpty()) {
                log.warn("BOCHA_SEARCH_EMPTY | traceId={} | Falling back to free search tool", traceId);

                try {
                    // 使用降级搜索工具
                    FallbackWebSearchTool fallbackTool = new FallbackWebSearchTool();
                    webResult = fallbackTool.searchWeb(topicName, callback);

                    if (webResult != null && !webResult.isEmpty()) {
                        // 从结果中识别实际使用的搜索引擎
                        JSONObject firstResult = webResult.get(0);
                        if (firstResult != null && firstResult.containsKey("source")) {
                            webSearchSource = firstResult.getStr("source");
                        } else {
                            webSearchSource = "fallback";
                        }

                        log.info("FALLBACK_SEARCH_SUCCESS | traceId={} | source={} | resultCount={}",
                                traceId, webSearchSource, webResult.size());

                        // 发送降级搜索成功消息
                        if (callback != null) {
                            // 格式化降级搜索结果
                            StringBuilder fallbackResultContent = new StringBuilder();
                            fallbackResultContent.append("📚 参考信息：\n\n");

                            int displayCount = Math.min(webResult.size(), 5);
                            for (int i = 0; i < displayCount; i++) {
                                JSONObject obj = webResult.get(i);
                                String name = obj.getStr("name");
                                String url = obj.getStr("url");

                                if (name != null && !name.isEmpty() && url != null && !url.isEmpty()) {
                                    // 使用 Markdown 链接格式，只显示名称，点击可跳转
                                    fallbackResultContent.append((i + 1)).append(". [")
                                            .append(name)
                                            .append("](")
                                            .append(url)
                                            .append(")\n\n");
                                }
                            }

                            Response fallbackResult = Response.ResponseContentChatBuilder(
                                fallbackResultContent.toString(),
                                "agent"
                            );
                            callback.onData(fallbackResult);
                        }
                    } else {
                        log.error("FALLBACK_SEARCH_FAILED | traceId={} | Both primary and fallback search returned empty", traceId);

                        if (callback != null) {
                            Response errorNotice = Response.ResponseContentChatBuilder(
                                "⚠️ 网页搜索暂时不可用，将继续进行数据库检索...\n",
                                "agent"
                            );
                            callback.onData(errorNotice);
                        }

                        // 初始化为空列表，避免后续空指针异常
                        webResult = new ArrayList<>();
                    }
                } catch (Exception e) {
                    log.error("FALLBACK_SEARCH_EXCEPTION | traceId={} | error={}", traceId, e.getMessage(), e);

                    if (callback != null) {
                        Response errorNotice = Response.ResponseContentChatBuilder(
                            "⚠️ 网页搜索暂时不可用，将继续进行数据库检索...\n",
                            "agent"
                        );
                        callback.onData(errorNotice);
                    }

                    // 初始化为空列表，避免后续空指针异常
                    webResult = new ArrayList<>();
                }
            }

            // 确保 webResult 不为 null
            if (webResult == null) {
                webResult = new ArrayList<>();
            }

            // 保存搜索源信息到context，供后续使用
            context.setWebSearchSource(webSearchSource);

            completedMainSteps.add("search_web");

            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowResultBuilder("已完成正在搜索相关的网页", callId);
                callback.onData(statusResponse);
            }

            // =============== Step 1.2: 正在搜索相关的数据库 ===============
            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder("正在搜索相关的数据库", "正在搜索相关的数据库", callId, "");
                callback.onData(statusResponse);
            }

            getEsResult(context, id, userId, parentId, callback, traceId);
            String cleanJson;

            completedMainSteps.add("search_database");

            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowResultBuilder("已完成正在搜索相关的数据库", callId);
                callback.onData(statusResponse);
            }

            // =============== Step 1.3: 正在整合搜索结果 ===============
            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder("正在整合搜索结果", "正在整合搜索结果", callId, "");
                callback.onData(statusResponse);
            }

            getResult(context, webResult, traceId);

            // 获取整合后的结果，提取文献数量信息
            getLiterature(context, callback, traceId);

            sleep(5000);
            completedMainSteps.add("integrate_results");

            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowResultBuilder("已完成正在整合搜索结果", callId);
                callback.onData(statusResponse);

                // 更新任务状态：收集相关证据完成
            ArrayList<String> status = new ArrayList<>(Collections.nCopies(todo.size(), "todo"));
            status.set(0, "done");
            status.set(1, "doing");
            Response taskStatus = Response.itemInfoBuilder(todo, status);
            callback.onData(taskStatus);
        }

        // =============== Step 2.1: 正在分析研究背景 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("2.1 正在分析研究背景", "正在分析研究背景", callId, "");
            callback.onData(statusResponse);
        }

        // 调用大模型生成研究背景分析
        String researchBackground = reportGenerationAgent.generateResearchBackground(context, callback);
        log.info("RESEARCH_BACKGROUND_GENERATED | traceId={} | length={}", traceId, researchBackground.length());
        completedMainSteps.add("analyze_research_background");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成2.1 正在分析研究背景", callId);
            callback.onData(statusResponse);
        }

        // =============== Step 2.2: 正在检索相关证据 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("2.2 正在检索相关证据", "正在检索相关证据", callId, "");
            callback.onData(statusResponse);
        }

        // 调用大模型生成证据检索分析
        String evidenceRetrieval = reportGenerationAgent.generateEvidenceRetrieval(context, callback);
        log.info("EVIDENCE_RETRIEVAL_GENERATED | traceId={} | length={}", traceId, evidenceRetrieval.length());
        completedMainSteps.add("retrieve_relevant_evidence");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成2.2 正在检索相关证据", callId);
            callback.onData(statusResponse);
        }

        // =============== Step 2.3: 正在进行文献综述 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("2.3 正在进行文献综述", "正在进行文献综述", callId, "");
            callback.onData(statusResponse);
        }

        // 调用大模型生成文献综述
        String literatureReview = reportGenerationAgent.generateLiteratureReview(context, callback);
        log.info("LITERATURE_REVIEW_GENERATED | traceId={} | length={}", traceId, literatureReview.length());
        completedMainSteps.add("conduct_literature_review");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成2.3 正在进行文献综述", callId);
            callback.onData(statusResponse);
        }

        // =============== Step 2.4: 正在分析领域发展趋势 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("2.4 正在分析领域发展趋势", "正在分析领域发展趋势", callId, "");
            callback.onData(statusResponse);
        }

        // 调用大模型生成领域发展趋势分析
        String fieldTrends = reportGenerationAgent.generateFieldTrends(context, callback);
        log.info("FIELD_TRENDS_GENERATED | traceId={} | length={}", traceId, fieldTrends.length());
        completedMainSteps.add("analyze_field_trends");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成2.4 正在分析领域发展趋势", callId);
            callback.onData(statusResponse);

            // 更新任务状态：背景分析完成
            ArrayList<String> status = new ArrayList<>(Collections.nCopies(todo.size(), "todo"));
            status.set(0, "done");
            status.set(1, "done");
            status.set(2, "doing");
            Response taskStatus = Response.itemInfoBuilder(todo, status);
            callback.onData(taskStatus);
        }

//        // =============== Step 3.1: 正在编写项目申报要求 ===============
//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowBuilder("3.1 正在编写项目申报要求", "正在编写项目申报要求", callId, "");
//            callback.onData(statusResponse);
//        }
//
//        //返回项目申报要求
//        String fundRequirements = "# 项目申报要求 \n" +
//                "\n## 关键要点 \n" +
//                "\n### 1. 研究属性分类 \n" +
//                "- **自由探索类基础研究**：选题源于科研人员好奇心或创新性学术灵感，不以满足现阶段应用需求为目的的原创性、前沿性基础研究 \n" +
//                "- **目标导向类基础研究**：以经济社会发展需要或国家需求为牵引的基础研究 \n" +
//                "\n### 2. 申请要求 \n" +
//                "- 全面实行无纸化申请 \n" +
//                "- 申请书一律采用在线方式撰写 \n" +
//                "- 准确选择申报学科领域 \n" +
//                "- 准确选择\"团队情况\"\"研究方向\"\"输出格式\" \n" +
//                "\n### 3. 科研诚信要求 \n" +
//                "- 严禁抄袭剽窃 \n" +
//                "- 严禁弄虚作假 \n" +
//                "- 严禁违反法律法规、伦理准则及科技安全规定 \n" +
//                "- 申请人应对所提交申请材料的真实性、合法性负责 \n" +
//                "- 如实填报个人信息 \n" +
//                "\n### 4. 申请书内容禁止事项 \n" +
//                "- 不得出现违反法律法规的内容 \n" +
//                "- 不得含有涉密信息、敏感信息 \n" +
//                "- 严禁抄袭剽窃（包括学术观点、研究思路、研究方案、文字表述等） \n" +
//                "\n### 5. 提交要求 \n" +
//                "- 依托单位应在申请截止时间前通过信息系统逐项确认提交电子申请书及附件材料 \n" +
//                "- 项目获批准后，需将申请书的纸质签字盖章页装订在《项目计划书》最后 \n" +
//                "- 需要提交的附件材料：证明信、推荐信和其他需要特别说明的材料（电子扫描件上传） \n";
//
//        sleep(2000);
//        if (callback != null) {
//            // 字符流式返回给前端，每次发送累积的字符串
//            StringBuilder accumulatedContent = new StringBuilder();
//            for (char c : fundRequirements.toCharArray()) {
//                // 将当前字符添加到累积字符串中
//                accumulatedContent.append(c);
//                // 通过Response.ResponseContentChatBuilder流式返回累积的字符串
//                Response charResponse = Response.ResponseReportChatBuilder(accumulatedContent.toString(), "agent");
//                callback.onData(charResponse);
//                // 添加20毫秒延迟，模拟真实的流式效果
//                try {
//                    Thread.sleep(20); // 20毫秒延迟，根据要求设置
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                    break;
//                }
//            }
//        }
//
//        completedMainSteps.add("write_report_outline");
//
//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowResultBuilder("已完成正在编写项目申报要求", callId);
//            callback.onData(statusResponse);
//        }

//        // =============== Step 3.2: 正在编写项目申请书标准结构 ===============
//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowBuilder("3.2 正在编写项目申请书标准结构", "正在编写项目申请书标准结构", callId, "");
//            callback.onData(statusResponse);
//        }
//
//
//        // 返回优秀青年科学基金项目申请书标准结构内容
//        String applicationStructure = "# 项目申请书标准结构 \n" +
//                "\n## 申请书组成部分 \n" +
//                "\n 项目申请书由以下五部分构成： \n" +
//                "\n### 1、研究意义 \n" +
//                "- 融入患者价值观的指南能为临床提供复杂医疗决策框架和参考，降低决策负担，推动以患者为中心的医疗实践 \n" +
//                "- 传统患者价值观信息获取方式阻碍了价值观与指南融合 \n" +
//                "- 利用网络患者自述数据和生成式人工智能打通指南与患者价值观融合的壁垒 \n" +
//                "\n### 2、国内外研究现状分析及存在问题 \n" +
//                "- 中医指南融入患者价值观现状及瓶颈 \n" +
//                "- 中医指南患者价值观信息获取的新思路 \n" +
//                "- 依托生成式人工智能助力网络患者价值观信息高效利用 \n" +
//                "\n### 3、研究思路 \n" +
//                "\n### 4、应用方向或应用前景 \n" +
//                "\n#### 2.项目的研究内容、研究目标、以及拟解决的关键科学问题 \n" +
//                "- 研究内容 \n" +
//                "- 研究目标 \n" +
//                "- 拟解决的关键科学问题 \n" +
//                "\n#### 3. 拟采取的研究方案及可行性分析 \n" +
//                "- 技术路线 \n" +
//                "- 研究方案 \n" +
//                "- 关键技术 \n" +
//                "- 可行性分析 \n" +
//                "\n#### 4. 本项目的特色与创新之处 \n" +
//                "- 学术创新 \n" +
//                "- 技术创新 \n" +
//                "\n## 优秀青年科学基金项目特殊要求 \n" +
//                "\n 1. **重点考察**：申请人的工作基础和创新潜力 \n" +
//                " 2. **撰写重点**： \n" +
//                "   - 工作基础方面：重点阐述申请人所取得的研究成果的创新性和科学价值 \n" +
//                "   - 创新潜力方面：重点阐述申请人拟开展研究工作的科学意义和创新性 \n" +
//                "\n## 撰写注意事项 \n" +
//                "\n 1. 立项依据是体现项目科学性、创新性的最重要环节 \n" +
//                " 2. 研究目标要明确具体，避免泛泛探索规律的研究 \n" +
//                " 3. 研究内容要突出重点，紧紧围绕研究目标 \n" +
//                " 4. 技术路线要清晰可行 \n" +
//                " 5. 特色与创新要突出，有说服力 \n" +
//                " 6. 研究基础要充分，能支撑项目的顺利实施 \n";
//
//        sleep(2000);
//        if (callback != null) {
//            // 字符流式返回给前端，每次发送累积的字符串
//            StringBuilder accumulatedContent = new StringBuilder();
//            for (char c : applicationStructure.toCharArray()) {
//                // 将当前字符添加到累积字符串中
//                accumulatedContent.append(c);
//                // 通过Response.ResponseContentChatBuilder流式返回累积的字符串
//                Response charResponse = Response.ResponseReportChatBuilder(accumulatedContent.toString(), "agent");
//                callback.onData(charResponse);
//                // 添加20毫秒延迟，模拟真实的流式效果
//                try {
//                    Thread.sleep(20); // 20毫秒延迟，与Step 3.1保持一致
//                } catch (InterruptedException e) {
//                    Thread.currentThread().interrupt();
//                    break;
//                }
//            }
//        }
//
//        completedMainSteps.add("write_report_structure");
//
//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowResultBuilder("已完成正在编写项目申请书标准结构", callId);
//            callback.onData(statusResponse);
//
//            // 更新任务状态：研究优青基金申报要求和模板格式完成
//            ArrayList<String> status = new ArrayList<>(Collections.nCopies(todo.size(), "todo"));
//            status.set(0, "done");
//            status.set(1, "done");
//            status.set(2, "done");
//            status.set(3, "doing");
//            Response taskStatus = Response.itemInfoBuilder(todo, status);
//            callback.onData(taskStatus);
//        }

        // 用于存储所有章节内容
        List<String> reportSections = new ArrayList<>();
        // 用于存储所有章节的摘要
        List<String> reportSummaries = new ArrayList<>();

//        // =============== 生成项目基本信息表格 ===============
////        if (callback != null) {
////            String callId = UUID.randomUUID().toString();
////            Response statusResponse = Response.flowBuilder("生成项目基本信息表格", "生成项目基本信息表格", callId, "");
////            callback.onData(statusResponse);
////        }
//
//        // 生成项目基本信息表格
//        String projectInfoTable = generateProjectInfoTable(context);
//        reportSections.add(projectInfoTable);

//        // =============== Step 4.1: 正在生成报告摘要 ===============
//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowBuilder("4.1 正在生成报告摘要", "正在生成报告摘要", callId, "");
//            callback.onData(statusResponse);
//        }
//
//        // 生成报告摘要（流式输出）
//        String abstractSection = reportGenerationAgent.generateAbstract(context, callback);
//        // 分离报告内容和摘要
//        ReportSectionResult abstractResult = reportGenerationAgent.separateReportContentAndSummary(abstractSection);
//        if (abstractResult.getReportContent() != null && !abstractResult.getReportContent().isEmpty()) {
//            reportSections.add(abstractResult.getReportContent());
//        }
//        if (abstractResult.getSummary() != null && !abstractResult.getSummary().isEmpty()) {
//            reportSummaries.add(abstractResult.getSummary());
//        }
//        completedMainSteps.add("write_report_abstract");
//
//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowResultBuilder("已完成正在生成报告摘要", callId);
//            callback.onData(statusResponse);
//        }



        // =============== Step 4.1: 撰写研究意义与研究背景 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("3.1 正在撰写研究意义", "正在撰写研究意义", callId, "");
            callback.onData(statusResponse);
        }

        // 拼接已生成的报告内容，作为上下文传递给下一步
//        String previousReportContent = String.join("\n\n", reportSections);
//        context.getQueryParams().put("previous_report_content", previousReportContent);

        // 撰写研究意义（流式输出）
        String significanceSection = reportGenerationAgent.generateResearchSignificance(context, callback);
        // 分离报告内容和摘要
        ReportSectionResult significanceResult = reportGenerationAgent.separateReportContentAndSummary(significanceSection);
        if (significanceResult.getReportContent() != null && !significanceResult.getReportContent().isEmpty()) {
            reportSections.add(significanceResult.getReportContent());
        }
        if (significanceResult.getSummary() != null && !significanceResult.getSummary().isEmpty()) {
            reportSummaries.add(significanceResult.getSummary());
        }
        completedMainSteps.add("write_research_significance");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成正在撰写研究意义", callId);
            callback.onData(statusResponse);
        }

        // =============== Step 3.2: 正在分析国内外研究现状与发展趋势 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("3.2 正在分析国内外研究现状及存在问题", "正在分析国内外研究现状及存在问题", callId, "");
            callback.onData(statusResponse);
        }

        // 拼接已生成的报告内容，作为上下文传递给下一步
        String previousReportContent = String.join("\n\n", reportSections);
        context.getQueryParams().put("previous_report_content", previousReportContent);

        // 分析研究现状（流式输出）
        String statusSection = reportGenerationAgent.generateResearchStatus(context, callback);
        // 分离报告内容和摘要
        ReportSectionResult statusResult = reportGenerationAgent.separateReportContentAndSummary(statusSection);
        if (statusResult.getReportContent() != null && !statusResult.getReportContent().isEmpty()) {
            reportSections.add(statusResult.getReportContent());
        }
        if (statusResult.getSummary() != null && !statusResult.getSummary().isEmpty()) {
            reportSummaries.add(statusResult.getSummary());
        }
        completedMainSteps.add("write_research_status");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成正在分析国内外研究现状及存在问题", callId);
            callback.onData(statusResponse);
        }


            // =============== Step 3.3: 正在拟解决的关键科学问题 ===============
            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder("3.3 正在拟解决的关键科学问题", "正在拟解决的关键科学问题", callId, "");
                callback.onData(statusResponse);
            }

            // 拼接已生成的报告内容，作为上下文传递给下一步
            previousReportContent = String.join("\n\n", reportSections);
            context.getQueryParams().put("previous_report_content", previousReportContent);

            // 拟解决的关键科学问题（流式输出）
            String problemsSection = reportGenerationAgent.generateKeyScientificProblems(context, callback);
            // 分离报告内容和摘要
            ReportSectionResult problemsResult = reportGenerationAgent.separateReportContentAndSummary(problemsSection);
            if (problemsResult.getReportContent() != null && !problemsResult.getReportContent().isEmpty()) {
                reportSections.add(problemsResult.getReportContent());
            }
            if (problemsResult.getSummary() != null && !problemsResult.getSummary().isEmpty()) {
                reportSummaries.add(problemsResult.getSummary());
            }
            completedMainSteps.add("write_key_scientific_problems");

            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowResultBuilder("已完成正在拟解决的关键科学问题", callId);
                callback.onData(statusResponse);
            }


            // =============== Step 3.4: 正在明确研究目标 ===============
            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder("3.4 正在明确研究目标", "正在明确研究目标", callId, "");
                callback.onData(statusResponse);
            }

            // 拼接已生成的报告内容，作为上下文传递给下一步
            previousReportContent = String.join("\n\n", reportSections);
            context.getQueryParams().put("previous_report_content", previousReportContent);

            // 明确研究目标（流式输出）
            String objectivesSection = reportGenerationAgent.generateResearchObjectives(context, callback);
            // 分离报告内容和摘要
            ReportSectionResult objectivesResult = reportGenerationAgent.separateReportContentAndSummary(objectivesSection);
            if (objectivesResult.getReportContent() != null && !objectivesResult.getReportContent().isEmpty()) {
                reportSections.add(objectivesResult.getReportContent());
            }
            if (objectivesResult.getSummary() != null && !objectivesResult.getSummary().isEmpty()) {
                reportSummaries.add(objectivesResult.getSummary());
            }
            completedMainSteps.add("write_research_objectives");

            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowResultBuilder("已完成正在明确研究目标", callId);
                callback.onData(statusResponse);
            }



            // =============== Step 3.5: 正在明确研究内容 ===============
            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder("3.5 正在明确研究内容", "正在明确研究内容", callId, "");
                callback.onData(statusResponse);
            }

            // 拼接已生成的报告内容，作为上下文传递给下一步
            previousReportContent = String.join("\n\n", reportSections);
            context.getQueryParams().put("previous_report_content", previousReportContent);

            // 明确研究内容（流式输出）
            String contentSection = reportGenerationAgent.generateResearchContent(context, callback);
            // 分离报告内容和摘要
            ReportSectionResult contentResult = reportGenerationAgent.separateReportContentAndSummary(contentSection);
            if (contentResult.getReportContent() != null && !contentResult.getReportContent().isEmpty()) {
                reportSections.add(contentResult.getReportContent());
            }
            if (contentResult.getSummary() != null && !contentResult.getSummary().isEmpty()) {
                reportSummaries.add(contentResult.getSummary());
            }

            String literature = context.getLiterature();
            if(literature !=null && !literature.isEmpty()){
                // 截取前30条文献
                String[] literatureArray = literature.split("\\n\\n");
                StringBuilder truncatedLiterature = new StringBuilder();
                int limit = Math.min(literatureArray.length, 30);
                for (int i = 0; i < limit; i++) {
                    truncatedLiterature.append(literatureArray[i]);
                    if (i < limit - 1) {
                        truncatedLiterature.append("\n\n");
                    }
                }
                reportSections.add(truncatedLiterature.toString());
            }
            completedMainSteps.add("write_research_content");

            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowResultBuilder("已完成正在明确研究内容", callId);
                callback.onData(statusResponse);
            }



//        // =============== Step 4.3: 正在梳理研究思路 ===============
//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowBuilder("4.4 正在梳理研究思路", "正在梳理研究思路", callId, "");
//            callback.onData(statusResponse);
//        }
//
//        // 拼接已生成的报告内容，作为上下文传递给下一步
//         previousReportContent = String.join("\n\n", reportSections);
//        context.getQueryParams().put("previous_report_content", previousReportContent);
//
//        // 梳理研究思路（流式输出）
//        String ideaSection = reportGenerationAgent.generateResearchIdea(context, callback);
//        // 分离报告内容和摘要
//        ReportSectionResult ideaResult = reportGenerationAgent.separateReportContentAndSummary(ideaSection);
//        if (ideaResult.getReportContent() != null && !ideaResult.getReportContent().isEmpty()) {
//            reportSections.add(ideaResult.getReportContent());
//        }
//        if (ideaResult.getSummary() != null && !ideaResult.getSummary().isEmpty()) {
//            reportSummaries.add(ideaResult.getSummary());
//        }
//        completedMainSteps.add("write_research_idea");
//
//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowResultBuilder("已完成正在梳理研究思路", callId);
//            callback.onData(statusResponse);
//        }

//        // =============== Step 4.5: 正在分析应用方向或应用前景 ===============
//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowBuilder("4.5 正在分析应用方向或应用前景", "正在分析应用方向或应用前景", callId, "");
//            callback.onData(statusResponse);
//        }
//
//        // 拼接已生成的报告内容，作为上下文传递给下一步
//         previousReportContent = String.join("\n\n", reportSections);
//        context.getQueryParams().put("previous_report_content", previousReportContent);
//
//        // 分析应用前景（流式输出）
//        String prospectSection = reportGenerationAgent.generateApplicationProspect(context, callback);
//        // 分离报告内容和摘要
//        ReportSectionResult prospectResult = reportGenerationAgent.separateReportContentAndSummary(prospectSection);
//        if (prospectResult.getReportContent() != null && !prospectResult.getReportContent().isEmpty()) {
//            reportSections.add(prospectResult.getReportContent());
//        }
//        if (prospectResult.getSummary() != null && !prospectResult.getSummary().isEmpty()) {
//            reportSummaries.add(prospectResult.getSummary());
//        }
//        completedMainSteps.add("write_application_prospect");
//
//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowResultBuilder("已完成正在分析应用方向或应用前景", callId);
//            callback.onData(statusResponse);
//        }







        // =============== Step 4.6: 正在制定技术路线 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("3.6 正在制定技术路线", "正在制定技术路线", callId, "");
            callback.onData(statusResponse);
        }

        // 拼接已生成的报告内容，作为上下文传递给下一步
         previousReportContent = String.join("\n\n", reportSections);
        context.getQueryParams().put("previous_report_content", previousReportContent);

        // 制定技术路线（流式输出）
        String routeSection = reportGenerationAgent.generateTechnicalRoute(context, callback);
        // 分离报告内容和摘要
        ReportSectionResult routeResult = reportGenerationAgent.separateReportContentAndSummary(routeSection);
        if (routeResult.getReportContent() != null && !routeResult.getReportContent().isEmpty()) {
            reportSections.add(routeResult.getReportContent());
        }
        if (routeResult.getSummary() != null && !routeResult.getSummary().isEmpty()) {
            reportSummaries.add(routeResult.getSummary());
        }
        completedMainSteps.add("write_technical_route");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成正在制定技术路线", callId);
            callback.onData(statusResponse);
        }

        // =============== Step 4.7: 正在设计研究方案 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("3.7 正在设计研究方案", "正在设计研究方案", callId, "");
            callback.onData(statusResponse);
        }

        // 拼接已生成的报告内容，作为上下文传递给下一步
         previousReportContent = String.join("\n\n", reportSections);
        context.getQueryParams().put("previous_report_content", previousReportContent);

        // 设计研究方案（流式输出）
        String planSection = reportGenerationAgent.generateResearchPlan(context, callback);
        // 分离报告内容和摘要
        ReportSectionResult planResult = reportGenerationAgent.separateReportContentAndSummary(planSection);
        if (planResult.getReportContent() != null && !planResult.getReportContent().isEmpty()) {
            reportSections.add(planResult.getReportContent());
        }
        if (planResult.getSummary() != null && !planResult.getSummary().isEmpty()) {
            reportSummaries.add(planResult.getSummary());
        }
        completedMainSteps.add("write_research_plan");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成正在设计研究方案", callId);
            callback.onData(statusResponse);
        }


            // =============== Step 4.8: 正在进行可行性分析 ===============
            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder("3.8 正在进行可行性分析", "正在进行可行性分析", callId, "");
                callback.onData(statusResponse);
            }

            // 拼接已生成的报告内容，作为上下文传递给下一步
            previousReportContent = String.join("\n\n", reportSections);
            context.getQueryParams().put("previous_report_content", previousReportContent);

            // 进行可行性分析（流式输出）
            String feasibilitySection = reportGenerationAgent.generateFeasibilityAnalysis(context, callback);
            // 分离报告内容和摘要
            ReportSectionResult feasibilityResult = reportGenerationAgent.separateReportContentAndSummary(feasibilitySection);
            if (feasibilityResult.getReportContent() != null && !feasibilityResult.getReportContent().isEmpty()) {
                reportSections.add(feasibilityResult.getReportContent());
            }
            if (feasibilityResult.getSummary() != null && !feasibilityResult.getSummary().isEmpty()) {
                reportSummaries.add(feasibilityResult.getSummary());
            }
            completedMainSteps.add("write_feasibility_analysis");

            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowResultBuilder("已完成正在进行可行性分析", callId);
                callback.onData(statusResponse);
            }



//        // =============== Step 4.11: 正在明确关键技术 ===============
//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowBuilder("4.11 正在明确关键技术", "正在明确关键技术", callId, "");
//            callback.onData(statusResponse);
//        }
//
//        // 拼接已生成的报告内容，作为上下文传递给下一步
//         previousReportContent = String.join("\n\n", reportSections);
//        context.getQueryParams().put("previous_report_content", previousReportContent);
//
//        // 明确关键技术（流式输出）
//        String technologiesSection = reportGenerationAgent.generateKeyTechnologies(context, callback);
//        // 分离报告内容和摘要
//        ReportSectionResult technologiesResult = reportGenerationAgent.separateReportContentAndSummary(technologiesSection);
//        if (technologiesResult.getReportContent() != null && !technologiesResult.getReportContent().isEmpty()) {
//            reportSections.add(technologiesResult.getReportContent());
//        }
//        if (technologiesResult.getSummary() != null && !technologiesResult.getSummary().isEmpty()) {
//            reportSummaries.add(technologiesResult.getSummary());
//        }
//        completedMainSteps.add("write_key_technologies");
//
//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowResultBuilder("已完成正在明确关键技术", callId);
//            callback.onData(statusResponse);
//        }



        // =============== Step 3.9: 正在总结思路创新 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("3.9 正在总结思路创新", "正在总结思路创新", callId, "");
            callback.onData(statusResponse);
        }

        // 拼接已生成的报告内容，作为上下文传递给下一步
         previousReportContent = String.join("\n\n", reportSections);
        context.getQueryParams().put("previous_report_content", previousReportContent);

        // 总结思路创新（流式输出）
        String ideaInnovationSection = reportGenerationAgent.generateIdeaInnovation(context, callback);
        // 分离报告内容和摘要
        ReportSectionResult ideaInnovationResult = reportGenerationAgent.separateReportContentAndSummary(ideaInnovationSection);
        if (ideaInnovationResult.getReportContent() != null && !ideaInnovationResult.getReportContent().isEmpty()) {
            reportSections.add(ideaInnovationResult.getReportContent());
        }
        if (ideaInnovationResult.getSummary() != null && !ideaInnovationResult.getSummary().isEmpty()) {
            reportSummaries.add(ideaInnovationResult.getSummary());
        }
        completedMainSteps.add("write_idea_innovation");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成正在总结思路创新", callId);
            callback.onData(statusResponse);
        }

        // =============== Step 3.10: 正在总结技术创新 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("3.10 正在总结技术创新", "正在总结技术创新", callId, "");
            callback.onData(statusResponse);
        }

        // 拼接已生成的报告内容，作为上下文传递给下一步
         previousReportContent = String.join("\n\n", reportSections);
        context.getQueryParams().put("previous_report_content", previousReportContent);

        // 总结技术创新（流式输出）
        String technologyInnovationSection = reportGenerationAgent.generateTechnologyInnovation(context, callback);
        // 分离报告内容和摘要
        ReportSectionResult technologyInnovationResult = reportGenerationAgent.separateReportContentAndSummary(technologyInnovationSection);
        if (technologyInnovationResult.getReportContent() != null && !technologyInnovationResult.getReportContent().isEmpty()) {
            reportSections.add(technologyInnovationResult.getReportContent());
        }
        if (technologyInnovationResult.getSummary() != null && !technologyInnovationResult.getSummary().isEmpty()) {
            reportSummaries.add(technologyInnovationResult.getSummary());
        }
        completedMainSteps.add("write_technology_innovation");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成正在总结技术创新", callId);
            callback.onData(statusResponse);
        }

        // =============== Step 3.11: 正在撰写研究基础 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("3.11 正在撰写研究基础", "正在撰写研究基础", callId, "");
            callback.onData(statusResponse);
        }

        previousReportContent = String.join("\n\n", reportSections);
        context.getQueryParams().put("previous_report_content", previousReportContent);

        String researchBasisSection = reportGenerationAgent.generateResearchBasis(context, callback);
        ReportSectionResult researchBasisResult = reportGenerationAgent.separateReportContentAndSummary(researchBasisSection);
        if (researchBasisResult.getReportContent() != null && !researchBasisResult.getReportContent().isEmpty()) {
            reportSections.add(researchBasisResult.getReportContent());
        }
        if (researchBasisResult.getSummary() != null && !researchBasisResult.getSummary().isEmpty()) {
            reportSummaries.add(researchBasisResult.getSummary());
        }
        completedMainSteps.add("write_research_basis");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成正在撰写研究基础", callId);
            callback.onData(statusResponse);
        }

        // =============== Step 3.12: 正在撰写工作条件 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("3.12 正在撰写工作条件", "正在撰写工作条件", callId, "");
            callback.onData(statusResponse);
        }

        previousReportContent = String.join("\n\n", reportSections);
        context.getQueryParams().put("previous_report_content", previousReportContent);

        String workConditionsSection = reportGenerationAgent.generateWorkConditions(context, callback);
        ReportSectionResult workConditionsResult = reportGenerationAgent.separateReportContentAndSummary(workConditionsSection);
        if (workConditionsResult.getReportContent() != null && !workConditionsResult.getReportContent().isEmpty()) {
            reportSections.add(workConditionsResult.getReportContent());
        }
        if (workConditionsResult.getSummary() != null && !workConditionsResult.getSummary().isEmpty()) {
            reportSummaries.add(workConditionsResult.getSummary());
        }
        completedMainSteps.add("write_work_conditions");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成正在撰写工作条件", callId);
            callback.onData(statusResponse);
        }

        // =============== Step 3.13: 正在撰写研究计划与进度安排 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("3.13 正在撰写研究计划与进度安排", "正在撰写研究计划与进度安排", callId, "");
            callback.onData(statusResponse);
        }

        previousReportContent = String.join("\n\n", reportSections);
        context.getQueryParams().put("previous_report_content", previousReportContent);

        String researchPlanAndScheduleSection = reportGenerationAgent.generateResearchPlanAndSchedule(context, callback);
        ReportSectionResult researchPlanAndScheduleResult = reportGenerationAgent.separateReportContentAndSummary(researchPlanAndScheduleSection);
        if (researchPlanAndScheduleResult.getReportContent() != null && !researchPlanAndScheduleResult.getReportContent().isEmpty()) {
            reportSections.add(researchPlanAndScheduleResult.getReportContent());
        }
        if (researchPlanAndScheduleResult.getSummary() != null && !researchPlanAndScheduleResult.getSummary().isEmpty()) {
            reportSummaries.add(researchPlanAndScheduleResult.getSummary());
        }
        completedMainSteps.add("write_research_plan_and_schedule");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成正在撰写研究计划与进度安排", callId);
            callback.onData(statusResponse);
        }

        // =============== Step 3.14: 正在撰写预期研究成果 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("3.14 正在撰写预期研究成果", "正在撰写预期研究成果", callId, "");
            callback.onData(statusResponse);
        }

        previousReportContent = String.join("\n\n", reportSections);
        context.getQueryParams().put("previous_report_content", previousReportContent);

        String expectedOutcomesSection = reportGenerationAgent.generateExpectedResearchOutcomes(context, callback);
        ReportSectionResult expectedOutcomesResult = reportGenerationAgent.separateReportContentAndSummary(expectedOutcomesSection);
        if (expectedOutcomesResult.getReportContent() != null && !expectedOutcomesResult.getReportContent().isEmpty()) {
            reportSections.add(expectedOutcomesResult.getReportContent());
        }
        if (expectedOutcomesResult.getSummary() != null && !expectedOutcomesResult.getSummary().isEmpty()) {
            reportSummaries.add(expectedOutcomesResult.getSummary());
        }
        completedMainSteps.add("write_expected_research_outcomes");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成正在撰写预期研究成果", callId);
            callback.onData(statusResponse);
        }

        // =============== Step 3.15: 正在撰写风险分析与对策 ===============
        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowBuilder("3.15 正在撰写风险分析与对策", "正在撰写风险分析与对策", callId, "");
            callback.onData(statusResponse);
        }

        previousReportContent = String.join("\n\n", reportSections);
        context.getQueryParams().put("previous_report_content", previousReportContent);

        String riskSection = reportGenerationAgent.generateRiskAnalysisAndCountermeasures(context, callback);
        ReportSectionResult riskResult = reportGenerationAgent.separateReportContentAndSummary(riskSection);
        if (riskResult.getReportContent() != null && !riskResult.getReportContent().isEmpty()) {
            reportSections.add(riskResult.getReportContent());
        }
        if (riskResult.getSummary() != null && !riskResult.getSummary().isEmpty()) {
            reportSummaries.add(riskResult.getSummary());
        }
        completedMainSteps.add("write_risk_analysis_and_countermeasures");

        if (callback != null) {
            String callId = UUID.randomUUID().toString();
            Response statusResponse = Response.flowResultBuilder("已完成正在撰写风险分析与对策", callId);

            callback.onData(statusResponse);
            ArrayList<String> status = new ArrayList<>(Collections.nCopies(todo.size(), "todo"));
            status.set(0, "done");
            status.set(1, "done");
            status.set(2, "done");
            status.set(3, "doing");
            Response taskStatus = Response.itemInfoBuilder(todo, status);
            callback.onData(taskStatus);
        }

        // =============== Step 4.16: 正在整理文献列表 ===============
//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowBuilder("4.16 正在整理文献列表", "正在整理文献列表", callId, "");
//            callback.onData(statusResponse);
//        }
//
//        // 整理文献列表（流式输出）
//        String referencesSection = reportGenerationAgent.generateReferences(context, callback);
//        reportSections.add(referencesSection);
//        completedMainSteps.add("write_references");

//        if (callback != null) {
//            String callId = UUID.randomUUID().toString();
//            Response statusResponse = Response.flowResultBuilder("已完成正在整理文献列表", callId);
//            callback.onData(statusResponse);
//
//            // 更新任务状态：研究优青基金申报要求和模板格式完成
//            ArrayList<String> status = new ArrayList<>(Collections.nCopies(todo.size(), "todo"));
//            status.set(0, "done");
//            status.set(1, "done");
//            status.set(2, "done");
//            status.set(3, "done");
//            status.set(4, "doing");
//            Response taskStatus = Response.itemInfoBuilder(todo, status);
//            callback.onData(taskStatus);
//
//        }

            String fullReport = reportGenerationAgent.assembleFullReport(reportSections);

            String cleanedReport = reportGenerationAgent.cleanReportContent(fullReport);


            // =============== Step 4.1: 正在生成研究框架图 ===============
            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder("正在生成研究框架图", "正在生成研究框架图", callId, "");
                ((ResponseContent) statusResponse.getData()).setHighlightType(true);
                callback.onData(statusResponse);
            }

            cleanedReport = reportGenerationAgent.generateResearchFrameworkChart(cleanedReport,userId,callback);
            completedMainSteps.add("generate_research_framework");

            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowResultBuilder("正在生成研究框架图", callId);
                callback.onData(statusResponse);
            }


            // =============== Step 4.2: 正在生成技术路线图 ===============
            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder("正在生成技术路线图", "正在生成技术路线图", callId, "");
                ((ResponseContent) statusResponse.getData()).setHighlightType(true);
                callback.onData(statusResponse);
            }

            cleanedReport = reportGenerationAgent.generateTechnicalRoadmap(cleanedReport,userId,callback);
            completedMainSteps.add("generate_tech_roadmap");

            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowResultBuilder("正在生成技术路线图", callId);
                callback.onData(statusResponse);
            }


            // =============== Step 4.3: 正在整理报告内容 ===============
            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder("正在整理报告内容", "正在整理报告内容", callId, "");
                ((ResponseContent) statusResponse.getData()).setHighlightType(true);
                callback.onData(statusResponse);
            }

            // 拼接所有章节成完整报告
            // 添加整合后的文献列表到报告章节
//            String formattedLiteratures = context.getResponseContent();
//            if (formattedLiteratures != null && !formattedLiteratures.isEmpty()) {
//                reportSections.add(formattedLiteratures);
//            }
//            String literature = context.getLiterature();
////            if(literature !=null && !literature.isEmpty()){
////                reportSections.add(literature);
////            }
            sleep(5000);

//            // 将收集的摘要拼接到最终报告末尾
//            if (!reportSummaries.isEmpty()) {
//                StringBuilder summarySection = new StringBuilder();
//                summarySection.append("\n\n<h2>报告摘要汇总</h2>\n\n");
//
//                for (int i = 0; i < reportSummaries.size(); i++) {
//                    String summary = reportSummaries.get(i);
//                    if (summary != null && !summary.isEmpty()) {
//                        summarySection.append("<h3>章节 ").append(i + 1).append(" 摘要</h3>\n");
//                        summarySection.append(summary).append("\n\n");
//                    }
//                }
//
//                fullReport += summarySection.toString();
//            }

            completedMainSteps.add("assemble_full_report");

            if (callback != null) {
                String callId = UUID.randomUUID ().toString();
                Response statusResponse = Response.flowResultBuilder("正在整理报告内容", callId);
                callback.onData(statusResponse);
            }

            // =============== Step 4.4: 正在规范报告格式 ===============
            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder("正在规范报告格式", "正在规范报告格式", callId, "");
                ((ResponseContent) statusResponse.getData()).setHighlightType(true);
                callback.onData(statusResponse);
            }
            sleep(5000);

            completedMainSteps.add("standard_report_content");

            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowResultBuilder("正在规范报告格式", callId);
                callback.onData(statusResponse);
            }

            // =============== Step 4.5: 正在生成最终申报书文档 ===============
            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder("正在生成最终申报书文档", "正在生成最终申报书文档", callId, "");
                ((ResponseContent) statusResponse.getData()).setHighlightType(true);
                callback.onData(statusResponse);
            }

            sleep(2000);
            completedMainSteps.add("finalize_document");

            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response statusResponse = Response.flowResultBuilder("正在生成最终申报书文档", callId);
                callback.onData(statusResponse);

//                // 更新任务状态：整理并生成最终申报书文档完成
//                ArrayList<String> status = new ArrayList<>(Collections.nCopies(todo.size(), "todo"));
//                status.set(0, "done");
//                status.set(1, "done");
//                status.set(2, "done");
//                status.set(3, "done");
//                status.set(4, "doing");
//                Response taskStatus = Response.itemInfoBuilder(todo, status);
//                callback.onData(taskStatus);

                // 更新任务状态：所有任务完成
                ArrayList<String> status = new ArrayList<>(Collections.nCopies(todo.size(), "done"));
                Response finalStatus = Response.itemInfoBuilder(todo, status);
                callback.onData(finalStatus);

            }

//            // =============== Step 5.1: 向用户提供综合分析报告 ===============
//            if (callback != null) {
//                String callId = UUID.randomUUID().toString();
//                Response statusResponse = Response.flowBuilder("5.1 向用户提供综合分析报告", "向用户提供综合分析报告", callId, "");
//                callback.onData(statusResponse);
//            }
////            Response response1 = Response.ResponseContentChatBuilder(context.getResponseContent(), "agent");
////            callback.onData(response1);
//            sleep(3000);
//            completedMainSteps.add("provide_analysis_report");
//
//            if (callback != null) {
//                String callId = UUID.randomUUID().toString();
//                Response statusResponse = Response.flowResultBuilder("已完成向用户提供综合分析报告", callId);
//                callback.onData(statusResponse);
//
//                // 更新任务状态：所有任务完成
//                ArrayList<String> status = new ArrayList<>(Collections.nCopies(todo.size(), "done"));
//                Response finalStatus = Response.itemInfoBuilder(todo, status);
//                callback.onData(finalStatus);
//            }

//            context.setResponseContent(topicName + "报告分析已完成，报告已生成并附在下方，请查收。\n"+
//                    "\n" +
//                    cleanedReport
//            );
//            // 设置响应内容，确保DialogRouter能获取到报告生成完成的信息

            Response response4 = Response.ResponseContentChatBuilder(topicName + "报告分析已完成，报告已生成并附在下方，请查收。", "agent");
            callback.onData(response4);
            //             打印生成的报告内容到控制台（供调试和验证使用）
            System.out.println("\n" + "=".repeat(80));
            System.out.println("=== 生成的报告内容 ===");
            System.out.println("=".repeat(80));
            System.out.println("报告长度: " + cleanedReport.length() + " 字符");
            System.out.println("=".repeat(80));
            System.out.println("=== 报告内容结束 ===");
            System.out.println("=".repeat(80) + "\n");

            // 保存完整报告到Session
            saveAsPaperVersion(context, cleanedReport);
            // 保存报告到AnalysisContext供后续使用
            context.setFinalPaper(cleanedReport);

            // 保存报告到本地项目目录
//            String localReportPath = saveReportToLocalFile(cleanedReport, topicName, traceId);
//
//            // 生成PDF和DOCX文档
//            String localPdfPath = null;
//            String localDocxPath = null;
//            try {
//                // 使用新的PDF转换器
//                localPdfPath = markdownToPdfConverter.convert(cleanedReport, topicName, traceId);
//                log.info("PDF document generated successfully | traceId={} | path={}", traceId, localPdfPath);
//
//                // 使用新的DOCX转换器
//                localDocxPath = markdownToDocxConverter.convert(cleanedReport, topicName, traceId);
//                log.info("DOCX document generated successfully | traceId={} | path={}", traceId, localDocxPath);
//            } catch (Exception e) {
//                log.error("Document generation failed | traceId={}", traceId, e);
//            }

            // 发送OSS上传开始事件
            try {
                JSONObject ossStartEvent = new JSONObject();
                ossStartEvent.put("type", "oss_upload_start");
                callback.onData(ossStartEvent.toString());
            } catch (Exception e) {
                log.warn("Failed to send oss_upload_start event", e);
            }

            // 上传到OSS
            String reportUrl = null;
            try {
                reportUrl = fileParsingUtils.upload(parentId, userId, cleanedReport);
                if (reportUrl == null || reportUrl.isEmpty()) {
                    log.warn("REPORT_UPLOAD_FAILED | traceId={} | OSS upload returned empty URL", traceId);
                    reportUrl = "报告上传失败，但已保存到本地";
                }
            } catch (Exception uploadError) {
                log.error("REPORT_UPLOAD_ERROR | traceId={}", traceId, uploadError);
                reportUrl = "报告上传失败: " + uploadError.getMessage() + "，但已保存到本地";
            }

            log.info("REPORT_GENERATION_SUCCESS | traceId={} | length={}| ossUrl={}",
                    traceId, cleanedReport.length(), reportUrl);
            // 在控制台显示报告链接
            System.out.println("=".repeat(80));
            System.out.println("📎 报告生成链接:");
            System.out.println("=".repeat(80));
//            if (localReportPath != null && !localReportPath.isEmpty()) {
//                System.out.println("💾 本地Markdown文件路径: " + localReportPath);
//            }
//            if (localPdfPath != null && !localPdfPath.isEmpty()) {
//                System.out.println("📄 本地PDF文件路径: " + localPdfPath);
//            }
//            if (localDocxPath != null && !localDocxPath.isEmpty()) {
//                System.out.println("📄 本地DOCX文件路径: " + localDocxPath);
//            }
            if (reportUrl != null && !reportUrl.isEmpty() && !reportUrl.contains("失败")) {
                System.out.println("🌐 OSS链接: " + reportUrl);
            }
            System.out.println("=".repeat(80) + "\n");

            // 构造报告链接返回对象
//                    JSONObject reportResult = new JSONObject();
//                    reportResult.put("type", "finalPaper");
//                    JSONObject reportData = new JSONObject();
//                    reportResult.put("data", reportData);
//                    reportData.put("type", "line");
//                    reportData.put("url", reportUrl);
//                    reportData.put("localPath", localReportPath);
//                    reportData.put("localWordPath", localWordPath);
//                    reportData.put("reportLength", cleanedReport.length());
//                    reportData.put("topicName", topicName);
//                    reportData.put("generatedAt", LocalDateTime.now().toString());
//
//                    callback.onData(reportResult.toString());

            // 发送完成通知
            sendFinalNotification(sessionId, cleanedReport.length(), id, parentId, userId, callback);

            // 发送finish消息
//            Response finish = Response.finishBuilder(reportUrl);
            Response finish = Response.finishBuilder(reportUrl,context);
            callback.onData(finish);
        } catch (Exception e) {
            if (callback != null) {
                String callId = UUID.randomUUID().toString();
                Response errorResponse = Response.flowResultBuilder("任务执行失败: " + e.getMessage(), callId);
                callback.onData(errorResponse);
            }
            throw e;
        }


    }

    /**
     * 获取并整合文献信息，返回统计结果给前端
     * 
     * @param context 分析上下文
     * @param callback 回调接口
     * @param traceId 追踪ID
     */
    private void getLiterature(AnalysisContext context, StreamDataCallback callback, String traceId) {
        String combinedJson = context.getCleanJson();
        if (combinedJson == null || combinedJson.isEmpty()) {
            log.warn("COMBINED_JSON_EMPTY | traceId={}", traceId);
            return;
        }
        
        if (callback == null) {
            log.warn("CALLBACK_IS_NULL | traceId={}", traceId);
            return;
        }
        
        try {
            // 解析整合后的JSON数据
            List<Map<String, Object>> combinedResults = objectMapper.readValue(combinedJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

            int totalCount = 0;
            int webSearchCount = 0;
            int esSearchCount = 0;
            String webSearchSource = "none"; // 实际使用的网页搜索引擎
            Map<String, Integer> webSourceDistribution = new HashMap<>(); // 各搜索引擎的结果分布

            // 解析ES搜索结果和网页搜索结果
            List<ProjectApplicationLiteratureDTO> esLiteratures = new ArrayList<>();
            List<Map<String, Object>> webLiteratures = new ArrayList<>();

            for (Map<String, Object> result : combinedResults) {
                String type = (String) result.get("type");
                Integer count = (Integer) result.get("count");
                
                if ("es_search".equals(type)) {
                    // 处理ES搜索结果
                    esSearchCount = count != null ? count : 0;
                    totalCount += esSearchCount;
                    
                    String esContent = (String) result.get("content");
                    if (esContent != null && !esContent.isEmpty()) {
                        try {
                            List<ProjectApplicationLiteratureDTO> tempEsLiteratures = objectMapper.readValue(esContent,
                                objectMapper.getTypeFactory().constructCollectionType(List.class, ProjectApplicationLiteratureDTO.class));
                            if (tempEsLiteratures != null) {
                                esLiteratures = tempEsLiteratures;
                            }
                        } catch (JsonProcessingException e) {
                            log.error("FAILED_TO_PARSE_ES_CONTENT | traceId={}", traceId, e);
                        }
                    }
                    
                } else if ("web_search".equals(type)) {
                    // 处理网页搜索结果
                    webSearchCount = count != null ? count : 0;
                    totalCount += webSearchCount;
                    
                    // 获取搜索引擎来源
                    if (result.containsKey("source")) {
                        webSearchSource = (String) result.get("source");
                    }
                    
                    // 获取各搜索引擎的结果分布
                    if (result.containsKey("sourceCount")) {
                        try {
                            Map<String, Integer> sourceCount = (Map<String, Integer>) result.get("sourceCount");
                            if (sourceCount != null) {
                                webSourceDistribution = sourceCount;
                            }
                        } catch (ClassCastException e) {
                            log.warn("FAILED_TO_PARSE_SOURCE_COUNT | traceId={}", traceId, e);
                        }
                    }
                    
                    // 获取网页搜索内容
                    if (result.containsKey("content")) {
                        try {
                            List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
                            if (content != null) {
                                webLiteratures = content;
                            }
                        } catch (ClassCastException e) {
                            log.error("FAILED_TO_PARSE_WEB_CONTENT | traceId={}", traceId, e);
                        }
                    }
                }
            }

            // 构建详细的统计消息
            StringBuilder statisticsMessage = new StringBuilder();
            statisticsMessage.append("📊 整合搜索结果完成\n\n");
            statisticsMessage.append(String.format("✅ 共检索 %d 篇文献\n\n", totalCount));
            
            // 网页搜索统计
            if (webSearchCount > 0) {
                statisticsMessage.append(String.format("🌐 网页搜索结果：%d 篇 ", webSearchCount));
                
                // 展示详细分布，多个搜索引擎结果排成一行
                if (!webSourceDistribution.isEmpty()) {
                    statisticsMessage.append("详细分布：");
                    List<String> distributionItems = new ArrayList<>();
                    for (Map.Entry<String, Integer> entry : webSourceDistribution.entrySet()) {
                        String engine = getSearchEngineDisplayName(entry.getKey());
                        distributionItems.add(String.format("%s: %d 篇", engine, entry.getValue()));
                    }
                    statisticsMessage.append(String.join("、", distributionItems));
                }
                statisticsMessage.append(" ");
            } else {
                statisticsMessage.append("🌐 网页搜索结果：0 篇 ");
            }
            
            // 数据库搜索统计，与网页搜索结果排成一行
            if (esSearchCount > 0) {
                statisticsMessage.append(String.format("📚 数据库搜索结果：%d 篇", esSearchCount));
            } else {
                statisticsMessage.append("📚 数据库搜索结果：0 篇");
            }
            statisticsMessage.append("\n");
            
            statisticsMessage.append("\n");

            // 发送统计消息到前端
            Response countResponse = Response.ResponseContentChatBuilder(statisticsMessage.toString(), "agent");
            callback.onData(countResponse);
            
            log.info("LITERATURE_STATISTICS | traceId={} | total={} | webSearch={} | webSource={} | esSearch={}", 
                    traceId, totalCount, webSearchCount, webSearchSource, esSearchCount);

            // 整合并格式化文献列表
            StringBuilder formattedLiteratures = new StringBuilder();
            formattedLiteratures.append("<strong>参考文献：</strong>\n\n");

            int literatureNumber = 1;

            // 用于去重的Set，存储已经处理过的标题
            Set<String> processedTitles = new HashSet<>();

            // 添加ES数据库查询的文献（按照中英文格式）
            // 通过OpenFeign远程调用获取完整的MongoLiterature数据进行格式化
            if (esLiteratures != null && !esLiteratures.isEmpty()) {
                for (ProjectApplicationLiteratureDTO literature : esLiteratures) {
                    if (literature != null && literature.getId() != null) {
                        // 获取文献标题用于去重
                        String title = literature.getTitle();
                        if (title != null && !title.isEmpty() && !processedTitles.contains(title.trim().toLowerCase())) {
                            // 标题不在已处理列表中，添加到已处理集合
                            processedTitles.add(title.trim().toLowerCase());
                            
                            String formattedReference = formatLiteratureReference(literature.getId(), literatureNumber);
                            if (formattedReference != null && !formattedReference.isEmpty()) {
                                formattedLiteratures.append(formattedReference).append("\n\n");
                                literatureNumber++;
                            }
                        }
                    }
                }
            }

            // 添加网页搜索的文献
            if (webLiteratures != null && !webLiteratures.isEmpty()) {
                for (Map<String, Object> webLiterature : webLiteratures) {
                    if (webLiterature != null) {
                        String name = (String) webLiterature.get("name");
                        String url = (String) webLiterature.get("url");
                        if (name != null && !name.isEmpty() && url != null && !url.isEmpty()) {
                                // 处理标题，确保从前往后显示，过长时在后面显示...
                                if (name.length() > 50) {
                                    name = name.substring(0, 47) + "...";
                                }
                                formattedLiteratures.append("[").append(literatureNumber).append("] ")
                                    .append(name).append("\n")
                                    .append(url).append("\n\n");
                                literatureNumber++;
                        }
                    }
                }
            }

            // 将格式化的文献列表保存到context中，供后续步骤使用
            context.setLiterature(formattedLiteratures.toString());
            
            log.info("LITERATURE_FORMATTED | traceId={} | totalFormatted={}", traceId, literatureNumber - 1);

        } catch (JsonProcessingException e) {
            log.error("FAILED_TO_PARSE_COMBINED_RESULTS | traceId={}", traceId, e);
            
            // 发送错误消息到前端
            if (callback != null) {
                Response errorResponse = Response.ResponseContentChatBuilder(
                    "⚠️ 整合搜索结果时出现错误，但将继续后续流程。\n", 
                    "agent"
                );
                callback.onData(errorResponse);
            }
        } catch (Exception e) {
            log.error("UNEXPECTED_ERROR_IN_GET_LITERATURE | traceId={}", traceId, e);
            
            // 发送错误消息到前端
            if (callback != null) {
                Response errorResponse = Response.ResponseContentChatBuilder(
                    "⚠️ 处理文献数据时出现意外错误，但将继续后续流程。\n", 
                    "agent"
                );
                callback.onData(errorResponse);
            }
        }
    }
    
    /**
     * 获取搜索引擎的显示名称
     * 
     * @param source 搜索引擎代码
     * @return 显示名称
     */
    private String getSearchEngineDisplayName(String source) {
        if (source == null || source.isEmpty()) {
            return "未知";
        }
        
        switch (source.toLowerCase()) {
            case "bocha":
                return "Bocha搜索";
            case "baidu":
                return "百度搜索";
            case "bing":
                return "必应搜索";
            case "duckduckgo":
                return "DuckDuckGo搜索";
            case "google_scholar":
                return "谷歌学术";
            case "fallback":
                return "备用搜索";
            case "none":
                return "无";
            default:
                return source;
        }
    }

    /**
     * 整合网页搜索和ES搜索结果
     * 
     * @param context 分析上下文
     * @param webResult 网页搜索结果（cn.hutool.json.JSONObject列表）
     * @param traceId 追踪ID
     */
    private void getResult(AnalysisContext context, List<JSONObject> webResult, String traceId) {
        String cleanJson;
        // 获取ES搜索结果
        cleanJson = context.getCleanJson();
        
        // 获取网页搜索源
        String webSearchSource = context.getWebSearchSource();
        if (webSearchSource == null || webSearchSource.isEmpty()) {
            webSearchSource = "none";
        }

        // 统计网页搜索结果数量，并按搜索引擎分组
        int webResultCount = webResult != null ? webResult.size() : 0;
        Map<String, Integer> webSourceCount = new HashMap<>();
        
        if (webResult != null && !webResult.isEmpty()) {
            // 统计各个搜索引擎的结果数量
            for (JSONObject result : webResult) {
                if (result != null && result.containsKey("source")) {
                    String source = result.getStr("source");
                    webSourceCount.put(source, webSourceCount.getOrDefault(source, 0) + 1);
                }
            }
        }
        
        // 解析ES搜索结果，统计文献数量
        int esResultCount = 0;
        if (cleanJson != null && !cleanJson.isEmpty()) {
            try {
                List<ProjectApplicationLiteratureDTO> literatureList = objectMapper.readValue(cleanJson, 
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ProjectApplicationLiteratureDTO.class));
                esResultCount = literatureList != null ? literatureList.size() : 0;
            } catch (JsonProcessingException e) {
                log.error("FAILED_TO_PARSE_ES_RESULTS | traceId={}", traceId, e);
            }
        }
        
        int totalResultCount = webResultCount + esResultCount;
        
        // 封装结果到集合
        List<Map<String, Object>> combinedResults = new ArrayList<>();

        // 添加网页搜索结果，将cn.hutool.json.JSONObject转换为标准Map
        Map<String, Object> webResultMap = new HashMap<>();
        webResultMap.put("type", "web_search");
        webResultMap.put("source", webSearchSource); // 主要搜索源
        webResultMap.put("sourceCount", webSourceCount); // 各搜索引擎的结果数量分布
        
        // 将cn.hutool.json.JSONObject列表转换为标准Map列表，避免序列化/反序列化时的类型转换问题
        List<Map<String, Object>> standardWebResult = new ArrayList<>();
        if (webResult != null) {
            for (JSONObject jsonObject : webResult) {
                // 转换为标准HashMap
                Map<String, Object> map = new HashMap<>();
                if (jsonObject != null) {
                    // 获取所有键值对并转换
                    for (String key : jsonObject.keySet()) {
                        map.put(key, jsonObject.get(key));
                    }
                }
                standardWebResult.add(map);
            }
        }
        
        webResultMap.put("content", standardWebResult);
        webResultMap.put("count", webResultCount);
        webResultMap.put("timestamp", LocalDateTime.now().toString());
        combinedResults.add(webResultMap);

        // 添加ES搜索结果
        Map<String, Object> esResultMap = new HashMap<>();
        esResultMap.put("type", "es_search");
        esResultMap.put("content", cleanJson);
        esResultMap.put("count", esResultCount);
        esResultMap.put("timestamp", LocalDateTime.now().toString());
        combinedResults.add(esResultMap);

        // 保存到上下文供后续使用
        try {
            String combinedJson = objectMapper.writeValueAsString(combinedResults);
            context.setCleanJson(combinedJson);
            
            log.info("COMBINED_RESULTS_SAVED | traceId={} | webSource={} | webResultSize={} | esResultSize={} | totalResultCount={}",
                    traceId, webSearchSource, webResultCount, esResultCount, totalResultCount);
            
            // 记录详细的搜索引擎分布
            if (!webSourceCount.isEmpty()) {
                log.info("WEB_SEARCH_SOURCE_DISTRIBUTION | traceId={} | distribution={}", traceId, webSourceCount);
            }
        } catch (JsonProcessingException e) {
            log.error("JSON_SERIALIZATION_ERROR | traceId={}", traceId, e);
        }
    }

    private void getEsResult(AnalysisContext context, String id, String userId, String parentId, StreamDataCallback callback, String traceId) {
        topicESQueryAgent.process(context, id, parentId, userId, callback);

        // 获取ES查询到的文献结果
        String cleanJson = context.getCleanJson();
        try {
            // 解析JSON为ProjectApplicationLiteratureDTO列表
            List<ProjectApplicationLiteratureDTO> literatureList = objectMapper.readValue(cleanJson, objectMapper.getTypeFactory().constructCollectionType(List.class, ProjectApplicationLiteratureDTO.class));

            // 封装文献结果为String
            StringBuilder esResultContent = new StringBuilder();
            esResultContent.append("📚 数据库搜索获取的相关文献：\n\n");

            if (literatureList != null && !literatureList.isEmpty()) {
                // 遍历数据库结果，只取前5个显示给前端
                for (int i = 0; i < Math.min(literatureList.size(), 5); i++) {
                    ProjectApplicationLiteratureDTO literature = literatureList.get(i);
                    esResultContent.append((i + 1)).append(". ").append(literature.getTitle()).append("\n");
                    esResultContent.append("\n");
                    esResultContent.append(literature.getTldr()).append("\n\n");
//                    esResultContent.append("总结:").append(literature.getTldr()).append("\n\n");
//                    esResultContent.append("摘要：").append(literature.getSummary()).append("\n\n");
                }
            } else {
                esResultContent.append("暂无相关文献\n");
            }

            // 通过ResponseContentChatBuilder发送给前端
            Response esResponse = Response.ResponseContentChatBuilder(esResultContent.toString(), "agent");
            callback.onData(esResponse);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse ES search results | traceId={}", traceId, e);
        }
    }

    @Nullable
    private static List<JSONObject> getWebResult(StreamDataCallback callback, BochaWebSearchTool bochaWebSearchTool, String topicName) {
        List<JSONObject> webResult = bochaWebSearchTool.searchWebByBocha(topicName, callback);

        // 拼接搜索结果为字符串
        System.out.println("\n" + "=".repeat(80));
        System.out.println("=== 网页搜索获取的相关文献链接 ===");
        System.out.println("=".repeat(80));

        // 收集所有文献链接信息
        StringBuilder webResultContent = new StringBuilder();
        webResultContent.append("📚 参考信息：\n\n");

        if (webResult != null && !webResult.isEmpty()) {
            // 遍历搜索结果，只取前5个显示给前端
            int count = 0;
            for (int i = 0; i < webResult.size() && count < 5; i++) {
                JSONObject obj = webResult.get(i);

                // 过滤null或空值
                if (obj == null) {
                    continue;
                }

                String url = obj.getStr("url");
                String name = obj.getStr("name");

                // 跳过空值
                if (url == null || url.isEmpty() || name == null || name.isEmpty()) {
                    continue;
                }

                // 处理标题，确保从前往后显示，过长时在后面显示...
                String displayName = name;
                if (displayName.length() > 50) {
                    displayName = displayName.substring(0, 47) + "...";
                }
                // 打印文献链接到控制台
                System.out.printf("📄 [%d] %s\n   %s\n", (i + 1), displayName, url);
                // 拼接到结果字符串，使用 Markdown 链接语法，只显示名称，点击可跳转
                webResultContent.append((i + 1)).append(". [")
                        .append(displayName)
                        .append("](")
                        .append(url)
                        .append(")\n\n");
                count++;
            }
        } else {
//            webResultContent.append("暂无相关文献链接\n");
        }

        System.out.println("=".repeat(80) + "\n");

        Response webResponse = Response.ResponseContentChatBuilder(webResultContent.toString(), "agent");
        callback.onData(webResponse);
        return webResult;
    }

    /**
     * 从上下文中获取课题名称
     * @param context 分析上下文
     * @return 课题名称
     */
    private String getTopicNameFromContext(AnalysisContext context) {
        // 优先从queryParams中获取
        if (context.getQueryParams() != null && context.getQueryParams().containsKey("topicName")) {
            return context.getQueryParams().get("topicName").toString();
        }
        // 其次从userMessage中提取
        return context.getUserMessage();
    }

    /**
     * 保存报告到本地项目目录
     *
     * @param report 报告内容
     * @param topicName 课题名称
     * @param traceId 追踪ID
     * @return 保存的文件路径，失败返回null
     */
    public String saveReportToLocalFile(String report, String topicName, String traceId) {
        try {
            // 生成文件名：使用课题名称和时间戳
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String safeTopicName = topicName != null && !topicName.isEmpty()
                    ? topicName.replaceAll("[\\\\/:*?\"<>|]", "_").substring(0, Math.min(50, topicName.length()))
                    : "项目申报书";
            String fileName = safeTopicName + "_" + timestamp + ".md";

            // 保存到项目根目录的reports文件夹
            Path reportPath = Paths.get("reports", fileName);

            // 创建目录（如果不存在）
            Files.createDirectories(reportPath.getParent());

            // 写入文件，确保使用UTF-8编码
            Files.write(reportPath, report.getBytes(StandardCharsets.UTF_8));

            log.info("REPORT_SAVED_LOCALLY | traceId={} | path={}", traceId, reportPath.toAbsolutePath());
            return reportPath.toAbsolutePath().toString();

        } catch (IOException e) {
            log.error("REPORT_SAVE_LOCAL_ERROR | traceId={}", traceId, e);
            return null;
        }
    }


    /**
     * 发送报告生成完成通知
     *
     * @param dialogId 对话ID
     * @param length 报告字符长度
     * @param id 消息ID
     * @param parentId 父消息ID
     * @param userId 用户ID
     * @param streamDataCallback 流式数据回调
     */
    private void sendFinalNotification(String dialogId, int length, String id,
                                       String parentId,
                                       String userId, StreamDataCallback streamDataCallback) {
        try {
            String msg = objectMapper.writeValueAsString(Map.of(
                    "type", "paper_complete",
                    "message", "项目申报书报告生成完成！",
                    "totalChars", length,
                    "status", "success"
            ));
            //webSocketClient.sendMessage(dialogId, msg, id, parentId, userId);

            streamDataCallback.onData(msg);
        } catch (Exception e) {
            log.warn("Failed to send completion", e);
        }
    }

    /**
     * 将生成的报告保存为论文版本
     *
     * @param context 分析上下文
     * @param report 生成的报告内容
     */
    public void saveAsPaperVersion(AnalysisContext context, String report) {
        try {
            Session session = context.getSession();
            if (session == null) {
                log.warn("Session is null, cannot save paper version");
                return;
            }

            if (session.getPaperVersions() == null) {
                session.setPaperVersions(new java.util.ArrayList<>());
            }

            Session.PaperVersion version = new Session.PaperVersion();
            version.setVersion("v" + (session.getPaperVersions().size() + 1));
            version.setFullPaper(report);
            version.setTimestamp(LocalDateTime.now());
            session.getPaperVersions().add(0, version); // 插入最新版

            log.info("Paper version saved | version={} | length={}", version.getVersion(), report.length());
        } catch (Exception e) {
            log.error("Failed to save paper version", e);
            // 不抛出异常，避免影响主流程
        }
    }
    
    /**
     * 生成项目基本信息表格
     * @param context 分析上下文
     * @return HTML格式的项目基本信息表格
     */
//    private String generateProjectInfoTable(AnalysisContext context) {
//        Map<String, Object> params = context.getQueryParams();
//
//        // 获取参数值，默认(待填写)
//        String topicName = getParamValue(params, "topicName");
//            String applicationCode = getParamValue(params, "applicationCode", "H3115 (建议，或根据实际情况选择 H2701, C0708 等)");
//            String researchType = getParamValue(params, "researchType", "目标导向类基础研究");
//            String applicant = getParamValue(params, "applicant");
//            String 依托单位 = getParamValue(params, "依托单位");
//            String fundingType = getParamValue(params, "fundingType", "优秀青年科学基金项目");
//            String notes = getParamValue(params, "notes", "(根据指南填写)");
//
//        // 构建HTML表格
//        StringBuilder tableBuilder = new StringBuilder();
//        tableBuilder.append("<h2>项目基本信息</h2>");
//        tableBuilder.append("<table style=\"width: 100%; border-collapse: collapse; margin: 20px 0;\">");
//        tableBuilder.append("<thead>");
//        tableBuilder.append("<tr style=\"background-color: #f2f2f2;\">");
//        tableBuilder.append("<th style=\"border: 1px solid #ddd; padding: 8px; text-align: left;\">项目信息</th>");
//        tableBuilder.append("<th style=\"border: 1px solid #ddd; padding: 8px; text-align: left;\">内容</th>");
//        tableBuilder.append("</tr>");
//        tableBuilder.append("</thead>");
//        tableBuilder.append("<tbody>");
//
//        // 项目名称行
//        tableBuilder.append("<tr>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px; font-weight: bold;\">项目名称</td>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px;\">").append(topicName).append("</td>");
//        tableBuilder.append("</tr>");
//
//        // 申请代码行
//        tableBuilder.append("<tr>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px; font-weight: bold;\">申请代码</td>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px;\">").append(applicationCode).append("</td>");
//        tableBuilder.append("</tr>");
//
//        // 研究属性行
//        tableBuilder.append("<tr>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px; font-weight: bold;\">研究属性</td>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px;\">").append(researchType).append("</td>");
//        tableBuilder.append("</tr>");
//
//        // 申请人行
//        tableBuilder.append("<tr>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px; font-weight: bold;\">申请人</td>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px;\">").append(applicant).append("</td>");
//        tableBuilder.append("</tr>");
//
//        // 依托单位行
//        tableBuilder.append("<tr>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px; font-weight: bold;\">依托单位</td>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px;\">").append(依托单位).append("</td>");
//        tableBuilder.append("</tr>");
//
//        // 资助类别行
//        tableBuilder.append("<tr>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px; font-weight: bold;\">资助类别</td>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px;\">").append(fundingType).append("</td>");
//        tableBuilder.append("</tr>");
//
//        // 附注说明行
//        tableBuilder.append("<tr>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px; font-weight: bold;\">附注说明</td>");
//        tableBuilder.append("<td style=\"border: 1px solid #ddd; padding: 8px;\">").append(notes).append("</td>");
//        tableBuilder.append("</tr>");
//
//        tableBuilder.append("</tbody>");
//        tableBuilder.append("</table>");
//
//        return tableBuilder.toString();
//    }
    
    /**
     * 格式化文献引用（根据中英文区分格式）
     * 通过OpenFeign远程调用获取完整的MongoLiterature数据
     * 
     * @param literatureId 文献ID
     * @param number 文献编号
     * @return 格式化后的文献引用字符串
     */
    private String formatLiteratureReference(String literatureId, int number) {
        try {
            // 通过OpenFeign远程调用获取MongoLiterature数据
            MongoLiterature literature = PaperUtils.paper(literatureId);
            
            if (literature == null) {
                log.warn("LITERATURE_NOT_FOUND | literatureId={}", literatureId);
                return String.format("[%d] 文献信息未找到 (ID: %s)", number, literatureId);
            }
            
            StringBuilder builder = new StringBuilder();
            builder.append("[").append(number).append("] ");
            
            // 获取语言信息
            String language = literature.getLanguage();
            if (language == null || language.isEmpty()) {
                language = "en"; // 默认英文
            }
            
            // ===== 1. 作者处理 =====
            List<String> author = literature.getAuthor();
            if (author != null && !author.isEmpty()) {
                // 作者进行特殊处理 - 判断第4个作者是否为"等"或"et al"
                List<String> processedAuthors = new ArrayList<>(author);
                if (processedAuthors.size() > 3) {
                    String fourthAuthor = processedAuthors.get(3);
                    if (!"等".equalsIgnoreCase(fourthAuthor) 
                        && !"etal".equalsIgnoreCase(fourthAuthor.replaceAll("\\s+", ""))
                        && !"et al".equalsIgnoreCase(fourthAuthor)) {
                        processedAuthors = processedAuthors.subList(0, 3);
                    }
                    
                    // 根据语言添加"等"或"et al"
                    if ("zh".equalsIgnoreCase(language)) {
                        processedAuthors.add("等");
                    } else {
                        processedAuthors.add("et al");
                    }
                }
                
                // 拼接作者
                for (int i = 0; i < processedAuthors.size() - 1; i++) {
                    builder.append(processedAuthors.get(i).replaceAll(",", ", ")).append(", ");
                }
                builder.append(processedAuthors.get(processedAuthors.size() - 1)).append(". ");
            }
            
            // ===== 2. 标题 =====
            String title = literature.getTitle();
            if (title != null && !title.isEmpty()) {
                if (title.startsWith("[")) {
                    title = title.replaceAll("\\[", "").replaceAll("]", "");
                }
                builder.append(title).append("[J]").append(". ");
            }
            
            // ===== 3. 期刊 =====
            String journal = literature.getJournal();
            if (journal != null && !journal.isEmpty()) {
                builder.append(journal).append(", ");
            }
            
            // ===== 4. 年份 =====
            String year = literature.getYear();
            if (year != null && !year.isEmpty()) {
                year = "unkn".equalsIgnoreCase(year) ? "" : year;
                if (!year.isEmpty()) {
                    builder.append(year).append(", ");
                }
            }
            
            // ===== 5. 卷 (volume) =====
            List<String> volume = literature.getVolume();
            if (volume != null && !volume.isEmpty()) {
                for (String v : volume) {
                    builder.append(v);
                }
            }
            
            // ===== 6. 期 (issue) =====
            List<String> issue = literature.getIssue();
            if (issue != null && !issue.isEmpty()) {
                for (String i : issue) {
                    builder.append("(").append(i).append(")");
                }
            }
            
            // ===== 7. 页码 =====
            String pages = literature.getPages();
            if (pages != null && !pages.isEmpty()) {
                builder.append(": ");
                pages = pages.replaceAll("[\\[\\]]", "");
                builder.append(pages).append(". ");
            }
            
            // ===== 8. DOI =====
            List<String> doi = literature.getDoi();
            if (doi != null && !doi.isEmpty()) {
                builder.append("DOI:");
                for (String d : doi) {
                    builder.append(d).append(".");
                }
            }
            
            return builder.toString();
            
        } catch (Exception e) {
            log.error("FORMAT_LITERATURE_ERROR | literatureId={}", literatureId, e);
            // 返回简化格式
            return String.format("[%d] 文献格式化失败 (ID: %s, 错误: %s)", 
                number, literatureId, e.getMessage());
        }
    }
    
    /**
     * 获取参数值，默认返回(待填写)
     * @param params 参数映射
     * @param key 参数名
     * @return 参数值
     */
    private String getParamValue(Map<String, Object> params, String key) {
        return getParamValue(params, key, "(待填写)");
    }
    
    /**
     * 获取参数值，自定义默认值
     * @param params 参数映射
     * @param key 参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    private String getParamValue(Map<String, Object> params, String key, String defaultValue) {
        if (params != null && params.containsKey(key)) {
            Object value = params.get(key);
            return value != null ? value.toString() : "(待填写)";
        }
        return defaultValue;
    }
}
