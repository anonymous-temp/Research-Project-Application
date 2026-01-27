package com.faers.agent.agent;

import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.pojo.StreamDataCallback;

public interface Agent {
    void process(AnalysisContext context, String id,
                 String parentId,
                 String userId,
                 StreamDataCallback streamDataCallback);
}
