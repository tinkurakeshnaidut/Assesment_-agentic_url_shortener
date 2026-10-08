package com.demo.agentic.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.demo.agentic.model.Artifact;
import com.demo.agentic.model.Run;
import com.demo.agentic.model.Task;
import com.demo.agentic.model.TaskStatus;

@Component
public class ReleaseAgent implements Agent {

    @Override
    public String name() {
        return "release-agent";
    }

    @Override
    public AgentResult execute(Run run) {
        List<String> blockers = new ArrayList<>();
        StringBuilder md = new StringBuilder("# Release readiness\n\n");

        for (String id : List.of("IMPLEMENTATION", "TESTING", "SECURITY_REVIEW", "DOCUMENTATION")) {
            Task t = run.getTasks().get(id);
            boolean ok = t != null && t.getStatus() == TaskStatus.DONE && !t.isUsedFallback();
            md.append(ok ? "- [x] " : "- [ ] ").append(id);
            if (t != null && t.isUsedFallback()) md.append(" (reduced output from fallback)");
            md.append("\n");
            if (!ok) blockers.add(id);
        }

        Object open = run.getContext().getOrDefault("openRisks", 0L);
        md.append("- ").append(open.equals(0L) ? "[x]" : "[ ]").append(" no OPEN risks in the register (found: ").append(open).append(")\n");
        md.append("- [x] rollback plan: redeploy previous version; new columns are nullable/defaulted so the old code keeps working\n");

        boolean go = blockers.isEmpty() && open.equals(0L);
        md.append("\n**Recommendation: ").append(go ? "GO" : "NO-GO").append("**");
        if (!blockers.isEmpty()) md.append(" - blockers: ").append(blockers);
        md.append("\n\nA human must still sign off before the final summary is released.\n");

        return new AgentResult(List.of(new Artifact("release-readiness.md", "checklist", md.toString())),
                Map.of("releaseRecommendation", go ? "GO" : "NO-GO"));
    }
}
