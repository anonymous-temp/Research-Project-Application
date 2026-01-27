package com.faers.agent.dynamic.planner;

import com.faers.agent.dynamic.model.PlanDocument;
import com.faers.agent.dynamic.model.StepDocument;
import com.faers.agent.dynamic.model.SubstepDocument;
import com.faers.agent.service.LLMInvocationService;
import com.faers.agent.pojo.StreamDataCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class AiPlannerClient {
    private static final Logger log = LoggerFactory.getLogger(AiPlannerClient.class);
    
    @Autowired
    private LLMInvocationService llmInvocationService;
    @Autowired
    private ObjectMapper objectMapper;

    public PlanDocument generatePlan(String topic, int seed, StreamDataCallback callback, String traceId) {
        log.info("[AI计划生成器] 开始生成动态计划 | traceId={} | topic={} | seed={}", traceId, topic, seed);
        
        String prompt = buildPrompt(topic, seed);
        log.info("[AI计划生成器] 构建提示词完成 | traceId={}", traceId);
        
        log.info("[AI计划生成器] 调用大模型生成计划 | traceId={}", traceId);
        String content = llmInvocationService.invokeStream(prompt, callback, traceId);
        log.info("[AI计划生成器] 大模型响应获取完成 | traceId={} | 响应长度={}", traceId, content.length());
        
        PlanDocument plan = tryParse(content);
        if (plan == null) {
            log.warn("[AI计划生成器] 解析大模型响应失败，使用 fallback 计划 | traceId={}", traceId);
            plan = fallbackPlan(topic, seed);
        } else {
            log.info("[AI计划生成器] 成功解析大模型响应 | traceId={} | 生成 {} 个步骤", traceId, plan.getSteps().size());
        }
        
        plan.setPlanId("plan_" + System.currentTimeMillis());
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        plan.setVersion(1);
        plan.setStatus("pending");
        
        log.info("[AI计划生成器] 计划生成完成 | traceId={} | planId={} | 最终包含 {} 个步骤", 
                traceId, plan.getPlanId(), plan.getSteps().size());
        
        // 打印完整计划结构
        log.debug("[AI计划生成器] 完整计划结构 | traceId={} | plan={}", traceId, plan);
        
        return plan;
    }

    private String buildPrompt(String topic, int seed) {
        return "请根据搜索目标生成项目申报书的动态执行计划，采用JSON输出。" +
               "要求包含若干步骤，每步包含若干子步骤。" +
               "固定写作步骤必须包含编号4.1到4.14，对应申报书章节写作，并置于中段。" +
               "其它步骤可根据主题动态生成，需与搜索目标紧密相关。" +
               "字段包括: steps[].title, steps[].dependsOn, steps[].substeps[].action, inputs, successCriteria, artifacts。" +
               "topic: " + topic + " seed: " + seed;
    }

    private PlanDocument tryParse(String content) {
        try {
            log.debug("[AI计划生成器] 开始解析计划内容 | content={}", content);
            
            Map<?, ?> root = objectMapper.readValue(content, Map.class);
            Object stepsObj = root.get("steps");
            if (!(stepsObj instanceof List)) {
                log.error("[AI计划生成器] 解析失败：steps 不是数组类型 | stepsObj={}", stepsObj);
                return null;
            }
            
            List<?> stepsList = (List<?>) stepsObj;
            List<StepDocument> steps = new ArrayList<>();
            int idx = 1;
            
            for (Object s : stepsList) {
                if (!(s instanceof Map)) {
                    log.warn("[AI计划生成器] 跳过非Map类型的步骤 | stepObj={}", s);
                    continue;
                }
                
                Map<?, ?> sm = (Map<?, ?>) s;
                StepDocument step = new StepDocument();
                step.setStepId("S" + idx++);
                step.setTitle(String.valueOf(sm.get("title")));
                step.setDescription(String.valueOf(sm.get("description")));
                
                Object deps = sm.get("dependsOn");
                if (deps instanceof List) {
                    List<String> d = new ArrayList<>();
                    for (Object o : (List<?>) deps) {
                        if (o instanceof Number) {
                            int n = ((Number) o).intValue();
                            d.add("S" + (n + 1));
                        } else {
                            String v = String.valueOf(o);
                            if (v.matches("\\d+")) {
                                d.add("S" + (Integer.parseInt(v) + 1));
                            } else {
                                d.add(v);
                            }
                        }
                    }
                    step.setDependsOn(d);
                } else {
                    step.setDependsOn(Collections.emptyList());
                }
                
                List<SubstepDocument> substeps = new ArrayList<>();
                Object subs = sm.get("substeps");
                if (subs instanceof List) {
                    int j = 1;
                    for (Object sub : (List<?>) subs) {
                        if (!(sub instanceof Map)) {
                            log.warn("[AI计划生成器] 跳过非Map类型的子步骤 | substepObj={}", sub);
                            continue;
                        }
                        
                        Map<?, ?> subm = (Map<?, ?>) sub;
                        SubstepDocument ss = new SubstepDocument();
                        ss.setSubstepId(step.getStepId() + "-" + j++);
                        String originalAction = String.valueOf(subm.get("action"));
                        String canonical = canonicalizeAction(originalAction, subm);
                        ss.setAction(canonical);
                        
                        Object inputs = subm.get("inputs");
                        if (inputs instanceof Map) {
                            Map<String, Object> in = new HashMap<>();
                            for (Map.Entry<?, ?> e : ((Map<?, ?>) inputs).entrySet()) {
                                in.put(String.valueOf(e.getKey()), e.getValue());
                            }
                            ss.setInputs(in);
                        } else {
                            if ("section_write_legacy".equals(canonical)) {
                                Map<String, Object> in = new HashMap<>();
                                in.put("sectionCode", extractSectionCode(subm, originalAction));
                                ss.setInputs(in);
                            } else if ("outline_generate".equals(canonical)) {
                                Map<String, Object> in = new HashMap<>();
                                Object topicObj = root.get("topic");
                                in.put("topic", topicObj != null ? String.valueOf(topicObj) : "");
                                ss.setInputs(in);
                            } else if ("web_search".equals(canonical)) {
                                Map<String, Object> in = new HashMap<>();
                                Object topicObj = root.get("topic");
                                in.put("query", topicObj != null ? String.valueOf(topicObj) : "");
                                ss.setInputs(in);
                            } else if ("llm_task".equals(canonical)) {
                                Map<String, Object> in = new HashMap<>();
                                in.put("taskDescription", originalAction);
                                ss.setInputs(in);
                            }
                        }
                        
                        Object sc = subm.get("successCriteria");
                        if (sc instanceof List) {
                            List<String> c = new ArrayList<>();
                            for (Object o : (List<?>) sc) c.add(String.valueOf(o));
                            ss.setSuccessCriteria(c);
                        }
                        
                        Object arts = subm.get("artifacts");
                        if (arts instanceof List) {
                            List<String> a = new ArrayList<>();
                            for (Object o : (List<?>) arts) a.add(String.valueOf(o));
                            ss.setArtifacts(a);
                        }
                        
                        ss.setStatus("pending");
                        substeps.add(ss);
                    }
                }
                
                step.setSubsteps(substeps);
                step.setStatus("pending");
                steps.add(step);
            }
            
            PlanDocument plan = new PlanDocument();
            plan.setSteps(steps);
            plan.setTopic(String.valueOf(root.get("topic")));
            plan.setGoal(String.valueOf(root.get("goal")));
            
            Object seedObj = root.get("seed");
            if (seedObj instanceof Number) {
                plan.setSeed(((Number) seedObj).intValue());
            }
            
            return plan;
        } catch (Exception e) {
            log.error("[AI计划生成器] 解析计划内容失败 | error={}", e.getMessage(), e);
            return null;
        }
    }

    private String canonicalizeAction(String actionText, Map<?, ?> subm) {
        if (actionText == null) actionText = "";
        String at = actionText.trim();
        String sec = extractSectionCode(subm, at);
        if (sec != null) return "section_write_legacy";
        if (at.contains("大纲") || at.contains("结构")) return "outline_generate";
        if (at.contains("网页") || at.contains("搜索") || at.contains("调研")) return "web_search";
        if (at.contains("合并") || at.contains("定稿") || at.contains("导出")) return "merge_document";
        return "llm_task";
    }

    private String extractSectionCode(Map<?, ?> subm, String actionText) {
        Object arts = subm.get("artifacts");
        if (arts instanceof List) {
            for (Object o : (List<?>) arts) {
                String s = String.valueOf(o);
                if (s.matches("^4\\.\\d+_.*")) {
                    String code = s.substring(0, s.indexOf("_"));
                    return code;
                }
            }
        }
        if (actionText != null) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(4\\.\\d+)").matcher(actionText);
            if (m.find()) return m.group(1);
            Map<String, String> map = new HashMap<>();
            map.put("立项依据", "4.1");
            map.put("研究目标", "4.2");
            map.put("研究内容", "4.3");
            map.put("技术路线", "4.9");
            map.put("研究方案", "4.10");
            map.put("关键技术", "4.11");
            map.put("可行性", "4.12");
            map.put("创新", "4.13");
            for (Map.Entry<String, String> e : map.entrySet()) {
                if (actionText.contains(e.getKey())) return e.getValue();
            }
        }
        return null;
    }
    private PlanDocument fallbackPlan(String topic, int seed) {
        log.info("[AI计划生成器] 生成 fallback 计划 | traceId={} | topic={}", "", topic);
        
        PlanDocument plan = new PlanDocument();
        plan.setTopic(topic);
        plan.setGoal("生成完整项目申报书");
        plan.setSeed(seed);
        
        List<StepDocument> steps = new ArrayList<>();
        
        // 步骤1：背景调研与大纲生成
        StepDocument pre = new StepDocument();
        pre.setStepId("S1");
        pre.setTitle("背景调研与大纲生成");
        pre.setDependsOn(Collections.emptyList());
        
        SubstepDocument s11 = new SubstepDocument();
        s11.setSubstepId("S1-1");
        s11.setAction("web_search");
        s11.setInputs(Map.of("query", topic));
        s11.setSuccessCriteria(List.of("找到5个以上权威来源"));
        s11.setArtifacts(List.of("artifacts/%plan%/S1/S1-1/sources.json"));
        
        SubstepDocument s12 = new SubstepDocument();
        s12.setSubstepId("S1-2");
        s12.setAction("outline_generate");
        s12.setInputs(Map.of("topic", topic));
        s12.setSuccessCriteria(List.of("生成申报书草案"));
        s12.setArtifacts(List.of("artifacts/%plan%/S1/S1-2/outline.md"));
        
        pre.setSubsteps(List.of(s11, s12));
        pre.setStatus("pending");
        steps.add(pre);
        log.info("[AI计划生成器] fallback 计划添加步骤：背景调研与大纲生成");
        
        // 步骤2：固定写作步骤
        StepDocument fixed = new StepDocument();
        fixed.setStepId("S2");
        fixed.setTitle("固定写作步骤");
        fixed.setDependsOn(List.of("S1"));
        
        List<SubstepDocument> subs = new ArrayList<>();
        for (int i = 1; i <= 14; i++) {
            SubstepDocument ss = new SubstepDocument();
            ss.setSubstepId("S2-" + i);
            ss.setAction("section_write_legacy");
            ss.setInputs(Map.of("sectionCode", "4." + i));
            ss.setSuccessCriteria(List.of("生成章节内容"));
            ss.setArtifacts(List.of("artifacts/%plan%/S2/S2-" + i + "/section.md"));
            subs.add(ss);
        }
        
        fixed.setSubsteps(subs);
        fixed.setStatus("pending");
        steps.add(fixed);
        log.info("[AI计划生成器] fallback 计划添加步骤：固定写作步骤（包含14个子步骤）");
        
        // 步骤3：合并与导出
        StepDocument post = new StepDocument();
        post.setStepId("S3");
        post.setTitle("合并与导出");
        post.setDependsOn(List.of("S2"));
        
        SubstepDocument s31 = new SubstepDocument();
        s31.setSubstepId("S3-1");
        s31.setAction("merge_document");
        s31.setInputs(Map.of("format", "md"));
        s31.setSuccessCriteria(List.of("生成最终申报书"));
        s31.setArtifacts(List.of("artifacts/%plan%/S3/S3-1/proposal.md"));
        
        post.setSubsteps(List.of(s31));
        post.setStatus("pending");
        steps.add(post);
        log.info("[AI计划生成器] fallback 计划添加步骤：合并与导出");
        
        plan.setSteps(steps);
        log.info("[AI计划生成器] fallback 计划生成完成 | 包含 {} 个步骤", plan.getSteps().size());
        
        return plan;
    }
}
