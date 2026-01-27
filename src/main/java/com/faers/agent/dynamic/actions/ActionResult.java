package com.faers.agent.dynamic.actions;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ActionResult {
    private String status;
    private Map<String, Object> outputs;
    private List<String> artifacts;
    private String logs;
}

