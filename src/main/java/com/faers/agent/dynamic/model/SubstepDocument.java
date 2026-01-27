package com.faers.agent.dynamic.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SubstepDocument {
    private String substepId;
    private String action;
    private Map<String, Object> inputs;
    private List<String> successCriteria;
    private Map<String, Object> outputs;
    private List<String> artifacts;
    private String evidence;
    private String status;
    private String execLogs;
}

