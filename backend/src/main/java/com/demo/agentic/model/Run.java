package com.demo.agentic.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Run {

    private static final Logger log = LoggerFactory.getLogger(Run.class);

    private final String id;
    private final Instant createdAt = Instant.now();
    private final Map<String, Object> context = new ConcurrentHashMap<>();
    private final List<AuditEvent> audit = new CopyOnWriteArrayList<>();
    private final Map<String, Integer> failureInjection = new ConcurrentHashMap<>();

    private volatile String requirement;
    private volatile int version = 1;
    private volatile String scenario = "UNKNOWN";
    private volatile RunStatus status = RunStatus.RUNNING;
    private volatile Map<String, Task> tasks = new LinkedHashMap<>();
    private volatile Instant finishedAt;
    private volatile boolean stopRequested;
    private volatile Analysis previousAnalysis;

    public Run(String id, String requirement) {
        this.id = id;
        this.requirement = requirement;
    }

    public void audit(String taskId, String type, String detail) {
        audit.add(new AuditEvent(Instant.now(), taskId, type, detail));
        log.info("[run {}] {} {} - {}", id, taskId, type, detail);
    }

    public boolean isTerminal() {
        return status == RunStatus.COMPLETED || status == RunStatus.FAILED || status == RunStatus.STOPPED;
    }

    public void nextVersion() { version++; }

    public Analysis previousAnalysis() { return previousAnalysis; }
    public void rememberAnalysis(Analysis a) { this.previousAnalysis = a; }

    public String getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Map<String, Object> getContext() { return context; }
    public List<AuditEvent> getAudit() { return audit; }
    public Map<String, Integer> getFailureInjection() { return failureInjection; }
    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }
    public int getVersion() { return version; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }
    public Map<String, Task> getTasks() { return tasks; }
    public void setTasks(Map<String, Task> tasks) { this.tasks = tasks; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public boolean isStopRequested() { return stopRequested; }
    public void setStopRequested(boolean stopRequested) { this.stopRequested = stopRequested; }
}
