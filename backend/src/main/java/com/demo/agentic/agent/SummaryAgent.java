package com.demo.agentic.agent;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.demo.agentic.model.Analysis;
import com.demo.agentic.model.Artifact;
import com.demo.agentic.model.AuditEvent;
import com.demo.agentic.model.Run;
import com.demo.agentic.model.Task;

@Component
public class SummaryAgent implements Agent {

    @Override
    public String name() {
        return "summary-agent";
    }

    @Override
    public AgentResult execute(Run run) {
        Analysis a = analysis(run);
        StringBuilder md = new StringBuilder("# Final engineering summary\n\n");
        md.append("**Requirement (v").append(run.getVersion()).append("):** ").append(run.getRequirement()).append("\n\n");
        md.append("**Problem statement:** ").append(a.problemStatement()).append("\n\n");

        md.append("## Plan and rationale\n");
        for (Task t : run.getTasks().values()) {
            md.append("- ").append(t.getId()).append(" (after ").append(t.getDependsOn().isEmpty() ? "-" : t.getDependsOn()).append(")");
            if (t.isRequiresApproval()) md.append(" [human approval]");
            md.append("\n");
        }
        md.append("\nTesting, security review and documentation only need the implementation, so they run in parallel and join at release readiness.\n");

        md.append("\n## Artifacts\n");
        for (Task t : run.getTasks().values()) {
            if (!t.getArtifacts().isEmpty()) {
                md.append("- ").append(t.getId()).append(": ")
                        .append(t.getArtifacts().stream().map(Artifact::name).toList()).append("\n");
            }
        }

        md.append("\n## Risks, trade-offs and validation\n");
        md.append("See risk-register.md and test-traceability.md. Every output passed the policy gate; release recommendation: ")
                .append(run.getContext().getOrDefault("releaseRecommendation", "n/a")).append(".\n");

        md.append("\n## Assumptions\n");
        a.assumptions().forEach(x -> md.append("- ").append(x).append("\n"));

        md.append("\n## Limitations\n");
        md.append("- Agents are rule/template based; the generated code is not compiled or executed by this system.\n");
        md.append("- Runs are kept in memory and lost on restart.\n");
        md.append("- Proposed changes are artifacts for human review; they are not applied to the source automatically.\n");

        long approvals = run.getAudit().stream().filter(e -> e.type().equals("APPROVED")).count();
        long retries = run.getAudit().stream().filter(e -> e.type().equals("ATTEMPT_FAILED")).count();
        md.append("\n## Decision lineage\n");
        md.append("- ").append(run.getAudit().size()).append(" audit events, ").append(approvals)
                .append(" human approvals, ").append(retries).append(" failed attempts handled.\n");

        return new AgentResult(List.of(new Artifact("engineering-summary.md", "summary", md.toString())), Map.of());
    }
}
