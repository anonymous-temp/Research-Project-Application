package com.faers.agent.dynamic.actions;

import com.faers.agent.agent.context.AnalysisContext;
import com.faers.agent.agent.impl.ReportGenerationAgent;
import com.faers.agent.dynamic.model.SubstepDocument;
import com.faers.agent.dto.responseDto.Response;
import com.faers.agent.pojo.StreamDataCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Component("section_write_legacy")
public class LegacySectionWriteAction implements Action {
    @Autowired
    private ReportGenerationAgent reportGenerationAgent;

    @Override
    public ActionResult execute(SubstepDocument substep, Map<String, Object> ctx, StreamDataCallback callback, String traceId) throws Exception {
        String sectionCode = String.valueOf(substep.getInputs().get("sectionCode"));
        AnalysisContext context = (AnalysisContext) ctx.get("context");
        String content = generateSection(sectionCode, context, callback);
        Path out = artifactPath(ctx, substep, "section.md");
        Files.createDirectories(out.getParent());
        Files.write(out, content.getBytes(StandardCharsets.UTF_8));
        ActionResult result = new ActionResult();
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("content", content);
        result.setOutputs(outputs);
        result.setArtifacts(java.util.List.of(out.toString()));
        result.setStatus("succeeded");
        return result;
    }

    private String generateSection(String code, AnalysisContext context, StreamDataCallback callback) {
        switch (code) {
            case "4.1":
                return reportGenerationAgent.generateAbstract(context, callback);
            case "4.2":
                return reportGenerationAgent.generateResearchSignificance(context, callback);
            case "4.3":
                return reportGenerationAgent.generateResearchStatus(context, callback);
            case "4.4":
                return reportGenerationAgent.generateResearchIdea(context, callback);
            case "4.5":
                return reportGenerationAgent.generateApplicationProspect(context, callback);
            case "4.6":
                return reportGenerationAgent.generateResearchContent(context, callback);
            case "4.7":
                return reportGenerationAgent.generateResearchObjectives(context, callback);
            case "4.8":
                return reportGenerationAgent.generateKeyScientificProblems(context, callback);
            case "4.9":
                return reportGenerationAgent.generateTechnicalRoute(context, callback);
            case "4.10":
                return reportGenerationAgent.generateResearchPlan(context, callback);
            case "4.11":
                return reportGenerationAgent.generateKeyTechnologies(context, callback);
            case "4.12":
                return reportGenerationAgent.generateFeasibilityAnalysis(context, callback);
            case "4.13":
                return reportGenerationAgent.generateIdeaInnovation(context, callback);
            case "4.14":
                return reportGenerationAgent.generateTechnologyInnovation(context, callback);
            default:
                return "";
        }
    }

    private Path artifactPath(Map<String, Object> ctx, SubstepDocument substep, String name) {
        String planId = String.valueOf(ctx.get("planId"));
        String stepId = String.valueOf(ctx.get("stepId"));
        return Path.of("artifacts", planId, stepId, substep.getSubstepId(), name);
    }
}
