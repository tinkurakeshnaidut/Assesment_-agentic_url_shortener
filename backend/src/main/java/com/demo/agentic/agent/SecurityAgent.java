package com.demo.agentic.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.demo.agentic.model.Artifact;
import com.demo.agentic.model.Run;
import com.demo.agentic.model.Task;
import com.demo.agentic.orchestrator.PolicyEngine;

@Component
public class SecurityAgent implements Agent {

    private final PolicyEngine policy;

    public SecurityAgent(PolicyEngine policy) {
        this.policy = policy;
    }

    @Override
    public String name() {
        return "security-agent";
    }

    @Override
    public List<String> requiredContext() {
        return List.of("analysis");
    }

    @Override
    public AgentResult execute(Run run) {
        Set<String> f = Set.copyOf(analysis(run).features());
        List<String[]> rows = new ArrayList<>();

        rows.add(new String[]{"Open redirect / phishing through short links", "High",
                "Allow only http/https and block private hosts", f.contains("URL_VALIDATION") ? "MITIGATED" : "OPEN"});
        rows.add(new String[]{"Link creation flooding (abuse)", "Medium",
                "Per-IP rate limit on POST /api/urls", f.contains("RATE_LIMIT") ? "MITIGATED" : "OPEN"});
        rows.add(new String[]{"Guessing codes (enumeration)", "Medium",
                "Random 7-char Base62 codes, about 3.5 trillion values", f.contains("RATE_LIMIT") ? "MITIGATED" : "PARTIAL"});
        rows.add(new String[]{"Database outage", "High",
                "Return 503, health check, retry with backoff", "ACCEPTED"});
        if (f.contains("MAX_USES")) {
            rows.add(new String[]{"Two clicks race for the last allowed use", "Medium",
                    "One atomic conditional UPDATE, no read-then-write", "MITIGATED"});
        }
        if (f.contains("CACHING") && f.contains("EXPIRY")) {
            rows.add(new String[]{"Cache serves a link after it expired", "Medium",
                    "Cache TTL shorter than the smallest expiry; evict on expiry", "MITIGATED"});
        }
        if (f.contains("CACHING") && f.contains("ANALYTICS")) {
            rows.add(new String[]{"Click counts missed on cache hits", "Medium",
                    "Count clicks in the controller, not inside the cached method", "MITIGATED"});
        }
        if (f.contains("EXPIRY") || f.contains("ANALYTICS") || f.contains("MAX_USES")) {
            rows.add(new String[]{"Schema change on a live table", "Medium",
                    "Nullable or defaulted columns; run migration before deploying code", "MITIGATED"});
        }

        List<Artifact> code = new ArrayList<>();
        Task impl = run.getTasks().get("IMPLEMENTATION");
        if (impl != null) code.addAll(impl.getArtifacts());
        List<String> violations = policy.check(code);

        StringBuilder md = new StringBuilder("# Risk register\n\n| Risk | Severity | Mitigation | Status |\n|---|---|---|---|\n");
        rows.forEach(r -> md.append("| ").append(String.join(" | ", r)).append(" |\n"));
        md.append("\n## Trade-offs\n");
        md.append("- 302 instead of 301: every redirect hits our service, in exchange for working expiry and analytics.\n");
        md.append("- Random codes instead of counters: unguessable, but need a collision check.\n");
        md.append("\n## Policy re-scan of generated code\n");
        md.append(violations.isEmpty() ? "- no violations\n" : "- " + String.join("\n- ", violations) + "\n");

        long open = rows.stream().filter(r -> r[3].equals("OPEN")).count();
        return new AgentResult(List.of(new Artifact("risk-register.md", "risk", md.toString())),
                Map.of("openRisks", open));
    }
}
