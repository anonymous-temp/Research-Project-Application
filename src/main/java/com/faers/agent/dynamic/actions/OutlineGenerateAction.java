package com.faers.agent.dynamic.actions;

import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.service.LLMInvocationService;
import com.faers.agent.dynamic.model.SubstepDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("outline_generate")
public class OutlineGenerateAction implements Action {
    @Autowired
    private LLMInvocationService llmInvocationService;

    @Override
    public ActionResult execute(SubstepDocument substep, Map<String, Object> ctx, StreamDataCallback callback, String traceId) throws Exception {
        String topic = String.valueOf(substep.getInputs().get("topic"));
        String prompt = "根据主题生成项目申报书大纲，使用Markdown列表输出，主题：" + topic;
        String outline = llmInvocationService.invokeStream(prompt, callback, traceId);
        Path out = artifactPath(ctx, substep, "outline.md");
        Files.createDirectories(out.getParent());
        Files.write(out, outline.getBytes(StandardCharsets.UTF_8));
        ActionResult result = new ActionResult();
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("outline", outline);
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
