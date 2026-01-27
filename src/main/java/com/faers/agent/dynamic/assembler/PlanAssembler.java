package com.faers.agent.dynamic.assembler;

import com.faers.agent.dynamic.model.PlanDocument;
import com.faers.agent.dynamic.model.StepDocument;
import com.faers.agent.dynamic.model.SubstepDocument;
import com.faers.agent.dto.responseDto.Response;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PlanAssembler {
    public Response toPlannerOverview(PlanDocument plan) {
        String analysis = "动态规划进度";
        List<String> todo = new ArrayList<>();
        List<String> details = new ArrayList<>();
        int i = 1;
        for (StepDocument s : plan.getSteps()) {
            todo.add(s.getTitle());
            int j = 1;
            for (SubstepDocument sub : s.getSubsteps()) {
                details.add(i + "." + j + " " + sub.getAction());
                j++;
            }
            i++;
        }
        return Response.itemBuilder(analysis, new ArrayList<>(todo), new ArrayList<>(details));
    }
}

