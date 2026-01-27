package com.faers.agent.dynamic.actions;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ActionRegistry {
    private final Map<String, Action> registry = new HashMap<>();

    public void register(String name, Action action) {
        registry.put(name, action);
    }

    public Action get(String name) {
        return registry.get(name);
    }
}

