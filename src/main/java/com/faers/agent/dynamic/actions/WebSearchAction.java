package com.faers.agent.dynamic.actions;

import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.tools.FallbackWebSearchTool;
import com.faers.agent.dynamic.model.SubstepDocument;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("web_search")
public class WebSearchAction implements Action {
    @Override
    public ActionResult execute(SubstepDocument substep, Map<String, Object> ctx, StreamDataCallback callback, String traceId) throws Exception {
        String query = String.valueOf(substep.getInputs().get("query"));
        FallbackWebSearchTool tool = new FallbackWebSearchTool();
        List<cn.hutool.json.JSONObject> results = tool.searchWeb(query, callback);
        Path out = artifactPath(ctx, substep, "sources.json");
        Files.createDirectories(out.getParent());
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(results);
        Files.write(out, json.getBytes(StandardCharsets.UTF_8));
        ActionResult result = new ActionResult();
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("count", results != null ? results.size() : 0);
        outputs.put("source", "fallback");
        result.setOutputs(outputs);
        result.setArtifacts(List.of(out.toString()));
        result.setStatus("succeeded");
        return result;
    }

    private Path artifactPath(Map<String, Object> ctx, SubstepDocument substep, String name) {
        String planId = String.valueOf(ctx.get("planId"));
        String stepId = String.valueOf(ctx.get("stepId"));
        return Path.of("artifacts", planId, stepId, substep.getSubstepId(), name);
    }
}
