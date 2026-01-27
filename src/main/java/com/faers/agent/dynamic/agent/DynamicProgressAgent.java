package com.faers.agent.dynamic.agent;

import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.dynamic.assembler.PlanAssembler;
import com.faers.agent.dynamic.execution.DynamicExecutionEngine;
import com.faers.agent.dynamic.model.PlanDocument;
import com.faers.agent.dynamic.service.DynamicPlanService;
import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.pojo.StreamDataCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class DynamicProgressAgent {
    private static final Logger log = LoggerFactory.getLogger(DynamicProgressAgent.class);

    @Autowired
    private DynamicPlanService dynamicPlanService;
    @Autowired
    private DynamicExecutionEngine executionEngine;
    @Autowired
    private PlanAssembler planAssembler;

    public void process(AnalysisContext context, String id, String userId, String parentId, StreamDataCallback callback) throws ExecutionException, InterruptedException {
        String traceId = context.getTraceId();
        log.info("[动态进度代理] 开始处理动态执行流程 | traceId={} | userId={}", traceId, userId);

        String topicName = getTopicNameFromContext(context);
        log.info("[动态进度代理] 从上下文中提取主题 | traceId={} | topicName={}", traceId, topicName);

        log.info("[动态进度代理] 开始生成动态计划 | traceId={}", traceId);
        PlanDocument plan = dynamicPlanService.createPlan(topicName, null, callback, traceId);
        log.info("[动态进度代理] 计划生成成功 | traceId={} | planId={} | 包含 {} 个步骤",
                traceId, plan.getPlanId(), plan.getSteps().size());

        Response overview = planAssembler.toPlannerOverview(plan);
        callback.onData(overview);
        log.info("[动态进度代理] 已向客户端推送计划概览 | traceId={}", traceId);

        try {
            log.info("[动态进度代理] 开始执行计划 | traceId={} | planId={}", traceId, plan.getPlanId());
            executionEngine.execute(plan, context, callback, traceId);

            log.info("[动态进度代理] 计划执行成功 | traceId={} | planId={}", traceId, plan.getPlanId());
            dynamicPlanService.updateStatus(plan.getPlanId(), "succeeded");

            String callId = UUID.randomUUID().toString();
            Response done = Response.flowResultBuilder("动态流程执行完成", callId);
            callback.onData(done);
            log.info("[动态进度代理] 已向客户端推送执行完成通知 | traceId={}", traceId);
        } catch (Exception e) {
            log.error("[动态进度代理] 计划执行失败 | traceId={} | planId={} | error={}",
                    traceId, plan.getPlanId(), e.getMessage(), e);

            dynamicPlanService.updateStatus(plan.getPlanId(), "failed");
            String callId = UUID.randomUUID().toString();
            Response fail = Response.flowResultBuilder("动态流程执行失败: " + e.getMessage(), callId);
            callback.onData(fail);
            log.info("[动态进度代理] 已向客户端推送执行失败通知 | traceId={}", traceId);
        }
    }

    private String getTopicNameFromContext(AnalysisContext context) {
        Object topic = context.getQueryParams().get("topicName");
        return topic != null ? topic.toString() : "默认主题";
    }
}
