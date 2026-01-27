package com.faers.agent.agent.impl;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.faers.agent.agent.Agent;
import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.dto.responseDto.ImageData;
import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.model.Session;
import com.faers.agent.pojo.ReportSectionResult;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.service.LLMInvocationService;
import com.faers.agent.utils.FileParsingUtils;
import com.faers.agent.utils.MarkdownToWordConverter;
import com.faers.agent.utils.MermaidChartUtils;
import com.faers.agent.utils.MermaidToImageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.faers.agent.pojo.ReportContentPromptConstants.*;

/**
 * 报告生成智能体（支持流式输出）
 * 特性：
 * - 调用 LLM 流式生成规范化的 Markdown 格式项目申报书
 * - 严格按照提示词模板要求生成报告内容
 * - 通过 WebSocket 实时推送片段
 * - 生成完成后保存为 paperVersion 并提供下载链接
 * - 将报告内容打印到控制台供调试查看
 */
@Component
public class ReportGenerationAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationAgent.class);

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private FileParsingUtils fileParsingUtils;
    @Autowired
    private MermaidChartUtils mermaidChartUtils;
    @Autowired
    private MarkdownToWordConverter markdownToWordConverter;

    @Autowired
    private LLMInvocationService llmInvocationService;

    private ChatClient chatClient;
    public ReportGenerationAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    // @Autowired
    // private NettyWebSocketClient webSocketClient; // 使用你现有的客户端

//    @Override
//    public void process(AnalysisContext context ,String id,
//                        String parentId,
//                        String userId,
//                        StreamDataCallback streamDataCallback) {
//        String traceId = context.getTraceId();
//        String sessionId = context.getSessionId();
//
//        log.info("REPORT_GENERATION_START | traceId={}", traceId);
//
//        // 参数验证
//        if (context == null) {
//            log.error("REPORT_GENERATION_ERROR | context is null");
//            throw new IllegalArgumentException("分析上下文不能为空");
//        }
//        if (streamDataCallback == null) {
//            log.error("REPORT_GENERATION_ERROR | streamDataCallback is null");
//            throw new IllegalArgumentException("流式回调不能为空");
//        }
//
//        try {
//            Map<String, Object> params = context.getQueryParams();
//            if (params == null) {
//                params = new java.util.HashMap<>();
//                context.setQueryParams(params);
//            }
//
//            String cleanJson = context.getCleanJson();
//            String latestPaper = getLatestPaper(context);
//
//            if (cleanJson == null || cleanJson.isEmpty()) {
//                log.warn("REPORT_GENERATION_NO_DATA | traceId={}", traceId);
//                sendError(sessionId, "无分析数据，无法生成项目申报书报告。请先执行ES查询获取文献数据。", id, parentId, userId);
//                return;
//            }
//
//            // 将 queryParams 映射为结构化的项目申报书参数，便于前端或导出使用
//            ProjectApplicationParams projectParams = ProjectApplicationParams.fromMap(params);
//            params.put("projectApplicationParams", projectParams);
//
//            // 获取参数值
//            String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
//            String drugName = getValue(params, "drugName");
//            String indication = getValue(params, "indication");
//            String startDate = params.get("startDate") != null ? params.get("startDate").toString() : "未知";
//            String endDate = params.get("endDate") != null ? params.get("endDate").toString() : "未知";
//
//            // 获取关键词列表
//            List<String> keywords = new ArrayList<>();
//            if (params.containsKey("keywords")) {
//                Object keywordsObj = params.get("keywords");
//                if (keywordsObj instanceof List) {
//                    keywords = (List<String>) keywordsObj;
//                } else if (keywordsObj instanceof String) {
//                    keywords.add(keywordsObj.toString());
//                }
//            } else if (!topicName.isEmpty()) {
//                keywords.add(topicName);
//            }
//
//            // 获取研究类型列表
//            List<String> researchTypes = new ArrayList<>();
//            if (params.containsKey("researchType")) {
//                Object researchTypesObj = params.get("researchType");
//                if (researchTypesObj instanceof List) {
//                    researchTypes = (List<String>) researchTypesObj;
//                } else if (researchTypesObj instanceof String) {
//                    researchTypes.add(researchTypesObj.toString());
//                }
//            }
//
//            Response reporter = Response.newBuilder("reporter");
//            streamDataCallback.onData(reporter);
//            // 构建报告生成 prompt
//            String prompt = buildPrompt(topicName, drugName, indication,
//                    startDate, endDate, keywords,
//                    researchTypes, latestPaper, cleanJson, params);
//
//            // 调用大模型生成报告内容（流式生成）
//            generateReportContent(context,prompt, traceId, streamDataCallback, (fullReport) -> {
//                try {
//                    // 发送大模型调用完成事件
//                    try {
//                        JSONObject llmCompleteEvent = new JSONObject();
//                        llmCompleteEvent.put("type", "llm_call_complete");
//                        streamDataCallback.onData(llmCompleteEvent.toJSONString());
//                    } catch (Exception e) {
//                        log.warn("Failed to send llm_call_complete event", e);
//                    }
//
//
//                } catch (Exception e) {
//                    log.error("REPORT_GENERATION_POST_PROCESS_ERROR | traceId={}", traceId, e);
//                    Response errorResponse = Response.ResponseReportChatBuilder("报告后处理过程中发生错误：" + e.getMessage(), "agent");
//                    streamDataCallback.onData(errorResponse);
//                }
//                System.out.println("所有数据处理完毕");
//            });
//
//        } catch (NullPointerException e) {
//            log.error("REPORT_GENERATION_NULL_POINTER | traceId={}", traceId, e);
//            String errorMsg = "系统内部错误（空指针异常）：" + e.getMessage() + ". 可能原因：1) ES数据未正确查询 2) Session未初始化 3) 配置项缺失";
//            sendError(sessionId, errorMsg, id, parentId, userId);
//            throw new RuntimeException(errorMsg, e);
//        } catch (IllegalArgumentException e) {
//            log.error("REPORT_GENERATION_INVALID_ARG | traceId={}", traceId, e);
//            sendError(sessionId, "参数错误：" + e.getMessage(), id, parentId, userId);
//            throw e;
//        } catch (Exception e) {
//            log.error("REPORT_GENERATION_FAILED | traceId={}", traceId, e);
//            String errorMsg = "项目申报书报告生成失败：" + e.getMessage() + " (" + e.getClass().getSimpleName() + ")";
//            sendError(sessionId, errorMsg, id, parentId, userId);
//            throw new RuntimeException(errorMsg, e);
//        }
//    }

    /**
     * 调用大模型生成报告内容（流式生成）
     * 该方法负责调用大模型进行流式生成，并通过回调返回完整报告内容
     *
     * @param prompt             提示词
     * @param traceId            追踪ID
     * @param streamDataCallback 流式数据回调，用于实时推送生成内容
     * @param onComplete         完成回调，当生成完成时调用，参数为完整报告内容
     */
    public void generateReportContent(AnalysisContext context,String prompt, String traceId,
                                      StreamDataCallback streamDataCallback,
                                      Consumer<String> onComplete) {
        // 调用大模型生成报告
        log.info("REPORT_GENERATION_LLM_START | traceId={} | promptLength={}", traceId, prompt.length());

        // 发送大模型调用开始事件
        try {
            JSONObject llmStartEvent = new JSONObject();
            llmStartEvent.put("type", "llm_call_start");
            streamDataCallback.onData(llmStartEvent.toJSONString());
        } catch (Exception e) {
            log.warn("Failed to send llm_call_start event", e);
        }

        // 调用大模型（流式生成，使用LLMInvocationService）
        StringBuilder stringBuilder = new StringBuilder();
        
        // 创建适配器StreamDataCallback，用于构建报告响应
        StreamDataCallback adapterCallback = new StreamDataCallback() {
            @Override
            public void onData(Response response) {
                // 从Response中提取内容
                if (response != null && response.getData() != null) {
                    // Response是泛型，需要转换为ResponseContent
                    Object data = response.getData();
                    String content = null;
                    if (data instanceof com.faers.agent.dto.responseDto.ResponseContent) {
                        com.faers.agent.dto.responseDto.ResponseContent responseContent = 
                                (com.faers.agent.dto.responseDto.ResponseContent) data;
                        // 使用front_display或delta（它们都是累积的完整内容）
                        content = responseContent.getFront_display();
                        if (content == null || content.isEmpty()) {
                            content = responseContent.getDelta();
                        }
                    }
                    
                    if (content != null && !content.isEmpty()) {
                        // delta和front_display已经是累积的完整内容，直接使用
                        stringBuilder.setLength(0); // 清空，使用最新的累积内容
                        stringBuilder.append(content);
                        // 使用ResponseReportChatBuilder构建报告响应
//                        Response reportResponse = Response.ResponseReportChatBuilder(stringBuilder.toString(), "agent");
//                        streamDataCallback.onData(reportResponse);
                    }
                }
            }

            @Override
            public void onData(String chunk) {
                // 处理字符串格式的数据
                stringBuilder.append(chunk);
                // 使用ResponseReportChatBuilder构建报告响应
                Response reportResponse = Response.ResponseReportChatBuilder(stringBuilder.toString(), "agent");
                streamDataCallback.onData(reportResponse);
            }
        };

        try {
            // 使用LLMInvocationService进行调用
            String fullReport = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            
            // 调用完成回调
            if (onComplete != null) {
                onComplete.accept(fullReport);
            }
            context.setFinalPaper(fullReport);
        } catch (Exception e) {
            // 处理错误
            log.error("REPORT_GENERATION_STREAM_ERROR | traceId={} | error={}", traceId, e.getMessage(), e);
            // 如果已接收到部分内容，使用部分内容
            if (stringBuilder.length() > 0) {
                String partialReport = stringBuilder.toString();
                if (onComplete != null) {
                    onComplete.accept(partialReport);
                }
                context.setFinalPaper(partialReport);
            } else {
                // 向前端发送错误信息
                Response errorResponse = Response.ResponseReportChatBuilder(
                        "报告生成过程中发生错误：" + e.getMessage(), "agent");
                streamDataCallback.onData(errorResponse);
            }
        }
    }


//    /**
//     * 构建项目申报书生成的LLM提示词
//     * 严格按照国家自然科学基金申报书格式要求，生成规范化的markdown格式报告
//     *
//     * @param topicName 课题名称
//     * @param drugName 药物名称
//     * @param indication 适应症
//     * @param startDate 开始日期
//     * @param endDate 结束日期
//     * @param keywords 关键词列表
//     * @param researchTypes 研究类型
//     * @param latestPaper 上一版论文
//     * @param cleanJson ES查询结果JSON
//     * @param params 参数集合
//     * @return 完整的LLM提示词
//     */
//    public String buildPrompt(String topicName, String drugName, String indication,
//                              String startDate, String endDate, List<String> keywords,
//                              List<String> researchTypes, String latestPaper, String cleanJson, Map<String, Object> params) {
//        // 获取BochaWebSearch网页搜索结果
//        String webSearchResults = params != null && params.containsKey("webSearchResults")
//                ? params.get("webSearchResults").toString()
//                : "";
//        // 构建关键词字符串
//        String keywordsStr = keywords != null && !keywords.isEmpty() ? String.join("、", keywords) : topicName;
//        // 构建研究类型字符串
//        String researchTypesStr = researchTypes != null && !researchTypes.isEmpty() ? String.join("、", researchTypes) : "所有类型";
//
//        // 获取所有必要的参数
//        String researchLeader = getValue(params, "researchLeader");
//        String contactPhone = getValue(params, "contactPhone");
//        String email = getValue(params, "email");
//        String institution = getValue(params, "institution");
//        String applicationDepartment = getValue(params, "applicationDepartment");
//        String applicationDate = getValue(params, "applicationDate");
//        String researchPurpose = getValue(params, "researchPurpose");
//        String researchContent = getValue(params, "researchContent");
//        String researchTeam = getValue(params, "researchTeam");
//        String expectedOutcome = getValue(params, "expectedOutcome");
//        String sampleSize = getValue(params, "sampleSize");
//        String researchPeriod = getValue(params, "researchPeriod");
//        String evaluationIndicators = getValue(params, "evaluationIndicators");
//
//        // 获取输出语言参数，默认为中文
//        String outputLanguage = getValue(params, "outputLanguage");
//        if (outputLanguage == null || outputLanguage.trim().isEmpty()) {
//            outputLanguage = "中文";
//        }
//
//        // 根据输出语言设置语言指令
//        String languageInstruction = "";
//        if ("英文".equals(outputLanguage) || "English".equalsIgnoreCase(outputLanguage)) {
//            languageInstruction = """
//
//                    【重要：语言输出要求】
//                    请使用英文撰写整份报告的所有内容，包括：
//                    - 所有章节标题、小节标题
//                    - 所有正文内容、段落描述
//                    - 表格的标题和内容
//                    - 摘要和关键词
//
//                    注意：
//                    1. 文献引用中的原始文献标题和期刊名称保持原文（中文文献保留中文，英文文献保留英文）
//                    2. 专有名词的翻译要准确、规范
//                    3. 使用学术规范的英文表达，确保语法正确、表达地道
//                    4. 保持专业的学术写作风格
//                    5. 除了中文关键词外，所有内容全部使用英文撰写，包括标题
//
//                    """;
//        } else {
//            languageInstruction = """
//
//                    【重要：语言输出要求】
//                    请使用中文撰写整份报告的所有内容。
//
//                    注意：
//                    1. 文献引用中的原始文献标题和期刊名称保持原文（中文文献保留中文，英文文献保留英文）
//                    2. 使用规范的中文学术表达
//                    3. 保持专业的学术写作风格
//
//                    """;
//        }
//
//        return languageInstruction + """
//                【角色与任务】
//                您是医学科研项目管理与评审专家，精通国家自然科学基金等申报
//
//                您的任务：严格依据下方【固定章节结构】，结合提供的【项目参数】与【ES文献JSON数据】，生成内容完整、逻辑严密的项目申报书全文。
//
//                【全局写作原则】
//
//                结构严格：章节/小节名称必须与下方【固定章节结构】完全一致，不增、删、改。
//
//                内容充实：每小节必须有实质内容，禁止“同上”、“略”等敷衍表述。
//
//                每个小标题和内容之间进行换行，大标题加粗居中，小标题(如1.1、2.1、3.2.1等)靠左加粗
//
//                语言规范：使用正式、学术化的语言，确保内容可直接用于基金申报。
//
//                基于事实：所有论述必须基于【项目参数】、【ES文献JSON】、【网页搜索结果】及医学领域常识，不得虚构。
//
//                全文统一核对文献引用：作者、年份、期刊、文中标注顺序（中英文混排时尤其容易出错）。对于方法学关键文献（如GRADE、WHO手册、中医指南规范），建议在全文中对应标注，体现“不是凭印象写的”。
//
//                内容引用的文献统一使用文献编号如:[1]，引用的文献只显示编号，文献列表中最显示编号和相应的内容
//                每个小标题编号引用模板中的，不额外添加新的标题编号
//
//                 若定位青年项目：
//                ①建议把“主持5项国家级项目”等改为“参与若干项目，主持/承担子课题”等更贴近青年阶段的表述；
//                ②强调代表性工作和潜力，而不是绝对数量。
//
//                 若定位面上：
//                ①可以保留较强的基础表述，但记得：给出2–3篇直接相关的代表作简要说明其具体贡献。
//
//                逻辑连接词使用数量适当，使自然段落更加衔接。
//
//                格式规范：
//                删除所有无关符号（如 #, =, |, - 等），仅保留合理章节分隔符。
//                统一标题层级（如一、（一）、1. 等）。
//                段落间空一行，保持版面整洁。
//                全文使用标准宋体/新罗马体，保持字体字号一致。
//                确保最终文档符合通用报告格式标准。
//
//                【固定章节结构及每部分生成规则】
//                （注：如果是输出语言是英文，那么小标题翻译成英文，如果是中文，则为中文）
//
//                <h1>项目名称</h1> <br>
//                （项目名称字不显示，直接显示项目名称的内容）
//
//                项目名称翻译<br>
//                （不显示，直接显示项目名称的翻译内容）：提供准确、学术规范的中译英，控制15-25个英文单词。
//
//                <h4>摘要</h4> (250-400字)：需含背景、目的、方法、预期成果，突出创新与应用价值，语言精炼客观，强化1-2个可以被检验的核心科学问题，例如：如何构建适用于中医语境的患者价值观层级模型，并验证其结构效度？重点聚焦2-3个病种以及对接1-2部具体中医临床指南，其他病种可以写为“扩展验证”而非主战场，缓解“任务过宽”的疑虑。<br>
//
//                <h4>关键词</h4> (3-5个)：涵盖对象、方法、技术，按重要性排序，用“；”分隔。<br>
//
//                <h4>英文关键词</h4>：与中文一一对应，标准学术英文，首字母大写，用“;”分隔。<br>
//
//                <h5>表1：</h5><br>
//                提供规范三线表，包含预期论文、软件著作权/规范/白皮书、对接的指南案例或方法手册等关键信息，其中指标既要有压力，又要不过分KPI化（避免太极端的star数量、引文数等）配50-100字表注。
//                <h2>1、研究意义</h2><br>
//                （该段内容要突出对患者价值观、指南质量、中医现代化的意义）
//
//                <h4>(1) 融入患者价值观的指南能为临床提供复杂医疗决策框架和参考，降低决策负担，推动以患者为中心的医疗实践</h4><br>
//                (250-300字)：阐述患者价值观在指南中的核心作用及对临床决策的帮助，引用1-2篇文献。
//
//                <h4>(2) 传统患者价值观信息获取方式阻碍了价值观与指南融合</h4><br>
//                (250-300字)：分析传统获取方式（如成本高、样本小）的局限性及其对指南制定的阻碍。
//
//                <h4>(3) 利用网络患者自述数据和生成式人工智能打通指南与患者价值观融合的壁垒</h4><br>
//                (250-300字)：阐明利用网络自述数据与生成式AI解决上述瓶颈的优势与可行性。
//
//                (250-300字)：综合升华，概括研究的必要性与重要性，用1-2句bullet形式“明确要解决的缺口”。
//
//                <h2>2、国内外研究现状分析及存在问题</h2><br>
//                （该段内容要从“已有研究做了什么+还差什么”的角度进行结构化对比，减少重复背景描述）
//
//                <h4>(1)中医指南融入患者价值观现状及瓶颈</h4><br>
//                (250-300字)：综述中医指南融入患者价值观的现状与瓶颈（至少3点），引用5-8篇近5年文献。
//
//                <h4>(2)中医指南患者价值观信息获取的新思路</h4><br>
//                (250-300字)：介绍网络数据挖掘等新思路，分析其优势与应用，引用4-6篇文献。
//
//                <h4>(3) 依托生成式人工智能助力网络患者价值观信息高效利用</h4><br>
//                (250-300字)：综述生成式AI在医疗文本处理的应用与优势，分析不足，引用5-7篇近3年文献，突出本研究创新。
//
//                (250字左右)用1-2句bullet形式“明确要解决的缺口“
//
//                <h2>3、研究思路</h2><br>
//                (250-300字)：清晰描述“问题→方法→系统→评价”的完整逻辑闭环，体现科学性。
//
//                <h2>4、应用方向或应用前景</h2><br>
//                (250-300字)：列举3-5个具体方向，分析效益与推广可能。
//
//                <h2>2. 项目的研究内容、研究目标，以及拟解决的关键科学问题</h2><br>（此部分为重点阐述内容）
//
//                <h3>2.1 研究内容</h3><br>
//
//                (300-400字)：总阶段，概括主要模块及其逻辑关系。
//
//                <h4>2.1.1 基于关键词与向量搜索的中医相关患者价值观数据自动检索方案</h4><br>
//                (300-350字)：详述融合关键词与向量搜索的自动检索方案设计，突出中医优化。内容中体现中医医学实体识别和关键词搜索+向量搜索->向量排序(混合式中医患者自述数据搜索方案的效果评估)。
//
//                <h4>2.1.2 基于中医指南临床问题结构形成患者价值观的报告</h4><br>
//                (300-350字)：阐述基于中医指南临床问题结构的报告框架构建依据与要素。
//
//                <h4>2.1.3基于生成式人工智能技术构建智能认知的患者价值观生成</h4><br>
//                (300-350字)：详述生成式AI模型的构建方法、算法与训练策略。
//
//                (350字左右)：本项目拟验证的关键科学假设。
//
//                <h3>2.2 研究目标</h3><br>
//                 (300-400字)：设定3-5个明确、可量化目标，使用“建立…”、“构建…”等动词，强化1-2个可以被检验的核心科学问题，重点聚焦2-3个病种以及对接1-2部具体中医临床指南，其他病种可以写为“扩展验证”而非主战场，缓解“任务过宽”的疑虑。
//
//                <h3>2.3 拟解决的关键科学问题</h3><br>
//                （该2.3标题下的每个子标题问题末尾，加一句“本项目将通过...路径来回答这一问题”：）
//                ①对应检索问题 → 通过混合检索 +排序模型 + 人工金标准集来定量验证；（150字左右）
//                ②对应映射问题 → 通过构建“文本片段—价值要素—指南条目”矩阵，并设计一致性检验方案等。（150字左右）
//
//                <h4>2.3.1精准定位与中医指南相关的患者自述数据</h4><br>
//                (150-250字)：明确提出精准定位中医相关自述数据的挑战、难点及解决思路。
//
//                <h4>2.3.2建立自述数据-患者价值观-中医指南的映射关系</h4><br>
//                (150-250字)：阐述建立“数据-价值观-指南”映射关系的理论难题与创新思路。
//
//                <h4>2.3.3可靠性与偏倚控制</h4><br>
//                （150-250字）强调会系统评估：平台偏倚、表达偏倚、算法偏倚对结果的影响。
//
//                <h2>3. 拟采取的研究方案及可行性分析</h2><br>
//                （包括研究方法、技术路线、实验手段、关键技术等说明）
//
//                <h3>3.1 技术路线</h3><br>
//                （300-350字）该内容中明确：
//                ①数据检索模块会根据专家评审反馈进行多轮调参；
//                ②报告框架在试用中根据专家和患者反馈迭代修订；
//                ③生成模型也会迭代 SFT → RLHF → error analysis → 再训练。
//                提供包含研究起点、阶段任务、关键节点、最终成果的技术路线内容。重点聚焦2-3个病种以及对接1-2部具体中医临床指南。
//
//                <h3>3.2 研究方案</h3><br>
//
//                <h4>3.2.1基于关键词与向量搜索的中医患者自述数据自动检索方案</h4><br>
//
//                <h5>(1)自动生成覆盖所有目标患者自述数据库的检索式</h5><br>
//                (250-300字)：详述检索式生成的算法、多库覆盖及优化策略。
//
//                <h5>(2) 融合检索式搜索和向量检索的混合搜索方案</h5><br>
//                (250-300字)：详述关键词与向量检索的融合机制与权重策略。
//
//                <h5>(3)混合式中医患者自述数据搜索方案效果评估</h5><br>
//                (200-250字)：明确评估指标（如F1值）及与基线对比的实验设计。
//
//                (200-250字)数据获取的伦理审批流程和平台授权策略、对可能的偏倚类型进行分类（年龄、性别、城镇/农村、网络使用习惯等）、简述将采用的偏倚评估和缓解方法。
//
//                <h4>3.2.2基于中医指南临床问题结构形成患者价值观的报告框架</h4><br>
//                (总述250-300字)：说明报告框架研制的整体方法论。
//
//                <h5>(1)文献综述</h5><br>
//                (250-300字)：说明检索策略、分析维度与要素提炼方法。
//
//                <h5>(2)专家共识</h5><br>
//                (250-300字)：说明专家遴选、共识形成方法（如德尔菲法）及标准。
//
//                <h4>3.2.3基于生成式人工智能技术构建智能认知的患者价值观生成模型</h4><br>
//
//                <h5>(1)基于患者价值观报告框架的文本分类、筛选与关键信息提取</h5><br>
//                (250-300字)：详述基于框架的文本分类、筛选与信息提取技术方案。
//
//                <h5>(2)基于患者价值观报告框架的价值观报告生成</h5><br>
//                (250-300字)：详述价值观报告生成的AI模型训练、优化及质量控制。
//
//                <h5>(3)生成效果评估</h5><br>
//                (200-300字)：设定多维度评估指标，说明人工与自动评估结合方式。
//
//                <h5>样本量与工作量估算</h5><br>（这段文字不显示）
//                ①（100字左右）说明平台数据量预估（如百万级）、预期跟病种相关的中医内容比例；
//                ②（100字左右）标注团队人数、每人预期工作量、预计完成时间；
//                ③（100字左右）对于稀有价值观类别的处理策略（重采样、聚类扩展等）。
//
//                <h4>3.2.4 中医指南患者价值观智能报告系统构建与效果评价</h4><br>
//                (350-400字)：说明智能报告系统的架构、开发、效果评价的例子（1个即可）、评价方案及可推广性。
//
//                <h3>3.3 关键技术</h3><br>
//
//                <h4>3.3.1 融合关键词与向量搜索的混合式大数据搜索</h4><br>
//                (300-350字)：深入阐述混合搜索的核心技术原理、创新点及解决方案。
//
//                <h4>3.3.2基于患者自述数据的特点和价值观评估报告的框架要求，构建排序模型</h4><br>
//                (300-350字)：说明针对自述数据与报告框架的排序模型设计思路与算法。
//
//                <h3>3.4 可行性分析</h3><br>
//
//                <h4>3.4.1 立项依据充分</h4><br>
//                (200-300字)：说明研究符合国家政策与行业需求，引用相关文件。
//
//                <h4>3.4.2 研究方法熟悉</h4><br>
//                (200-300字)：说明团队在相关方法上的研究基础与积累。
//
//                <h4>3.4.3技术团队支持</h4><br>
//                (200-300字)：介绍团队构成、技术优势与合作基础。
//
//                <h4>3.4.4数据足量可用</h4><br>
//                (200-300字)：说明数据来源、数量、质量及合规性。
//
//                <h5>表2：</h5><br>
//                提供规范三线表，包含数据来源、检索方式和时间、结果数量、患者提问示例（可分析的患者价值观）等关键信息，配50-100字表注。
//
//                <h2>4. 本项目的特色与创新之处</h2><br>
//                （其中列出的创新点要满足以下规则：
//                 把创新点更“技术化 + 可验证化”：
//                ①创新点1： 提出“中医指南—患者价值观”三层映射矩阵构建方法，并通过实际指南案例验证其可用性；
//                ②创新点2： 开发中医知识增强的 LLM 价值观生成模型，并证明其在某些任务上优于通用模型；
//                ③创新点3： 形成一套可复用的方法学规范（检索流程、标注规范、报告模板等），可直接支撑后续指南项目。
//                 减弱绝对化措辞（“首次”“首个”），改为“有望成为”“拟构建”。）
//
//                <h3>4.1 思路创新</h3><br>
//                (250-300字)：提出2-3个创新点，说明内容、差异与价值。
//
//                <h3>4.2 技术创新</h3><br>
//                (250-300字)：提出2-3个技术创新点，说明突破、优势与应用价值。
//
//                文献列表
//                （请按照GB/T 7714规范，生成20篇权威参考文献列表，每篇以带方括号的编号（如[1]）起始并独占一行。）
//
//                【输入数据】
//
//                项目参数与ES文献JSON数据：%s
//
//                网页搜索结果（包含最新文献链接和摘要）：%s
//
//                上一版项目申报书（供参考风格）：%s
//
//                【最终输出要求】
//                请直接生成完整的项目申报书文本。
//                严格对应上方【固定章节结构】。
//                每部分信息充实，逻辑严密，论述深度接近正式docx文档。
//                语言正式、专业，达到申报书草稿水准。
//                每个小标题要带上h标签
//                如果要求输出语言为英文，那么则将小标题等全部翻译为英文，如：4.2 技术创新->4.2Technological innovation
//                严格遵守格式规范：删除无关符号、统一标题层级、调整段落间距、统一文本格式。
//
//                """.formatted(
//                topicName != null ? topicName : drugName + "研究",
//                drugName != null ? drugName : "未知",
//                indication != null ? indication : "未知",
//                keywordsStr,
//                researchTypesStr,
//                startDate != null ? startDate : "未知",
//                endDate != null ? endDate : "未知",
//                startDate != null ? startDate : "未知",
//                endDate != null ? endDate : "未知",
//                researchLeader != null ? researchLeader : "未知",
//                applicationDepartment != null ? applicationDepartment : institution,
//                contactPhone != null ? contactPhone : "未知",
//                email != null ? email : "未知",
//                sampleSize != null ? sampleSize : "未知",
//                researchPeriod != null ? researchPeriod : "未知",
//                evaluationIndicators != null ? evaluationIndicators : "未知",
//                expectedOutcome != null ? expectedOutcome : "未知",
//                truncate(cleanJson, 80000),
//                webSearchResults.isEmpty() ? "无网页搜索结果" : webSearchResults,
//                latestPaper.isEmpty() ? "无历史版本" : latestPaper
//        );
//    }

    private void sendFragment(String dialogId, String fragment, String id,
                              String parentId,
                              String userId,StreamDataCallback callback) {
        try {
            String msg = objectMapper.writeValueAsString(Map.of(
                    "type", "paper_fragment",
                    "content", fragment
            ));
            callback.onData( msg);
        } catch (Exception e) {
            log.warn("Failed to send fragment", e);
        }
    }

    /**
     * 发送错误消息
     *
     * @param dialogId 对话ID
     * @param error 错误信息
     * @param id 消息ID
     * @param parentId 父消息ID
     * @param userId 用户ID
     */
    private void sendError(String dialogId, String error, String id,
                           String parentId,
                           String userId) {
        try {
            String msg = objectMapper.writeValueAsString(Map.of(
                    "type", "error",
                    "message", error
            ));
            // webSocketClient.sendMessage(dialogId, msg, id, parentId, userId);
        } catch (Exception e) {
            log.warn("Failed to send error", e);
        }
    }

    private String getLatestPaper(AnalysisContext context) {
        try {
            if (context == null || context.getSession() == null) {
                return "";
            }

            List<Session.PaperVersion> versions = context.getSession().getPaperVersions();
            if (versions == null || versions.isEmpty()) {
                return "";
            }

            Session.PaperVersion latest = versions.get(0);
            return latest != null && latest.getFullPaper() != null ? latest.getFullPaper() : "";
        } catch (Exception e) {
            log.warn("Failed to get latest paper", e);
            return "";
        }
    }

    private String getValue(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val instanceof java.util.List && !((java.util.List<?>) val).isEmpty()) {
            return ((java.util.List<?>) val).get(0).toString();
        }
        return val != null ? val.toString() : "未知";
    }

    private String truncate(String str, int len) {
        return str == null ? "" : str.length() <= len ? str : str.substring(0, len) + "...";
    }

    // ==================== 分步骤报告生成方法 ====================


    // ==================== 背景分析相关方法 ====================

    /**
     * 生成研究背景分析（流式输出）
     * 用于"进行背景分析"步骤
     */
    public String generateResearchBackground(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_RESEARCH_BACKGROUND_START | traceId={}", traceId);

        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);

        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";

        String prompt = buildResearchBackgroundPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);

        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createReportStreamCallback1(contentBuilder, callback);

        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_RESEARCH_BACKGROUND_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_RESEARCH_BACKGROUND_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }

    /**
     * 生成证据检索分析（流式输出）
     * 用于"进行背景分析"步骤
     */
    public String generateEvidenceRetrieval(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_EVIDENCE_RETRIEVAL_START | traceId={}", traceId);

        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);

        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";

        String prompt = buildEvidenceRetrievalPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);

        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createReportStreamCallback1(contentBuilder, callback);

        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_EVIDENCE_RETRIEVAL_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_EVIDENCE_RETRIEVAL_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }

    /**
     * 生成文献综述（流式输出）
     * 用于"进行背景分析"步骤
     */
    public String generateLiteratureReview(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_LITERATURE_REVIEW_START | traceId={}", traceId);

        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);

        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";

        String prompt = buildLiteratureReviewPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);

        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createReportStreamCallback1(contentBuilder, callback);

        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_LITERATURE_REVIEW_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_LITERATURE_REVIEW_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }

    /**
     * 生成领域发展趋势分析（流式输出）
     * 用于"进行背景分析"步骤
     */
    public String generateFieldTrends(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_FIELD_TRENDS_START | traceId={}", traceId);

        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);

        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";

        String prompt = buildFieldTrendsPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);

        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createReportStreamCallback1(contentBuilder, callback);

        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_FIELD_TRENDS_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_FIELD_TRENDS_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }

    /**
     * 生成报告摘要章节（流式输出）
     * 
     * 工作流程：
     * 1. 调用LLM生成包含report_content和summary的JSON格式响应
     * 2. summary会流式输出到前端（通过callback）
     * 3. 返回完整的JSON字符串
     * 4. 调用方需要使用separateReportContentAndSummary()方法解析JSON，提取纯净的report_content
     * 
     * @param context 分析上下文
     * @param callback 流式回调（用于输出summary）
     * @return 完整的JSON格式响应字符串，需要通过separateReportContentAndSummary()解析
     */
    public String generateAbstract(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_ABSTRACT_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildAbstractPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_ABSTRACT_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_ABSTRACT_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 生成研究意义章节（流式输出）
     * 
     * 工作流程：
     * 1. 调用LLM生成包含report_content和summary的JSON格式响应
     * 2. summary会流式输出到前端（通过callback）
     * 3. 返回完整的JSON字符串
     * 4. 调用方需要使用separateReportContentAndSummary()方法解析JSON，提取纯净的report_content
     * 
     * @param context 分析上下文
     * @param callback 流式回调（用于输出summary）
     * @return 完整的JSON格式响应字符串，需要通过separateReportContentAndSummary()解析
     */
    public String generateResearchSignificance(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_RESEARCH_SIGNIFICANCE_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildResearchSignificancePrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_RESEARCH_SIGNIFICANCE_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_RESEARCH_SIGNIFICANCE_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 生成国内外研究现状及存在问题章节（流式输出）
     */
    public String generateResearchStatus(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_RESEARCH_STATUS_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildResearchStatusPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_RESEARCH_STATUS_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_RESEARCH_STATUS_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 生成研究思路章节（流式输出）
     */
    public String generateResearchIdea(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_RESEARCH_IDEA_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildResearchIdeaPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_RESEARCH_IDEA_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_RESEARCH_IDEA_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 生成应用方向或应用前景章节（流式输出）
     */
    public String generateApplicationProspect(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_APPLICATION_PROSPECT_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildApplicationProspectPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_APPLICATION_PROSPECT_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_APPLICATION_PROSPECT_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 生成研究内容章节（流式输出）
     */
    public String generateResearchContent(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_RESEARCH_CONTENT_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildResearchContentPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_RESEARCH_CONTENT_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_RESEARCH_CONTENT_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 生成研究目标章节（流式输出）
     */
    public String generateResearchObjectives(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_RESEARCH_OBJECTIVES_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildResearchObjectivesPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_RESEARCH_OBJECTIVES_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_RESEARCH_OBJECTIVES_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 生成拟解决的关键科学问题章节（流式输出）
     */
    public String generateKeyScientificProblems(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_KEY_SCIENTIFIC_PROBLEMS_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildKeyScientificProblemsPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_KEY_SCIENTIFIC_PROBLEMS_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_KEY_SCIENTIFIC_PROBLEMS_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 生成技术路线章节（流式输出）
     */
    public String generateTechnicalRoute(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_TECHNICAL_ROUTE_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildTechnicalRoutePrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_TECHNICAL_ROUTE_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_TECHNICAL_ROUTE_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 生成研究方案章节（流式输出）
     */
    public String generateResearchPlan(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_RESEARCH_PLAN_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildResearchPlanPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_RESEARCH_PLAN_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_RESEARCH_PLAN_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 生成关键技术章节（流式输出）
     */
    public String generateKeyTechnologies(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_KEY_TECHNOLOGIES_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildKeyTechnologiesPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_KEY_TECHNOLOGIES_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_KEY_TECHNOLOGIES_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 生成可行性分析章节（流式输出）
     */
    public String generateFeasibilityAnalysis(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_FEASIBILITY_ANALYSIS_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildFeasibilityAnalysisPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_FEASIBILITY_ANALYSIS_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_FEASIBILITY_ANALYSIS_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 生成思路创新章节（流式输出）
     */
    public String generateIdeaInnovation(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_IDEA_INNOVATION_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildIdeaInnovationPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_IDEA_INNOVATION_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_IDEA_INNOVATION_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 生成技术创新章节（流式输出）
     */
    public String generateTechnologyInnovation(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_TECHNOLOGY_INNOVATION_START | traceId={}", traceId);
        
        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);
        
        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";
        
        String prompt = buildTechnologyInnovationPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
        
        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);
        
        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_TECHNOLOGY_INNOVATION_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_TECHNOLOGY_INNOVATION_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }

    public String generateResearchBasis(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_RESEARCH_BASIS_START | traceId={}", traceId);

        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);

        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";

        String prompt = buildResearchBasisPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);

        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);

        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_RESEARCH_BASIS_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_RESEARCH_BASIS_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }

    public String generateWorkConditions(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_WORK_CONDITIONS_START | traceId={}", traceId);

        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);

        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";

        String prompt = buildWorkConditionsPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);

        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);

        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_WORK_CONDITIONS_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_WORK_CONDITIONS_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }

    public String generateResearchPlanAndSchedule(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_RESEARCH_PLAN_AND_SCHEDULE_START | traceId={}", traceId);

        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);

        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";

        String prompt = buildResearchPlanAndSchedulePrompt(topicName, cleanJson, webSearchResults, latestPaper, params);

        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);

        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_RESEARCH_PLAN_AND_SCHEDULE_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_RESEARCH_PLAN_AND_SCHEDULE_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }

    public String generateExpectedResearchOutcomes(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_EXPECTED_RESEARCH_OUTCOMES_START | traceId={}", traceId);

        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);

        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";

        String prompt = buildExpectedResearchOutcomesPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);

        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);

        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_EXPECTED_RESEARCH_OUTCOMES_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_EXPECTED_RESEARCH_OUTCOMES_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }

    public String generateRiskAnalysisAndCountermeasures(AnalysisContext context, StreamDataCallback callback) {
        String traceId = context.getTraceId();
        log.info("GENERATE_RISK_ANALYSIS_AND_COUNTERMEASURES_START | traceId={}", traceId);

        Map<String, Object> params = context.getQueryParams();
        String cleanJson = context.getCleanJson();
        String latestPaper = getLatestPaper(context);

        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
        String webSearchResults = params != null && params.containsKey("webSearchResults")
                ? params.get("webSearchResults").toString() : "";

        String prompt = buildRiskAnalysisAndCountermeasuresPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);

        StringBuilder contentBuilder = new StringBuilder();
        StreamDataCallback adapterCallback = createDualParameterStreamCallback(contentBuilder, callback);

        try {
            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
            log.info("GENERATE_RISK_ANALYSIS_AND_COUNTERMEASURES_COMPLETE | traceId={} | length={}", traceId, content.length());
            return content;
        } catch (Exception e) {
            log.error("GENERATE_RISK_ANALYSIS_AND_COUNTERMEASURES_ERROR | traceId={}", traceId, e);
            return contentBuilder.toString();
        }
    }
    
    /**
     * 分离报告内容和摘要
     * 解析LLM返回的JSON格式字符串，提取report_content和summary
     * 
     * 功能说明：
     * 1. 清理JSON字符串（移除Markdown代码块标记```json和```）
     * 2. 解析JSON，提取report_content和summary字段
     * 3. 清理report_content，确保不包含JSON标记、summary字段等杂质
     * 4. 返回纯净的Markdown格式report_content和summary
     * 
     * 使用示例：
     * <pre>
     * String jsonResponse = generateAbstract(context, callback);
     * ReportSectionResult result = separateReportContentAndSummary(jsonResponse);
     * String pureReportContent = result.getReportContent(); // 纯净的Markdown内容
     * String summary = result.getSummary(); // 摘要内容
     * reportSections.add(pureReportContent); // 添加到最终报告
     * </pre>
     * 
     * @param llmResponse LLM返回的JSON格式响应（可能包含```json标记）
     * @return ReportSectionResult对象，包含分离后的纯净reportContent和summary
     */
    public ReportSectionResult separateReportContentAndSummary(String llmResponse) {
        ReportSectionResult result = new ReportSectionResult();
        
        if (llmResponse == null || llmResponse.isEmpty()) {
            log.warn("Empty LLM response received");
            return result;
        }
        
        try {
            // 第一步：清理JSON字符串，去除Markdown代码块标记
            String cleanedJson = cleanJsonString(llmResponse);
            
            // 第二步：尝试解析JSON
            JSONObject jsonObject = parseJsonWithRetry(cleanedJson);
            
            if (jsonObject != null) {
                // 第三步：提取report_content和summary字段
                String reportContent = jsonObject.getString("report_content");
                String summary = jsonObject.getString("summary");
                
                // 第四步：清理report_content，确保不包含JSON标记和summary
                if (reportContent != null && !reportContent.isEmpty()) {
                    reportContent = cleanReportContentFromJson(reportContent);
                    result.setReportContent(reportContent);
                    log.info("Successfully extracted report_content, length: {}", reportContent.length());
                } else {
                    log.warn("report_content is null or empty in JSON");
                }
                
                // 第五步：设置summary
                if (summary != null && !summary.isEmpty()) {
                    result.setSummary(summary.trim());
                    log.info("Successfully extracted summary, length: {}", summary.length());
                } else {
                    log.warn("summary is null or empty in JSON");
                }
            } else {
                // 如果JSON解析失败，尝试直接使用内容（但记录警告）
                log.warn("Failed to parse as JSON, using raw content as report_content");
                result.setReportContent(cleanReportContentFromJson(llmResponse));
            }
        } catch (Exception e) {
            log.error("Failed to separate report content and summary", e);
            // 发生异常时，清理后作为报告内容
            result.setReportContent(cleanReportContentFromJson(llmResponse));
        }
        
        return result;
    }
    
    /**
     * 清理JSON字符串，去除Markdown代码块标记
     */
    private String cleanJsonString(String jsonStr) {
        if (jsonStr == null) {
            return "";
        }
        
        String cleaned = jsonStr.trim();
        
        // 去除开头的```json或```标记
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim();
        }
        
        // 去除结尾的```标记
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }
        
        // 移除控制字符
        cleaned = cleaned.replaceAll("[\\u0000-\\u001F\\u007F]", "");
        
        return cleaned;
    }
    
    /**
     * 尝试解析JSON，包含多次重试和修复机制
     */
    private JSONObject parseJsonWithRetry(String jsonStr) {
        // 第一次尝试：直接解析
        try {
            return JSONObject.parseObject(jsonStr);
        } catch (JSONException e) {
            log.debug("First JSON parse attempt failed: {}", e.getMessage());
        }
        
        // 第二次尝试：修复可能未闭合的字符串
        try {
            String fixed = jsonStr;
            // 检查最后一个引号和右大括号的位置
            int lastQuote = fixed.lastIndexOf('"');
            int lastBrace = fixed.lastIndexOf('}');
            
            if (lastQuote > lastBrace) {
                // 字符串可能未闭合，尝试添加引号和大括号
                fixed = fixed + "\"}";
                return JSONObject.parseObject(fixed);
            }
        } catch (JSONException e) {
            log.debug("Second JSON parse attempt failed: {}", e.getMessage());
        }
        
        // 第三次尝试：移除末尾可能的不完整内容
        try {
            String fixed = jsonStr;
            int lastCompleteBrace = fixed.lastIndexOf("}");
            if (lastCompleteBrace > 0) {
                fixed = fixed.substring(0, lastCompleteBrace + 1);
                return JSONObject.parseObject(fixed);
            }
        } catch (JSONException e) {
            log.debug("Third JSON parse attempt failed: {}", e.getMessage());
        }
        
        log.error("All JSON parse attempts failed for content length: {}", jsonStr.length());
        return null;
    }
    
    /**
     * 清理报告内容，移除JSON标记和其他杂质
     * 确保最终输出的是纯净的Markdown格式内容
     */
    private String cleanReportContentFromJson(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        
        String cleaned = content.trim();
        
        // 移除可能存在的JSON标记
        cleaned = cleanJsonString(cleaned);
        
        // 如果内容看起来像是完整的JSON对象，尝试提取report_content字段
        if (cleaned.startsWith("{") && cleaned.contains("report_content")) {
            try {
                JSONObject obj = JSONObject.parseObject(cleaned);
                String reportContent = obj.getString("report_content");
                if (reportContent != null && !reportContent.isEmpty()) {
                    cleaned = reportContent;
                }
            } catch (Exception e) {
                log.debug("Content looks like JSON but failed to parse: {}", e.getMessage());
            }
        }
        
        // 移除可能的JSON转义字符
        cleaned = cleaned.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\r", "\r");
        
        // 确保不包含summary字段的内容
        // 如果内容包含类似 "summary": "..." 的模式，移除它
        cleaned = cleaned.replaceAll("\"summary\"\\s*:\\s*\"[^\"]*\"", "");
        cleaned = cleaned.replaceAll(",\\s*$", ""); // 移除末尾可能的逗号
        
        // 移除开头和结尾的大括号（如果是JSON片段）
        if (cleaned.startsWith("{") && !cleaned.contains("\n")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.endsWith("}") && !cleaned.contains("\n")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        
        // 移除多余的空行
        cleaned = cleaned.replaceAll("\n{3,}", "\n\n");
        
        return cleaned.trim();
    }
    
//    /**
//     * 清理报告内容格式
//     * 移除多余的空行、格式标记和特殊字符
//     *
//     * @param reportContent 原始报告内容
//     * @return 清理后的报告内容
//     */
//    public String cleanReportContent(String reportContent) {
//        if (reportContent == null || reportContent.isEmpty()) {
//            return reportContent;
//        }
//
//        String cleaned = reportContent;
//
//        // 1. 移除多余的空行（连续超过2个空行）
//        cleaned = cleaned.replaceAll("\\n{3,}", "\\n\\n");
//
//        // 2. 移除行尾多余的空格
//        cleaned = cleaned.replaceAll("\\s+\\n", "\\n");
//
//        // 3. 移除开头和结尾的空行
//        cleaned = cleaned.trim();
//
//        // 4. 确保标题前后有适当的空行
//        cleaned = cleaned.replaceAll("(</?h[1-6]>)", "\\n$1\\n");
//
//        // 5. 移除可能的JSON格式标记
//        cleaned = cleaned.replaceAll("^\\{[\\s\\S]*?\\}", "");
//
//        // 6. 再次清理多余的空行
//        cleaned = cleaned.replaceAll("\\n{3,}", "\\n\\n");
//        cleaned = cleaned.trim();
//
//        return cleaned;
//    }
    
    /**
     * 生成技术路线图
     * @param report 报告内容
     * @param userId 用户ID
     * @param callback 回调函数
     * @return 更新后的报告内容
     */
    public String generateTechnicalRoadmap(String report, String userId, StreamDataCallback callback) {
        if (report == null || report.isEmpty()) {
            return report;
        }

        try {
            // 在"技术路线"小标题下方插入技术路线图
            String techRoadmapSection = "技术路线";
            int techRoadmapIndex = report.indexOf(techRoadmapSection);
            if (techRoadmapIndex != -1) {
                try {
                    // 生成技术路线图的Mermaid代码（不包含标题，因为我们会在Markdown中添加）
                    String roadmapMermaidCode = mermaidChartUtils.generateTechnologyRoadmap(report, true);

                    // 转换为静态图片并上传到OSS
                    String imageUrl = convertMermaidToOssImage(roadmapMermaidCode, userId, "roadmap", "png");
//                    Response<ImageData> imageDataResponse = Response.ImageBuilder(imageUrl);
//                    callback.onData(imageDataResponse);
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        // 生成Markdown图片链接
                        String imageMarkdown = "\n\n![技术路线图](" + imageUrl + ")\n\n<center><strong>图1：技术路线图</strong></center>\n\n";

                        // 找到技术路线小标题后的第一个换行符位置
                        int lineBreakIndex = report.indexOf("\n", techRoadmapIndex);
                        if (lineBreakIndex != -1) {
                            StringBuilder reportBuilder = new StringBuilder(report);
                            reportBuilder.insert(lineBreakIndex + 1, imageMarkdown);
                            report = reportBuilder.toString();
                        }
                    } else {
                        // 降级：插入Mermaid代码
                        int lineBreakIndex = report.indexOf("\n", techRoadmapIndex);
                        if (lineBreakIndex != -1) {
                            StringBuilder reportBuilder = new StringBuilder(report);
                            reportBuilder.insert(lineBreakIndex + 1, "\n" + roadmapMermaidCode + "\n\n");
                            report = reportBuilder.toString();
                        }
                    }
                } catch (Exception e) {
                    log.error("技术路线图生成失败", e);
                }
            }
            return report;
        } catch (Exception e) {
            log.error("生成技术路线图时发生错误", e);
            return report;
        }
    }

    /**
     * 生成研究框架图
     * @param report 报告内容
     * @param userId 用户ID
     * @param callback 回调函数
     * @return 更新后的报告内容
     */
    public String generateResearchFrameworkChart(String report, String userId, StreamDataCallback callback) {
        if (report == null || report.isEmpty()) {
            return report;
        }

        try {
            // 在"基于中医指南临床问题结构形成患者价值观的报告框架"小标题下方插入研究框架图
            String researchFrameworkSection = "基于中医指南临床问题结构形成患者价值观的报告框架";
            int researchFrameworkIndex = report.indexOf(researchFrameworkSection);
            if (researchFrameworkIndex != -1) {
                try {
                    // 生成研究框架图的Mermaid代码
                    String frameworkMermaidCode = mermaidChartUtils.generateResearchFrameworkChart(report);

                    // 转换为静态图片并上传到OSS
                    String imageUrl = convertMermaidToOssImage(frameworkMermaidCode, userId, "framework", "png");
//                    Response<ImageData> imageDataResponse = Response.ImageBuilder(imageUrl);
//                    callback.onData(imageDataResponse);
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        // 生成Markdown图片链接
                        String imageMarkdown = "\n\n![研究框架图](" + imageUrl + ")\n\n<center><strong>图2：患者价值观报告框架示意图</strong></center>\n\n";

                        // 找到小标题后的第一个换行符位置
                        int lineBreakIndex = report.indexOf("\n", researchFrameworkIndex);
                        if (lineBreakIndex != -1) {
                            StringBuilder reportBuilder = new StringBuilder(report);
                            reportBuilder.insert(lineBreakIndex + 1, imageMarkdown);
                            report = reportBuilder.toString();
                        }
                    } else {
                        // 降级：插入Mermaid代码
                        int lineBreakIndex = report.indexOf("\n", researchFrameworkIndex);
                        if (lineBreakIndex != -1) {
                            StringBuilder reportBuilder = new StringBuilder(report);
                            reportBuilder.insert(lineBreakIndex + 1, "\n" + frameworkMermaidCode + "\n\n");
                            report = reportBuilder.toString();
                        }
                    }
                } catch (Exception e) {
                    log.error("研究框架图生成失败", e);
                }
            }
            return report;
        } catch (Exception e) {
            log.error("生成研究框架图时发生错误", e);
            return report;
        }
    }
    
    /**
     * 生成文献列表章节（流式输出）
     */
//    public String generateReferences(AnalysisContext context, StreamDataCallback callback) {
//        String traceId = context.getTraceId();
//        log.info("GENERATE_REFERENCES_START | traceId={}", traceId);
//
//        Map<String, Object> params = context.getQueryParams();
//        String cleanJson = context.getCleanJson();
//        String latestPaper = getLatestPaper(context);
//
//        String topicName = params.get("topicName") != null ? params.get("topicName").toString() : "";
//        String webSearchResults = params != null && params.containsKey("webSearchResults")
//                ? params.get("webSearchResults").toString() : "";
//
//        String prompt = buildReferencesPrompt(topicName, cleanJson, webSearchResults, latestPaper, params);
//
//        StringBuilder contentBuilder = new StringBuilder();
//        StreamDataCallback adapterCallback = createReportStreamCallback(contentBuilder, callback);
//
//        try {
//            String content = llmInvocationService.invokeStream(prompt, adapterCallback, traceId);
//            log.info("GENERATE_REFERENCES_COMPLETE | traceId={} | length={}", traceId, content.length());
//            return content;
//        } catch (Exception e) {
//            log.error("GENERATE_REFERENCES_ERROR | traceId={}", traceId, e);
//            return contentBuilder.toString();
//        }
//    }
    
    /**
     * 拼接所有章节成完整报告
     * 
     * 注意事项：
     * 1. sections中的每个章节都应该是通过separateReportContentAndSummary()解析后的纯净report_content
     * 2. 不应包含JSON标记、summary字段等杂质
     * 3. 最终输出的是纯Markdown格式的完整报告
     * 
     * @param sections 章节内容列表（按顺序），每个元素都是纯净的Markdown格式内容
     * @return 完整的Markdown格式报告
     */
    public String assembleFullReport(List<String> sections) {
        log.info("ASSEMBLE_FULL_REPORT_START | sectionCount={}", sections.size());
        
        StringBuilder fullReport = new StringBuilder();
        int validSectionCount = 0;
        
        for (int i = 0; i < sections.size(); i++) {
            String section = sections.get(i);
            if (section != null && !section.trim().isEmpty()) {
                // 检查section是否包含JSON标记或summary字段（用于调试）
                if (section.contains("\"report_content\"") || section.contains("\"summary\"")) {
                    log.warn("Section {} appears to contain JSON format, this may indicate improper parsing", i);
                }

                String sectionTrimmed = section.trim();
                String currentTrimmed = fullReport.toString().trim();

                if (!currentTrimmed.isEmpty()) {
                    if (currentTrimmed.contains(sectionTrimmed)) {
                        continue;
                    }
                    if (sectionTrimmed.contains(currentTrimmed)) {
                        fullReport.setLength(0);
                        fullReport.append(sectionTrimmed).append("\n\n");
                        validSectionCount++;
                        continue;
                    }

                    String normalizedSection = sectionTrimmed.replaceAll("\\s+", "");
                    String normalizedCurrent = currentTrimmed.replaceAll("\\s+", "");

                    if (normalizedSection.length() >= 80 && normalizedCurrent.contains(normalizedSection)) {
                        continue;
                    }
                    if (normalizedCurrent.length() >= 20
                            && sectionTrimmed.length() >= currentTrimmed.length()
                            && normalizedSection.contains(normalizedCurrent)) {
                        fullReport.setLength(0);
                        fullReport.append(sectionTrimmed).append("\n\n");
                        validSectionCount++;
                        continue;
                    }
                }

                fullReport.append(sectionTrimmed).append("\n\n");
                validSectionCount++;
            } else {
                log.debug("Section {} is empty or null, skipping", i);
            }
        }
        
        String result = fullReport.toString().trim();
        log.info("ASSEMBLE_FULL_REPORT_COMPLETE | totalSections={} | validSections={} | length={}", 
                sections.size(), validSectionCount, result.length());
        
        return result;
    }
    
    /**
     * 创建报告流式回调适配器
     */
    private StreamDataCallback createReportStreamCallback(StringBuilder contentBuilder, StreamDataCallback originalCallback) {
        return new StreamDataCallback() {
            @Override
            public void onData(Response response) {
                if (response != null && response.getData() != null) {
                    Object data = response.getData();
                    String content = null;
                    if (data instanceof com.faers.agent.dto.responseDto.ResponseContent) {
                        com.faers.agent.dto.responseDto.ResponseContent responseContent = 
                                (com.faers.agent.dto.responseDto.ResponseContent) data;
                        content = responseContent.getFront_display();
                        if (content == null || content.isEmpty()) {
                            content = responseContent.getDelta();
                        }
                    }
                    
                    if (content != null && !content.isEmpty()) {
                        contentBuilder.setLength(0);
                        contentBuilder.append(content);
                        Response reportResponse = Response.ResponseReportChatBuilder(content, "agent");
                        originalCallback.onData(reportResponse);
                    }
                }
            }

            @Override
            public void onData(String chunk) {
                contentBuilder.append(chunk);
                Response reportResponse = Response.ResponseReportChatBuilder(contentBuilder.toString(), "agent");
                originalCallback.onData(reportResponse);
            }
        };
    }



    /**
     * 创建报告流式回调适配器
     */
    private StreamDataCallback createReportStreamCallback1(StringBuilder contentBuilder, StreamDataCallback originalCallback) {
        return new StreamDataCallback() {
            @Override
            public void onData(Response response) {
                if (response != null && response.getData() != null) {
                    Object data = response.getData();
                    String content = null;
                    if (data instanceof com.faers.agent.dto.responseDto.ResponseContent) {
                        com.faers.agent.dto.responseDto.ResponseContent responseContent = 
                                (com.faers.agent.dto.responseDto.ResponseContent) data;
                        content = responseContent.getFront_display();
                        if (content == null || content.isEmpty()) {
                            content = responseContent.getDelta();
                        }
                    }
                    
                    if (content != null && !content.isEmpty()) {
                        contentBuilder.setLength(0);
                        contentBuilder.append(content);
                        Response reportResponse = Response.ResponseContentChatBuilder(content, "agent");
                        originalCallback.onData(reportResponse);
                    }
                }
            }

            @Override
            public void onData(String chunk) {
                contentBuilder.append(chunk);
                Response reportResponse = Response.ResponseContentChatBuilder(contentBuilder.toString(), "agent");
                originalCallback.onData(reportResponse);
            }
        };
    }
    
    /**
     * 创建双参数流式回调适配器，处理大模型返回的report_content和summary
     * 策略：累积完整的JSON响应，不进行流式输出，等待完整响应后再解析
     */
    private StreamDataCallback createDualParameterStreamCallback(StringBuilder contentBuilder, StreamDataCallback originalCallback) {
        return new StreamDataCallback() {
            @Override
            public void onData(Response response) {
                if (response != null && response.getData() != null) {
                    Object data = response.getData();
                    String content = null;
                    if (data instanceof com.faers.agent.dto.responseDto.ResponseContent) {
                        com.faers.agent.dto.responseDto.ResponseContent responseContent = 
                                (com.faers.agent.dto.responseDto.ResponseContent) data;
                        content = responseContent.getFront_display();
                        if (content == null || content.isEmpty()) {
                            content = responseContent.getDelta();
                        }
                    }
                    
                    if (content != null && !content.isEmpty()) {
                        // 清空并使用最新的累积内容
                        contentBuilder.setLength(0);
                        contentBuilder.append(content);
                        
                        // 只在接收到完整JSON时处理并流式输出
                        if (isCompleteJson(content)) {
                            processAndStreamSummary(content, originalCallback);
//                            processAndStreamReportContent(content, originalCallback);
                        }
                    }
                }
            }

            @Override
            public void onData(String chunk) {
                contentBuilder.append(chunk);
                String fullContent = contentBuilder.toString();
                
                // 只在接收到完整JSON时处理并流式输出
                if (isCompleteJson(fullContent)) {
                    processAndStreamSummary(fullContent, originalCallback);
//                    processAndStreamReportContent(fullContent, originalCallback);
                }
            }
        };
    }
    
    /**
     * 检查是否为完整的JSON字符串
     */
    private boolean isCompleteJson(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        // 去除首尾空白
        String trimmed = content.trim();
        
        // 检查是否以```json或```开头
        if (trimmed.startsWith("```json") || trimmed.startsWith("```")) {
            // 检查是否以```结尾
            return trimmed.endsWith("```");
        }
        
        // 检查是否是纯JSON格式（以{开头，以}结尾）
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            // 简单检查括号是否匹配
            int openBraces = 0;
            int closeBraces = 0;
            boolean inString = false;
            char prevChar = '\0';
            
            for (char c : trimmed.toCharArray()) {
                if (c == '"' && prevChar != '\\') {
                    inString = !inString;
                } else if (!inString) {
                    if (c == '{') openBraces++;
                    else if (c == '}') closeBraces++;
                }
                prevChar = c;
            }
            
            return openBraces == closeBraces && openBraces > 0;
        }
        
        return false;
    }
    
    /**
     * 处理完整JSON并流式输出summary
     * 注意：不输出report_content，只流式输出summary
     */
    private void processAndStreamSummary(String jsonContent, StreamDataCallback originalCallback) {
        try {
            // 解析JSON获取summary
            ReportSectionResult result = separateReportContentAndSummary(jsonContent);
            
            // 只流式输出summary到前端
            if (result.getSummary() != null && !result.getSummary().isEmpty()) {
                String summary = result.getSummary();
                
                // 字符流式返回summary给前端
                StringBuilder accumulatedContent = new StringBuilder();
                for (char c : summary.toCharArray()) {
                    accumulatedContent.append(c);
                    Response charResponse = Response.ResponseContentChatBuilder(accumulatedContent.toString(), "agent");
                    originalCallback.onData(charResponse);
                    
                    // 添加10毫秒延迟，模拟流式效果
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process and stream summary", e);
        }
    }
    
    /**
     * 处理完整JSON并流式输出report_content
     * 注意：使用ResponseReportChatBuilder流式输出report_content
     */
    private void processAndStreamReportContent(String jsonContent, StreamDataCallback originalCallback) {
        try {
            // 解析JSON获取report_content
            ReportSectionResult result = separateReportContentAndSummary(jsonContent);
            
            // 只流式输出report_content到前端
            if (result.getReportContent() != null && !result.getReportContent().isEmpty()) {
                String reportContent = result.getReportContent();
                
                // 字符流式返回report_content给前端
                StringBuilder accumulatedContent = new StringBuilder();
                for (char c : reportContent.toCharArray()) {
                    accumulatedContent.append(c);
                    Response charResponse = Response.ResponseReportChatBuilder(accumulatedContent.toString(), "agent");
                    originalCallback.onData(charResponse);
                    
                    // 添加10毫秒延迟，模拟流式效果
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process and stream report content", e);
        }
    }
    
    /**
     * 在报告中插入可视化图表（转换为静态图片）
     * 包括研究总体框架图、技术路线图、可行性分析表格等
     *
     * 该方法可以在其他类中调用，用于对已生成的报告内容插入可视化图表
     *
     * @param report 原始报告内容（通常是大模型生成的原始报告）
     * @param userId 用户ID（用于OSS存储路径）
     * @param callback 流式回调
     * @return 插入可视化图表后的报告内容
     */
    public String insertVisualizations(String report, String userId, StreamDataCallback callback) {
        StringBuilder chartsBuilder = new StringBuilder();
        chartsBuilder.append("正在生成图表...");
        chartsBuilder.append("\n\n");

        if (report == null || report.isEmpty()) {
            return report;
        }

        try {
            // 1. 生成技术路线图
            report = generateTechnicalRoadmap(report, userId, callback);

            // 2. 生成研究框架图
            report = generateResearchFrameworkChart(report, userId, callback);

//            // 3. 在"基于关键词与向量搜索的中医患者自述数据自动检索方案"章节下插入数据检索图
//            String dataRetrievalSection = "基于关键词与向量搜索的中医患者自述数据自动检索方案";
//            int dataRetrievalIndex = report.indexOf(dataRetrievalSection);
//            if (dataRetrievalIndex != -1) {
//                try {
//                    // 生成数据检索图的Mermaid代码
//                    String dataRetrievalMermaidCode = mermaidChartUtils.generateDataRetrievalChart(report);
//
//                    // 转换为静态图片并上传到OSS
//                    String imageUrl = convertMermaidToOssImage(dataRetrievalMermaidCode, userId, "data_retrieval", "png");
//
//                    if (imageUrl != null && !imageUrl.isEmpty()) {
//                        // 生成Markdown图片链接
//                        String imageMarkdown = "\n\n![数据检索图](" + imageUrl + ")\n\n<center><strong>图2：数据检索图</strong></center>\n\n";
//                        chartsBuilder.append("✅ 数据检索图已上传: ").append(imageUrl).append("\n");
//
//                        // 找到章节标题后的第一个换行符位置
//                        int lineBreakIndex = report.indexOf("\n", dataRetrievalIndex);
//                        if (lineBreakIndex != -1) {
//                            StringBuilder reportBuilder = new StringBuilder(report);
//                            reportBuilder.insert(lineBreakIndex + 1, imageMarkdown);
//                            report = reportBuilder.toString();
//                        }
//                    } else {
//                        chartsBuilder.append("⚠️ 数据检索图上传失败，保留Mermaid代码\n");
//                        // 降级：插入Mermaid代码
//                        int lineBreakIndex = report.indexOf("\n", dataRetrievalIndex);
//                        if (lineBreakIndex != -1) {
//                            StringBuilder reportBuilder = new StringBuilder(report);
//                            reportBuilder.insert(lineBreakIndex + 1, "\n" + dataRetrievalMermaidCode + "\n\n");
//                            report = reportBuilder.toString();
//                        }
//                    }
//                } catch (Exception e) {
//                    log.error("数据检索图生成失败", e);
//                    chartsBuilder.append("❌ 数据检索图生成失败: ").append(e.getMessage()).append("\n");
//                }
//            }
//
//            // 4. 在"基于生成式人工智能技术构建智能认知的患者价值观生成模型"章节下插入数据分析图
//            String dataAnalysisSection = "基于生成式人工智能技术构建智能认知的患者价值观生成模型";
//            int dataAnalysisIndex = report.indexOf(dataAnalysisSection);
//            if (dataAnalysisIndex != -1) {
//                try {
//                    // 生成数据分析图的Mermaid代码
//                    String dataAnalysisMermaidCode = mermaidChartUtils.generateDataAnalysisChart(report);
//
//                    // 转换为静态图片并上传到OSS
//                    String imageUrl = convertMermaidToOssImage(dataAnalysisMermaidCode, userId, "data_analysis", "png");
//
//                    if (imageUrl != null && !imageUrl.isEmpty()) {
//                        // 生成Markdown图片链接
//                        String imageMarkdown = "\n\n![数据分析图](" + imageUrl + ")\n\n<center><strong>图4：数据分析图</strong></center>\n\n";
//                        chartsBuilder.append("✅ 数据分析图已上传: ").append(imageUrl).append("\n");
//
//                        // 找到章节标题后的第一个换行符位置
//                        int lineBreakIndex = report.indexOf("\n", dataAnalysisIndex);
//                        if (lineBreakIndex != -1) {
//                            StringBuilder reportBuilder = new StringBuilder(report);
//                            reportBuilder.insert(lineBreakIndex + 1, imageMarkdown);
//                            report = reportBuilder.toString();
//                        }
//                    } else {
//                        chartsBuilder.append("⚠️ 数据分析图上传失败，保留Mermaid代码\n");
//                        // 降级：插入Mermaid代码
//                        int lineBreakIndex = report.indexOf("\n", dataAnalysisIndex);
//                        if (lineBreakIndex != -1) {
//                            StringBuilder reportBuilder = new StringBuilder(report);
//                            reportBuilder.insert(lineBreakIndex + 1, "\n" + dataAnalysisMermaidCode + "\n\n");
//                            report = reportBuilder.toString();
//                        }
//                    }
//                } catch (Exception e) {
//                    log.error("数据分析图生成失败", e);
//                    chartsBuilder.append("❌ 数据分析图生成失败: ").append(e.getMessage()).append("\n");
//                }
//            }
//
//            // 5. 在"中医指南患者价值观智能报告系统构建与效果评价"章节下插入分析结果评价图
//            String analysisEvaluationSection = "中医指南患者价值观智能报告系统构建与效果评价";
//            int analysisEvaluationIndex = report.indexOf(analysisEvaluationSection);
//            if (analysisEvaluationIndex != -1) {
//                try {
//                    // 生成分析结果评价图的Mermaid代码
//                    String analysisEvaluationMermaidCode = mermaidChartUtils.generateAnalysisEvaluationChart(report);
//
//                    // 转换为静态图片并上传到OSS
//                    String imageUrl = convertMermaidToOssImage(analysisEvaluationMermaidCode, userId, "analysis_evaluation", "png");
//
//                    if (imageUrl != null && !imageUrl.isEmpty()) {
//                        // 生成Markdown图片链接
//                        String imageMarkdown = "\n\n![分析结果评价图](" + imageUrl + ")\n\n<center><strong>图5：分析结果评价图</strong></center>\n\n";
//                        chartsBuilder.append("✅ 分析结果评价图已上传: ").append(imageUrl).append("\n");
//
//                        // 找到章节标题后的第一个换行符位置
//                        int lineBreakIndex = report.indexOf("\n", analysisEvaluationIndex);
//                        if (lineBreakIndex != -1) {
//                            StringBuilder reportBuilder = new StringBuilder(report);
//                            reportBuilder.insert(lineBreakIndex + 1, imageMarkdown);
//                            report = reportBuilder.toString();
//                        }
//                    } else {
//                        chartsBuilder.append("⚠️ 分析结果评价图上传失败，保留Mermaid代码\n");
//                        // 降级：插入Mermaid代码
//                        int lineBreakIndex = report.indexOf("\n", analysisEvaluationIndex);
//                        if (lineBreakIndex != -1) {
//                            StringBuilder reportBuilder = new StringBuilder(report);
//                            reportBuilder.insert(lineBreakIndex + 1, "\n" + analysisEvaluationMermaidCode + "\n\n");
//                            report = reportBuilder.toString();
//                        }
//                    }
//                } catch (Exception e) {
//                    log.error("分析结果评价图生成失败", e);
//                    chartsBuilder.append("❌ 分析结果评价图生成失败: ").append(e.getMessage()).append("\n");
//                }
//            }

//             发送图表生成状态信息
            Response chartsResponse = Response.ResponseContentChatBuilder(chartsBuilder.toString(), "agent");
            callback.onData(chartsResponse);

        } catch (Exception e) {
            log.error("Failed to insert visualizations", e);
            return report;
        }

        return report;
    }

    /**
     * 将Mermaid代码转换为静态图片并上传到OSS
     * 
     * @param mermaidCode Mermaid代码（包含```mermaid标记）
     * @param userId 用户ID
     * @param chartName 图表名称（用于文件命名）
     * @param format 图片格式（png或svg）
     * @return OSS图片链接，失败返回null
     */
    private String convertMermaidToOssImage(String mermaidCode, String userId, String chartName, String format) {
        try {
            log.info("开始转换Mermaid代码为图片 | chartName={} | format={}", chartName, format);

            // 1. 提取纯Mermaid代码（去除Markdown标记和HTML标签）
            String pureMermaidCode = MermaidToImageUtils.extractMermaidCode(mermaidCode);
            
            if (pureMermaidCode == null || pureMermaidCode.isEmpty()) {
                log.error("无法从Markdown中提取Mermaid代码 | chartName={}", chartName);
                return null;
            }
            
            log.debug("提取的纯Mermaid代码长度: {} | chartName={}", pureMermaidCode.length(), chartName);

            // 2. 准备输出目录和文件名
            String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "mermaid_charts";
            String outputFileName = chartName + "_" + System.currentTimeMillis() + "." + format;

            // 3. 使用MermaidToImageUtils将纯Mermaid代码转换为本地图片文件
            File imageFile = MermaidToImageUtils.renderMermaidToImage(pureMermaidCode, tempDir, outputFileName);

            if (imageFile == null || !imageFile.exists()) {
                log.error("Mermaid转图片失败 | chartName={}", chartName);
                return null;
            }

            log.info("Mermaid转图片成功 | localPath={}", imageFile.getAbsolutePath());

            // 4. 上传图片到OSS
            String ossUrl = fileParsingUtils.uploadImage(userId, imageFile, format);

            if (ossUrl == null || ossUrl.isEmpty()) {
                log.error("图片上传OSS失败 | chartName={} | localPath={}", chartName, imageFile.getAbsolutePath());
                return null;
            }

            log.info("图片上传OSS成功 | chartName={} | ossUrl={}", chartName, ossUrl);

            // 5. 删除本地临时文件
            try {
                if (imageFile.exists()) {
                    imageFile.delete();
                    log.info("删除本地临时文件 | path={}", imageFile.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("删除本地临时文件失败 | path={}", imageFile.getAbsolutePath(), e);
            }

            return ossUrl;

        } catch (Exception e) {
            log.error("Mermaid转图片并上传OSS失败 | chartName={}", chartName, e);
            return null;
        }
    }

    // ==================== 提示词构建方法 ====================

    private String formatPreviousReportContentForPrompt(Map<String, Object> params) {
        if (params == null || !params.containsKey("previous_report_content")) {
            return "无";
        }
        Object raw = params.get("previous_report_content");
        if (raw == null) {
            return "无";
        }
        String content = raw.toString().trim();
        if (content.isEmpty()) {
            return "无";
        }

        String outline = extractHeadingOutline(content, 60);
        if (outline.isEmpty()) {
            outline = truncate(content, 20000);
        }

        String result = "（仅供参考：已生成内容提纲，严禁在本次输出中复述/复制）\n" + outline;
        return truncate(result, 60000);
    }

    private String extractHeadingOutline(String content, int maxLines) {
        if (content == null || content.isEmpty() || maxLines <= 0) {
            return "";
        }

        String[] lines = content.split("\\R");
        LinkedHashSet<String> outline = new LinkedHashSet<>();
        for (String line : lines) {
            if (outline.size() >= maxLines) {
                break;
            }
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.matches("(?i)^<h[1-6][^>]*>.*</h[1-6]>\\s*$")
                    || trimmed.matches("^\\d+(?:\\.\\d+)*[、\\.]?\\s*\\S+.*$")
                    || trimmed.matches("^\\(\\d+\\)\\s*\\S+.*$")) {
                outline.add(trimmed);
            }
        }
        return String.join("\n", outline);
    }

    /**
     * 构建摘要章节的提示词
     */
    private String buildAbstractPrompt(String topicName, String cleanJson, String webSearchResults, 
                                       String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        String previousContent = formatPreviousReportContentForPrompt(params);
        
        return languageInstruction + ABSTRACT_PROMPT
                .formatted(topicName, topicName, topicName, truncate(cleanJson, 20000), 
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }
    
    /**
     * 构建研究意义章节的提示词
     */
    private String buildResearchSignificancePrompt(String topicName, String cleanJson, String webSearchResults,
                                                   String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        String previousContent = formatPreviousReportContentForPrompt(params);
        
        return languageInstruction +RESEARCH_SIGNIFICANCE_PROMPT
                .formatted(topicName, topicName, topicName, topicName, truncate(cleanJson, 20000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }
    
    /**
     * 构建国内外研究现状章节的提示词
     */
    private String buildResearchStatusPrompt(String topicName, String cleanJson, String webSearchResults,
                                            String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        String previousContent = formatPreviousReportContentForPrompt(params);
        
        return languageInstruction +RESEARCH_STATUS_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }
    
    /**
     * 构建研究思路章节的提示词
     */
    private String buildResearchIdeaPrompt(String topicName, String cleanJson, String webSearchResults,
                                          String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        String previousContent = formatPreviousReportContentForPrompt(params);
        
        return languageInstruction +RESEARCH_IDEA_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }
    
    /**
     * 构建应用方向章节的提示词
     */
    private String buildApplicationProspectPrompt(String topicName, String cleanJson, String webSearchResults,
                                          String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        String previousContent = formatPreviousReportContentForPrompt(params);
        
        return languageInstruction +APPLICATION_PROSPECT_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }
    
    /**
     * 构建研究内容章节的提示词
     */
    private String buildResearchContentPrompt(String topicName, String cleanJson, String webSearchResults,
                                             String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        String previousContent = formatPreviousReportContentForPrompt(params);
        
        return languageInstruction + RESEARCH_CONTENT_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }
    
    /**
     * 构建研究目标章节的提示词
     */
    private String buildResearchObjectivesPrompt(String topicName, String cleanJson, String webSearchResults,
                                                String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        String previousContent = formatPreviousReportContentForPrompt(params);
        
        return languageInstruction + RESEARCH_OBJECTIVES_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }
    
    /**
     * 构建关键科学问题章节的提示词
     */
    private String buildKeyScientificProblemsPrompt(String topicName, String cleanJson, String webSearchResults,
                                                String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        String previousContent = formatPreviousReportContentForPrompt(params);
        
        return languageInstruction + KEY_SCIENTIFIC_PROBLEMS_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }
    
    /**
     * 构建技术路线章节的提示词
     */
    private String buildTechnicalRoutePrompt(String topicName, String cleanJson, String webSearchResults,
                                          String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        String previousContent = formatPreviousReportContentForPrompt(params);
        
        return languageInstruction +TECHNOLOGY_ROUTES_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }
    
    /**
     * 构建研究方案章节的提示词
     */
    private String buildResearchPlanPrompt(String topicName, String cleanJson, String webSearchResults,
                                          String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);

        String previousContent = formatPreviousReportContentForPrompt(params);

        return languageInstruction + RESEARCH_PLAN_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 25000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }
    
    /**
     * 构建关键技术章节的提示词
     */
    private String buildKeyTechnologiesPrompt(String topicName, String cleanJson, String webSearchResults,
                                          String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        String previousContent = formatPreviousReportContentForPrompt(params);
        
        return languageInstruction +KEY_TECHNOLOGY_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }
    
    /**
     * 构建可行性分析章节的提示词
     */
    private String buildFeasibilityAnalysisPrompt(String topicName, String cleanJson, String webSearchResults,
                                             String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        String previousContent = formatPreviousReportContentForPrompt(params);
        
        return languageInstruction +FEASIBILITY_ANALYSIS_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }
    
    /**
     * 构建思路创新章节的提示词
     */
    private String buildIdeaInnovationPrompt(String topicName, String cleanJson, String webSearchResults,
                                             String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        String previousContent = formatPreviousReportContentForPrompt(params);
        
        return languageInstruction +IDEA_INNOVATION_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }
    
    /**
     * 构建技术创新章节的提示词
     */
    private String buildTechnologyInnovationPrompt(String topicName, String cleanJson, String webSearchResults,
                                             String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        String previousContent = formatPreviousReportContentForPrompt(params);
        
        return languageInstruction +TECHNOLOGY_INNOVATION_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                             previousContent);
    }

    private String buildResearchBasisPrompt(String topicName, String cleanJson, String webSearchResults,
                                           String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);

        String previousContent = formatPreviousReportContentForPrompt(params);

        return languageInstruction + RESEARCH_BASIS_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                        webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                        latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                        previousContent);
    }

    private String buildWorkConditionsPrompt(String topicName, String cleanJson, String webSearchResults,
                                            String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);

        String previousContent = formatPreviousReportContentForPrompt(params);

        return languageInstruction + WORK_CONDITIONS_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                        webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                        latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                        previousContent);
    }

    private String buildResearchPlanAndSchedulePrompt(String topicName, String cleanJson, String webSearchResults,
                                                     String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);

        String previousContent = formatPreviousReportContentForPrompt(params);
        String currentDate = com.faers.agent.pojo.ReportContentPromptConstants.getCurrentDate();

        return languageInstruction + RESEARCH_PLAN_AND_SCHEDULE_PROMPT
                .formatted(topicName, currentDate, currentDate, currentDate, currentDate, currentDate, topicName,
                        truncate(cleanJson, 20000),
                        webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                        latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                        previousContent);
    }

    private String buildExpectedResearchOutcomesPrompt(String topicName, String cleanJson, String webSearchResults,
                                                      String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);

        String previousContent = formatPreviousReportContentForPrompt(params);

        return languageInstruction + EXPECTED_RESEARCH_OUTCOMES_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                        webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                        latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                        previousContent);
    }

    private String buildRiskAnalysisAndCountermeasuresPrompt(String topicName, String cleanJson, String webSearchResults,
                                                            String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);

        String previousContent = formatPreviousReportContentForPrompt(params);

        return languageInstruction + RISK_ANALYSIS_AND_COUNTERMEASURES_PROMPT
                .formatted(topicName, topicName, truncate(cleanJson, 20000),
                        webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                        latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000),
                        previousContent);
    }
    
    /**
     * 构建文献列表章节的提示词
     */
    private String buildReferencesPrompt(String topicName, String cleanJson, String webSearchResults,
                                        String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);
        
        return languageInstruction + """
                【角色与任务】
                您是医学科研项目管理与评审专家，精通国家自然科学基金等申报。
                
                您的任务：生成项目申报书的"文献列表"部分。
                
                【生成规则】
                
                文献列表
                （请按照GB/T 7714规范，生成20篇权威参考文献列表，每篇以带方括号的编号（如[1]）起始并独占一行。）
                全文统一核对文献引用：作者、年份、期刊、文中标注顺序（中英文混排时尤其容易出错）。对于方法学关键文献（如GRADE、WHO手册、中医指南规范），建议在全文中对应标注，体现"不是凭印象写的"。
                
                【输入数据】
                项目名称：%s
                ES文献数据：%s
                网页搜索结果：%s
                上一版项目申报书：%s
                
                【输出要求】
                请直接生成上述内容，保持专业学术风格，确保格式规范。
                采用ES文献数据以及网页搜索文献,不得虚造
                每篇文献以带方括号的编号（如[1]）起始并独占一行。
                """.formatted(topicName, truncate(cleanJson, 30000),
                             webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                             latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000));
    }
    
    /**
     * 获取输出语言参数
     */
    private String getOutputLanguage(Map<String, Object> params) {
        String outputLanguage = getValue(params, "outputLanguage");
        if (outputLanguage == null || outputLanguage.trim().isEmpty()) {
            outputLanguage = "中文";
        }
        return outputLanguage;
    }
    
    /**
     * 根据输出语言设置语言指令
     */
    private String getLanguageInstruction(String outputLanguage) {
        if ("英文".equals(outputLanguage) || "English".equalsIgnoreCase(outputLanguage)) {
            return """
                    
                    【排版格式强制规范、语言输出要求】（必须严格遵守）：
                    请使用英文撰写整份内容，包括：
                    - 所有章节标题、小节标题
                    - 所有正文内容、段落描述
                    - 表格的标题和内容
                    - 摘要和关键词
                    
                    注意：
                    1. 文献引用中的原始文献标题和期刊名称保持原文（中文文献保留中文，英文文献保留英文）
                    2. 专有名词的翻译要准确、规范
                    3. 使用学术规范的英文表达，确保语法正确、表达地道
                    4. 保持专业的学术写作风格
                    5. 除了中文关键词外，所有内容全部使用英文撰写，包括标题
                    
                    """;
        } else {
            return """
                    
                    【排版格式强制规范、语言输出要求】（必须严格遵守）：
                    请使用中文撰写整份内容。
                    
                    注意：
                    1. 文献引用中的原始文献标题和期刊名称保持原文（中文文献保留中文，英文文献保留英文）
                    2. 使用规范的中文学术表达
                    3. 保持专业的学术写作风格
                    
                    """;
        }
    }

    // ==================== 背景分析相关提示词构建方法 ====================

    /**
     * 构建研究背景分析的提示词
     */
    private String buildResearchBackgroundPrompt(String topicName, String cleanJson, String webSearchResults,
                                                 String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);

        return languageInstruction + """
                【角色与任务】
                您是医学科研项目管理与评审专家，精通国家自然科学基金等申报。
                
                您的任务：分析项目"%s"的研究背景，为项目申报提供背景支撑。
                
                【分析要求】
                1. 研究领域概述（150-200字）
                   - 简要介绍项目所属的研究领域和学科背景
                   - 说明该领域的重要性和研究价值
                   - 基于ES文献数据和网页搜索结果进行综述
                   
                【输入数据】
                项目名称：%s
                ES文献数据：%s
                网页搜索结果：%s
                上一版项目申报书：%s
                
                【输出要求】
                - 使用专业学术语言，逻辑清晰
                - 内容要与项目名称和研究方向紧密相关
                - 总字数控制在200字内
                - 直接输出分析内容
                - 每段话的首行请使用HTML标签 &emsp;&emsp; 进行缩进
                """.formatted(topicName, topicName, truncate(cleanJson, 20000),
                webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000));
    }

    /**
     * 构建立项证据综述分析的提示词
     */
    private String buildEvidenceRetrievalPrompt(String topicName, String cleanJson, String webSearchResults,
                                                String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);

        return languageInstruction + """
                【角色与任务】
                您是医学科研项目管理与评审专家，精通国家自然科学基金等申报。
                
                您的任务：基于已检索的文献和数据，为项目"%s"提供相关证据分析。
                
                【分析要求】
                1. 证据来源说明（50-100字）
                   - 说明已检索的数据库和网页资源
                   - 简要介绍检索到的文献类型和数量
                   - 评价证据来源的权威性和可靠性
                
                2. 关键证据总结（50-100字）
                   - 总结支持项目立项的核心证据（3-5条）
                   - 每条证据要明确说明来源文献
                   - 强调证据对项目研究的支撑作用
                
                【输入数据】
                项目名称：%s
                ES文献数据（已检索）：%s
                网页搜索结果（已检索）：%s
                上一版项目申报书：%s
                
                【输出要求】
                - 紧密结合已检索的ES文献数据和网页搜索结果
                - 证据要真实可靠，可追溯
                - 总字数控制在200字内
                - 直接输出分析内容
                - 每段话的首行请使用HTML标签 &emsp;&emsp; 进行缩进
                """.formatted(topicName, topicName, truncate(cleanJson, 25000),
                webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000));
    }

    /**
     * 构建文献综述的提示词
     */
    private String buildLiteratureReviewPrompt(String topicName, String cleanJson, String webSearchResults,
                                               String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);

        return languageInstruction + """
                【角色与任务】
                您是医学科研项目管理与评审专家，精通国家自然科学基金等申报。
                
                您的任务：针对项目"%s"进行系统的文献综述。
                
                【综述要求】
                
                1. 研究空白与机遇（150字）
                   - 基于文献分析，明确指出研究空白
                   - 说明本项目可以填补的空白
                   - 强调研究的创新性和前瞻性
                
                【输入数据】
                项目名称：%s
                ES文献数据：%s
                网页搜索结果：%s
                上一版项目申报书：%s
                
                【输出要求】
                - 综述要全面、客观、有深度
                - 直接输出综述内容
                - 总字数控制在150字内
                - 每段话的首行请使用HTML标签 &emsp;&emsp; 进行缩进
                """.formatted(topicName, topicName, truncate(cleanJson, 25000),
                webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000));
    }

    /**
     * 构建领域发展趋势分析的提示词
     */
    private String buildFieldTrendsPrompt(String topicName, String cleanJson, String webSearchResults,
                                          String latestPaper, Map<String, Object> params) {
        String outputLanguage = getOutputLanguage(params);
        String languageInstruction = getLanguageInstruction(outputLanguage);

        return languageInstruction + """
                【角色与任务】
                您是医学科研项目管理与评审专家，精通国家自然科学基金等申报。
                
                您的任务：分析项目"%s"所在领域的发展趋势，为项目定位提供战略支撑。
                
                【分析要求】
                1. 技术发展趋势（100字）
                   - 分析该领域的关键技术发展方向
                   - 介绍新兴技术和方法的应用前景
                   - 说明技术进步对研究的推动作用
                
                2. 研究热点与前沿（100字）
                   - 识别当前的研究热点问题
                   - 分析研究前沿的演进趋势
                   - 预测未来3-5年的研究重点
               
                【输入数据】
                项目名称：%s
                ES文献数据：%s
                网页搜索结果：%s
                上一版项目申报书：%s
                
                【输出要求】
                - 分析要有前瞻性和战略高度
                - 准确把握领域发展脉络和趋势
                - 直接输出分析内容
                - 总字数控制在200字内
                - 每段话的首行请使用HTML标签 &emsp;&emsp; 进行缩进
                """.formatted(topicName, topicName, truncate(cleanJson, 25000),
                webSearchResults.isEmpty() ? "无" : truncate(webSearchResults, 5000),
                latestPaper.isEmpty() ? "无" : truncate(latestPaper, 5000));
    }

    /**
     * 验证最终报告的质量
     * 检查报告中是否包含不应该出现的内容（JSON标记、summary字段等）
     * 
     * @param report 最终报告内容
     * @return 验证结果，如果发现问题会记录警告日志
     */
    public boolean validateFinalReport(String report) {
        if (report == null || report.isEmpty()) {
            log.warn("REPORT_VALIDATION | Report is null or empty");
            return false;
        }
        
        boolean isValid = true;
        
        // 检查是否包含JSON标记
        if (report.contains("```json") || report.contains("\"report_content\"") || report.contains("\"summary\"")) {
            log.warn("REPORT_VALIDATION | Report contains JSON markers or field names");
            isValid = false;
        }
        
        // 检查是否包含未清理的转义字符
        if (report.contains("\\n") || report.contains("\\t") || report.contains("\\\"")) {
            log.warn("REPORT_VALIDATION | Report contains unescaped characters");
            isValid = false;
        }
        
        // 检查是否包含过多的连续空行（超过3行）
        if (report.contains("\n\n\n\n")) {
            log.warn("REPORT_VALIDATION | Report contains excessive blank lines");
            isValid = false;
        }
        
        if (isValid) {
            log.info("REPORT_VALIDATION | Report passed validation | length={}", report.length());
        } else {
            log.warn("REPORT_VALIDATION | Report failed validation | length={}", report.length());
        }
        
        return isValid;
    }
    
    /**
     * 清理报告内容，去除多余符号和格式问题
     * 确保生成干净的Markdown格式报告
     *
     * 该方法可以在其他类中调用，用于清理大模型生成的原始报告内容
     *
     * @param report 原始报告内容（通常是大模型生成的原始报告）
     * @return 清理后的报告内容
     */
    public String cleanReportContent(String report) {
        if (report == null || report.isEmpty()) {
            return report;
        }

        // 去除开头的多余空白和符号
        String cleaned = report.trim();

        // 去除可能存在的JSON转义字符
        cleaned = cleaned.replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\r", "\r");

        // 确保Markdown格式正确：统一换行符为\n
        cleaned = cleaned.replace("\r\n", "\n")
                .replace("\r", "\n");

        // 去除多余的空行（保留最多两个连续空行，用于章节分隔）
        cleaned = cleaned.replaceAll("\n{4,}", "\n\n\n");

        // 去除行尾多余空格和制表符
        String[] lines = cleaned.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.replaceAll("[ \t]+$", "")).append("\n");
        }
        cleaned = sb.toString();

        // 去除开头和结尾的空白
        cleaned = cleaned.trim();

        // 确保以换行符结尾（如果内容不为空）
        if (!cleaned.isEmpty() && !cleaned.endsWith("\n")) {
            cleaned += "\n";
        }

        return cleaned;
    }


    @Override
    public void process(AnalysisContext context, String id, String parentId, String userId, StreamDataCallback streamDataCallback) {

    }
}
