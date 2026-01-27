package com.faers.agent.dynamic.model;

import lombok.Data;

import java.util.List;

@Data
public class StepDocument {
    private String stepId;
    private String title;
    private String description;
    private List<String> dependsOn;
    private List<SubstepDocument> substeps;
    private String status;
}

