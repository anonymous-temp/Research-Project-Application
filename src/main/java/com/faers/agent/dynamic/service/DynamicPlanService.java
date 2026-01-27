package com.faers.agent.dynamic.service;

import com.faers.agent.dynamic.model.PlanDocument;
import com.faers.agent.dynamic.planner.AiPlannerClient;
import com.faers.agent.dynamic.repository.PlanRepository;
import com.faers.agent.pojo.StreamDataCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DynamicPlanService {
    @Autowired
    private AiPlannerClient aiPlannerClient;
    @Autowired
    private PlanRepository planRepository;

    public PlanDocument createPlan(String topic, Integer seed, StreamDataCallback callback, String traceId) {
        int s = seed != null ? seed : (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
        PlanDocument plan = aiPlannerClient.generatePlan(topic, s, callback, traceId);
        if (plan.getPlanId() == null || plan.getPlanId().isEmpty()) {
            plan.setPlanId("plan_" + UUID.randomUUID());
        }
        planRepository.save(plan);
        return plan;
    }

    public Optional<PlanDocument> getPlan(String planId) {
        return planRepository.findByPlanId(planId);
    }

    public List<PlanDocument> listPlansByTopic(String topic) {
        return planRepository.findByTopic(topic);
    }

    public PlanDocument updateStatus(String planId, String status) {
        Optional<PlanDocument> opt = planRepository.findByPlanId(planId);
        if (opt.isEmpty()) return null;
        PlanDocument plan = opt.get();
        plan.setStatus(status);
        plan.setUpdatedAt(LocalDateTime.now());
        planRepository.save(plan);
        return plan;
    }
}

