package com.faers.agent.dynamic.execution;

import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.dynamic.actions.Action;
import com.faers.agent.dynamic.actions.ActionRegistry;
import com.faers.agent.dynamic.actions.ActionResult;
import com.faers.agent.dynamic.model.PlanDocument;
import com.faers.agent.dynamic.model.StepDocument;
import com.faers.agent.dynamic.model.SubstepDocument;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.dto.responseDto.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class DynamicExecutionEngine {
    private static final Logger log = LoggerFactory.getLogger(DynamicExecutionEngine.class);
    
    @Autowired
    private ActionRegistry actionRegistry;

    public void execute(PlanDocument plan, AnalysisContext context, StreamDataCallback callback, String traceId) throws Exception {
        log.info("[动态执行引擎] 开始执行计划 | traceId={} | planId={} | topic={}", traceId, plan.getPlanId(), plan.getTopic());
        log.info("[动态执行引擎] 计划包含 {} 个步骤", plan.getSteps().size());
        
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("planId", plan.getPlanId());
        ctx.put("context", context);
        Set<String> done = new HashSet<>();
        List<StepDocument> steps = plan.getSteps();
        
        int stepIndex = 1;
        for (StepDocument step : steps) {
            log.info("[动态执行引擎] 检查步骤依赖 | traceId={} | stepId={} | stepTitle={} | dependsOn={}", 
                    traceId, step.getStepId(), step.getTitle(), step.getDependsOn());
            
            if (!dependenciesSatisfied(step.getDependsOn(), done)) {
                log.info("[动态执行引擎] 步骤依赖未满足，跳过执行 | traceId={} | stepId={}", traceId, step.getStepId());
                continue;
            }
            
            log.info("[动态执行引擎] 开始执行步骤 {}/{} | traceId={} | stepId={} | stepTitle={}", 
                    stepIndex++, steps.size(), traceId, step.getStepId(), step.getTitle());
            
            ctx.put("stepId", step.getStepId());
            if (callback != null) {
                String callId = java.util.UUID.randomUUID().toString();
                Response statusResponse = Response.flowBuilder(step.getStepId() + " " + step.getTitle(), "开始执行步骤", callId, "");
                callback.onData(statusResponse);
            }
            
            int substepIndex = 1;
            for (SubstepDocument sub : step.getSubsteps()) {
                log.info("[动态执行引擎] 开始执行子步骤 {}/{} | traceId={} | substepId={} | action={} | inputs={}", 
                        substepIndex++, step.getSubsteps().size(), traceId, sub.getSubstepId(), sub.getAction(), sub.getInputs());
                
                Action action = actionRegistry.get(sub.getAction());
                if (action == null) {
                    log.warn("[动态执行引擎] 未找到动作实现，使用通用LLM任务 | traceId={} | action={}", traceId, sub.getAction());
                    action = actionRegistry.get("llm_task");
                }
                if (callback != null) {
                    String callId = java.util.UUID.randomUUID().toString();
                    Response statusResponse = Response.flowBuilder(sub.getSubstepId() + " " + sub.getAction(), "正在执行子步骤", callId, "");
                    callback.onData(statusResponse);
                }
                
                ActionResult r = action.execute(sub, ctx, callback, traceId);
                
                log.info("[动态执行引擎] 子步骤执行完成 | traceId={} | substepId={} | status={} | outputs={} | artifacts={}", 
                        traceId, sub.getSubstepId(), r.getStatus(), r.getOutputs(), r.getArtifacts());
                
                sub.setStatus(r.getStatus());
                sub.setOutputs(r.getOutputs());
                sub.setArtifacts(r.getArtifacts());
                if (callback != null) {
                    String callId = java.util.UUID.randomUUID().toString();
                    Response statusResponse = Response.flowResultBuilder("已完成子步骤：" + sub.getSubstepId(), callId);
                    callback.onData(statusResponse);
                }
            }
            
            step.setStatus("succeeded");
            done.add(step.getStepId());
            
            log.info("[动态执行引擎] 步骤执行完成 | traceId={} | stepId={} | stepTitle={}", 
                    traceId, step.getStepId(), step.getTitle());
            if (callback != null) {
                String callId = java.util.UUID.randomUUID().toString();
                Response statusResponse = Response.flowResultBuilder("已完成步骤：" + step.getStepId(), callId);
                callback.onData(statusResponse);
            }
        }
        
        plan.setStatus("succeeded");
        plan.setUpdatedAt(LocalDateTime.now());
        
        log.info("[动态执行引擎] 计划执行完成 | traceId={} | planId={} | topic={}", 
                traceId, plan.getPlanId(), plan.getTopic());
    }

    private boolean dependenciesSatisfied(List<String> deps, Set<String> done) {
        if (deps == null || deps.isEmpty()) return true;
        for (String d : deps) {
            if (!done.contains(d)) return false;
        }
        return true;
    }
}
