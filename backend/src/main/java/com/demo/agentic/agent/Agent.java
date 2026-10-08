package com.demo.agentic.agent;

import java.util.List;
import java.util.Map;

import com.demo.agentic.model.Analysis;
import com.demo.agentic.model.Artifact;
import com.demo.agentic.model.Run;


public interface Agent {

    String name();

    default List<String> requiredContext() {
        return List.of();
    }

    AgentResult execute(Run run) throws Exception;

    default AgentResult fallback(Run run) {
        Artifact note = new Artifact(name() + "-fallback.md", "doc",
                "# Reduced output\n\n" + name() + " could not complete normally. Manual work is required.");
        return new AgentResult(List.of(note), Map.of());
    }

    default Analysis analysis(Run run) {
        return (Analysis) run.getContext().get("analysis");
    }
}
