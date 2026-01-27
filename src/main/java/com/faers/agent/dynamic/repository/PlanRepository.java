package com.faers.agent.dynamic.repository;

import com.faers.agent.dynamic.model.PlanDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PlanRepository extends MongoRepository<PlanDocument, String> {
    Optional<PlanDocument> findByPlanId(String planId);
    List<PlanDocument> findByTopic(String topic);
    List<PlanDocument> findByStatus(String status);
    List<PlanDocument> findByUpdatedAtAfter(LocalDateTime time);
}

