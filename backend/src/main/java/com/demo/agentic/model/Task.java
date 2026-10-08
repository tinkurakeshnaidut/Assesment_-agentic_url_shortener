package com.demo.agentic.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Task {

    private final String id;
    private final String name;
    private final String agent;
    private final List<String> dependsOn = new CopyOnWriteArrayList<>();

    private volatile boolean requiresApproval;
    private volatile boolean approved;
    private volatile boolean usedFallback;
    private volatile int attempts;
    private volatile String message = "";
    private volatile TaskStatus status = TaskStatus.PENDING;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile List<Artifact> artifacts = new ArrayList<>();

    public Task(String id, String name, String agent, List<String> dependsOn, boolean requiresApproval) {
        this.id = id;
        this.name = name;
        this.agent = agent;
        this.dependsOn.addAll(dependsOn);
        this.requiresApproval = requiresApproval;
    }

    public void reset() {
        status = TaskStatus.PENDING;
        approved = false;
        usedFallback = false;
        attempts = 0;
        message = "";
        startedAt = null;
        finishedAt = null;
        artifacts = new ArrayList<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAgent() { return agent; }
    public List<String> getDependsOn() { return dependsOn; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public boolean isUsedFallback() { return usedFallback; }
    public void setUsedFallback(boolean usedFallback) { this.usedFallback = usedFallback; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public List<Artifact> getArtifacts() { return artifacts; }
    public void setArtifacts(List<Artifact> artifacts) { this.artifacts = artifacts; }
}
