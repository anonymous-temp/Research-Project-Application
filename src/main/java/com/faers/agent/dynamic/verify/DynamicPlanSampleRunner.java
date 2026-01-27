package com.faers.agent.dynamic.verify;

import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.dynamic.agent.DynamicProgressAgent;
import com.faers.agent.pojo.StreamDataCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class DynamicPlanSampleRunner {
    @Autowired
    private DynamicProgressAgent dynamicProgressAgent;

    public void runSample(String topic) throws ExecutionException, InterruptedException {
        AnalysisContext context = new AnalysisContext();
        Map<String, Object> qp = new HashMap<>();
        qp.put("topicName", topic);
        context.setQueryParams(qp);
        context.setTraceId("trace_" + System.currentTimeMillis());
        StreamDataCallback callback = new StreamDataCallback() {
            @Override
            public void onData(Response response) { }
            @Override
            public void onData(String chunk) { }
        };
        dynamicProgressAgent.process(context, UUID.randomUUID().toString(), "user", "parent", callback);
    }
}

