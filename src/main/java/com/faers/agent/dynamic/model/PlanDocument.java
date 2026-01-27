package com.faers.agent.dynamic.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "dynamic_plans")
public class PlanDocument {
    @Id
    private String planId;
    private String topic;
    private String goal;
    private Integer seed;
    private String status;
    private List<StepDocument> steps;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
}

