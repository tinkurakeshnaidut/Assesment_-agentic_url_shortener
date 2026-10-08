package com.demo.agentic.orchestrator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.demo.agentic.model.Artifact;

/**
 * Guardrails applied to every agent output (the exit gate). Security and compliance
 * rules live here; change control is handled by the approval checkpoints.
 */
@Component
public class PolicyEngine {

    private record Rule(String id, String description, Pattern pattern, Set<String> appliesTo) { }

    private static final List<Rule> RULES = List.of(
            new Rule("SEC-01", "hard-coded credential",
                    Pattern.compile("(?i)(password|secret|api[_-]?key|token)\\s*=\\s*\"[^\"]+\""), Set.of("code", "test")),
            new Rule("SEC-02", "SQL built by string concatenation",
                    Pattern.compile("(?i)\"\\s*(select|update|delete|insert)[^\"]*\"\\s*\\+"), Set.of("code")),
            new Rule("COMP-01", "System.out used instead of a logger",
                    Pattern.compile("System\\.out\\.print"), Set.of("code")));

    /** Returns one message per violation; an empty list means the artifacts pass. */
    public List<String> check(List<Artifact> artifacts) {
        List<String> violations = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            for (Rule rule : RULES) {
                if (rule.appliesTo().contains(artifact.type()) && rule.pattern().matcher(artifact.content()).find()) {
                    violations.add(rule.id() + " " + rule.description() + " in " + artifact.name());
                }
            }
        }
        return violations;
    }
}
