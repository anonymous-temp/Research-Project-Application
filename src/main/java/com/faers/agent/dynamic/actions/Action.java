package com.faers.agent.dynamic.actions;

import com.faers.agent.dynamic.model.SubstepDocument;
import com.faers.agent.pojo.StreamDataCallback;

import java.util.Map;

public interface Action {
    ActionResult execute(SubstepDocument substep, Map<String, Object> ctx, StreamDataCallback callback, String traceId) throws Exception;
}

