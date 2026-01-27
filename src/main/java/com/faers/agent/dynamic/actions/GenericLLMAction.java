package com.faers.agent.dynamic.actions;

import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.dynamic.model.SubstepDocument;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.service.LLMInvocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("llm_task")
public class GenericLLMAction implements Action {
    @Autowired
    private LLMInvocationService llmInvocationService;

    @Override
    public ActionResult execute(SubstepDocument substep, Map<String, Object> ctx, StreamDataCallback callback, String traceId) throws Exception {
        String desc = String.valueOf(substep.getInputs().get("taskDescription"));
        AnalysisContext context = (AnalysisContext) ctx.get("context");
        String topic = context != null && context.getQueryParams() != null ? String.valueOf(context.getQueryParams().getOrDefault("topicName", "")) : "";
        String prompt = "根据任务描述，生成与项目申报书相关的可交付内容（Markdown）：\n任务：" + desc + "\n主题：" + topic;
        String content = llmInvocationService.invokeStream(prompt, callback, traceId);
        Path out = artifactPath(ctx, substep, "result.md");
        Files.createDirectories(out.getParent());
        Files.write(out, content.getBytes(StandardCharsets.UTF_8));
        ActionResult result = new ActionResult();
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("content", content);
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

