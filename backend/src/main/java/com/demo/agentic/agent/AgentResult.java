package com.demo.agentic.agent;

import java.util.List;
import java.util.Map;

import com.demo.agentic.model.Artifact;

/**
 * What an agent hands back. The context entries are only committed to the shared
 * run context after the exit gate passes, so a rollback leaves no half-written state.
 */
public record AgentResult(List<Artifact> artifacts, Map<String, Object> context) { }
