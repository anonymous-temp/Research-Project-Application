package com.faers.agent.dynamic.actions;

import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.pojo.StreamDataCallback;
import com.faers.agent.dynamic.model.SubstepDocument;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("merge_document")
public class MergeDocumentAction implements Action {
    @Override
    public ActionResult execute(SubstepDocument substep, Map<String, Object> ctx, StreamDataCallback callback, String traceId) throws Exception {
        String planId = String.valueOf(ctx.get("planId"));
        List<String> fragments = collectFragments(planId);
        StringBuilder sb = new StringBuilder();
        for (String f : fragments) {
            sb.append(new String(Files.readAllBytes(Path.of(f)), StandardCharsets.UTF_8)).append("\n\n");
        }
        Path out = artifactPath(ctx, substep, "proposal.md");
        Files.createDirectories(out.getParent());
        Files.write(out, sb.toString().getBytes(StandardCharsets.UTF_8));
        ActionResult result = new ActionResult();
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("path", out.toString());
        result.setOutputs(outputs);
        result.setArtifacts(List.of(out.toString()));
        result.setStatus("succeeded");
        return result;
    }

    private List<String> collectFragments(String planId) throws Exception {
        List<String> list = new ArrayList<>();
        Path root = Path.of("artifacts", planId);
        if (!Files.exists(root)) return list;
        Files.walk(root)
                .filter(p -> p.getFileName().toString().equals("section.md"))
                .forEach(p -> list.add(p.toString()));
        return list;
    }

    private Path artifactPath(Map<String, Object> ctx, SubstepDocument substep, String name) {
        String planId = String.valueOf(ctx.get("planId"));
        String stepId = String.valueOf(ctx.get("stepId"));
        return Path.of("artifacts", planId, stepId, substep.getSubstepId(), name);
    }
}
