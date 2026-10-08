package com.demo.agentic.orchestrator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.demo.agentic.agent.Agent;
import com.demo.agentic.agent.AgentResult;
import com.demo.agentic.model.Analysis;
import com.demo.agentic.model.Run;
import com.demo.agentic.model.RunStatus;
import com.demo.agentic.model.Task;
import com.demo.agentic.model.TaskStatus;

import jakarta.annotation.PreDestroy;


@Service
public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final String CODEBASE = "CODEBASE_ANALYSIS";

    private final Map<String, Agent> agents = new HashMap<>();
    private final PolicyEngine policy;
    private final MetricsService metrics;
    private final RunStore store;
    private final long stepDelayMs;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public Orchestrator(List<Agent> agentList, PolicyEngine policy, MetricsService metrics, RunStore store,
                        @Value("${agentic.step-delay-ms:600}") long stepDelayMs) {
        agentList.forEach(a -> agents.put(a.name(), a));
        this.policy = policy;
        this.metrics = metrics;
        this.store = store;
        this.stepDelayMs = stepDelayMs;
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }


    public Run start(String requirement, Map<String, Integer> failureInjection) {
        if (requirement == null || requirement.isBlank()) {
            throw new IllegalArgumentException("requirement must not be empty");
        }
        Run run = new Run(UUID.randomUUID().toString().substring(0, 8), requirement.trim());
        run.setTasks(basePlan());
        if (failureInjection != null) {
            run.getFailureInjection().putAll(failureInjection);
        }
        run.audit("-", "RUN_STARTED", "requirement v1 received");
        store.save(run);
        metrics.runStarted();
        pool.submit(() -> safeAdvance(run));
        return run;
    }

    /** Human decision approval. */
    public void decide(String runId, String taskId, boolean approve, String reviewer, String comment) {
        Run run = store.get(runId);
        synchronized (run) {
            Task task = run.getTasks().get(taskId);
            if (task == null) {
                throw new NoSuchElementException("task not found: " + taskId);
            }
            if (task.getStatus() != TaskStatus.WAITING_APPROVAL) {
                throw new IllegalStateException("task " + taskId + " is not waiting for approval");
            }
            String who = (reviewer == null || reviewer.isBlank()) ? "reviewer" : reviewer;
            String note = comment == null ? "" : comment;
            if (!approve) {
                task.setStatus(TaskStatus.REJECTED);
                run.audit(taskId, "REJECTED", "by " + who + " " + note);
                finish(run, RunStatus.STOPPED, "rejected by " + who);
                return;
            }
            task.setApproved(true);
            task.setStatus(TaskStatus.PENDING);
            run.audit(taskId, "APPROVED", "by " + who + " " + note);
            run.setStatus(RunStatus.RUNNING);
        }
        pool.submit(() -> safeAdvance(run));
    }

    public void replan(String runId, String newRequirement) {
        if (newRequirement == null || newRequirement.isBlank()) {
            throw new IllegalArgumentException("requirement must not be empty");
        }
        Run run = store.get(runId);
        synchronized (run) {
            run.rememberAnalysis((Analysis) run.getContext().get("analysis"));
            run.setRequirement(newRequirement.trim());
            run.nextVersion();
            for (Task t : run.getTasks().values()) {
                TaskStatus s = t.getStatus();
                boolean stuck = s == TaskStatus.WAITING_APPROVAL || s == TaskStatus.ROLLED_BACK || s == TaskStatus.REJECTED;
                if (t.getId().equals("REQUIREMENTS") || stuck) {
                    t.reset();
                }
            }
            run.setStopRequested(false);
            run.setFinishedAt(null);
            run.setStatus(RunStatus.RUNNING);
            run.audit("REQUIREMENTS", "REPLAN_REQUESTED", "requirement changed, now v" + run.getVersion());
            metrics.replan();
        }
        pool.submit(() -> safeAdvance(run));
    }

    public void stop(String runId) {
        Run run = store.get(runId);
        run.setStopRequested(true);
        pool.submit(() -> {
            synchronized (run) {
                if (!run.isTerminal()) {
                    finish(run, RunStatus.STOPPED, "stopped by operator");
                }
            }
        });
    }


    private void safeAdvance(Run run) {
        try {
            advance(run);
        } catch (Exception e) {
            log.error("run {} crashed", run.getId(), e);
            synchronized (run) {
                finish(run, RunStatus.FAILED, "unexpected error: " + e.getMessage());
            }
        }
    }

    private void advance(Run run) {
        synchronized (run) {
            if (run.isTerminal()) {
                return;
            }
            run.setStatus(RunStatus.RUNNING);

            while (true) {
                if (run.isStopRequested()) {
                    finish(run, RunStatus.STOPPED, "stopped by operator");
                    return;
                }
                List<Task> wave = pickRunnableTasks(run);
                if (wave.isEmpty()) {
                    break;
                }
                if (!runWave(run, wave)) {
                    finish(run, RunStatus.FAILED, "safe stop: a task was rolled back, remaining work was not started");
                    return;
                }
                afterWave(run, wave);
            }
            settle(run);
        }
    }

    private List<Task> pickRunnableTasks(Run run) {
        List<Task> runnable = new ArrayList<>();
        for (Task task : run.getTasks().values()) {
            if (task.getStatus() != TaskStatus.PENDING || !entryGateOpen(run, task)) {
                continue;
            }
            if (task.isRequiresApproval() && !task.isApproved()) {
                task.setStatus(TaskStatus.WAITING_APPROVAL);
                run.audit(task.getId(), "APPROVAL_REQUESTED", "high-impact step, waiting for a human decision");
                continue;
            }
            runnable.add(task);
        }
        return runnable;
    }

    private boolean entryGateOpen(Run run, Task task) {
        for (String dep : task.getDependsOn()) {
            if (run.getTasks().get(dep).getStatus() != TaskStatus.DONE) {
                return false;
            }
        }
        Agent agent = agents.get(task.getAgent());
        return agent.requiredContext().stream().allMatch(run.getContext()::containsKey);
    }

    private boolean runWave(Run run, List<Task> wave) {
        run.audit("-", "WAVE_STARTED", wave.stream().map(Task::getId).toList() + (wave.size() > 1 ? " in parallel" : ""));
        List<Callable<Boolean>> jobs = new ArrayList<>();
        for (Task task : wave) {
            jobs.add(() -> executeTask(run, task));
        }
        boolean allOk = true;
        try {
            for (Future<Boolean> result : pool.invokeAll(jobs)) {
                if (!result.get()) {
                    allOk = false;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException e) {
            run.audit("-", "WAVE_ERROR", String.valueOf(e.getCause()));
            return false;
        }
        return allOk;
    }

    private void settle(Run run) {
        var tasks = run.getTasks().values();
        if (tasks.stream().allMatch(t -> t.getStatus() == TaskStatus.DONE)) {
            finish(run, RunStatus.COMPLETED, "all tasks done");
        } else if (tasks.stream().anyMatch(t -> t.getStatus() == TaskStatus.WAITING_APPROVAL)) {
            run.setStatus(RunStatus.AWAITING_APPROVAL);
        } else {
            finish(run, RunStatus.FAILED, "blocked: a dependency or entry gate can never be satisfied");
        }
    }

    private void finish(Run run, RunStatus status, String reason) {
        run.setStatus(status);
        run.setFinishedAt(Instant.now());
        run.audit("-", "RUN_" + status, reason);
        metrics.runFinished(status, Duration.between(run.getCreatedAt(), run.getFinishedAt()).toMillis());
    }

    private boolean executeTask(Run run, Task task) {
        Agent agent = agents.get(task.getAgent());
        task.setStatus(TaskStatus.RUNNING);
        task.setStartedAt(Instant.now());
        run.audit(task.getId(), "TASK_STARTED", "agent=" + agent.name() + ", inputs from " + task.getDependsOn()
                + ", requirement v" + run.getVersion());

        Instant firstFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            task.setAttempts(task.getAttempts() + 1);
            metrics.attempt();
            String problem = tryOnce(run, task, agent, false);
            if (problem == null) {
                complete(run, task, firstFailure, false);
                return true;
            }
            if (firstFailure == null) {
                firstFailure = Instant.now();
            }
            run.audit(task.getId(), "ATTEMPT_FAILED", "attempt " + attempt + "/" + MAX_ATTEMPTS + ": " + problem);
            if (attempt < MAX_ATTEMPTS) {
                metrics.retry();
            }
        }

        run.audit(task.getId(), "FALLBACK", "retries used up, trying the reduced-scope fallback");
        metrics.fallback();
        String problem = tryOnce(run, task, agent, true);
        if (problem == null) {
            complete(run, task, firstFailure, true);
            return true;
        }

        task.setArtifacts(new ArrayList<>());
        task.setStatus(TaskStatus.ROLLED_BACK);
        task.setFinishedAt(Instant.now());
        task.setMessage(problem);
        metrics.rollback();
        run.audit(task.getId(), "ROLLBACK", "outputs discarded: " + problem);
        return false;
    }

    private String tryOnce(Run run, Task task, Agent agent, boolean useFallback) {
        try {
            pause();
            Integer remaining = run.getFailureInjection().get(task.getId());
            if (remaining != null && remaining > 0) {
                run.getFailureInjection().put(task.getId(), remaining - 1);
                throw new IllegalStateException("injected failure (demo)");
            }
            AgentResult result = useFallback ? agent.fallback(run) : agent.execute(run);

            // exit gate
            if (result.artifacts().isEmpty()) {
                return "exit gate: no artifacts produced";
            }
            List<String> violations = policy.check(result.artifacts());
            if (!violations.isEmpty()) {
                return "policy gate: " + String.join("; ", violations);
            }
            task.setArtifacts(new ArrayList<>(result.artifacts()));
            run.getContext().putAll(result.context());
            return null;
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private void complete(Run run, Task task, Instant firstFailure, boolean usedFallback) {
        task.setUsedFallback(usedFallback);
        task.setStatus(TaskStatus.DONE);
        task.setFinishedAt(Instant.now());
        task.setMessage(usedFallback ? "reduced output (fallback)" : "ok");
        if (firstFailure != null) {
            metrics.recovered(Duration.between(firstFailure, task.getFinishedAt()).toMillis());
        }
        run.audit(task.getId(), "TASK_DONE", "attempts=" + task.getAttempts()
                + ", artifacts=" + task.getArtifacts().stream().map(a -> a.name()).toList());
    }

    private void pause() throws InterruptedException {
        if (stepDelayMs > 0) {
            Thread.sleep(stepDelayMs);
        }
    }

    private Map<String, Task> basePlan() {
        Map<String, Task> plan = new LinkedHashMap<>();
        add(plan, new Task("REQUIREMENTS", "Understand requirement", "requirement-agent", List.of(), false));
        add(plan, new Task("DESIGN", "API, schema and decisions", "design-agent", List.of("REQUIREMENTS"), false));
        add(plan, new Task("IMPLEMENTATION", "Generate code", "implementation-agent", List.of("DESIGN"), false));
        add(plan, new Task("TESTING", "Generate tests", "test-agent", List.of("IMPLEMENTATION"), false));
        add(plan, new Task("SECURITY_REVIEW", "Risk register and policy scan", "security-agent", List.of("IMPLEMENTATION"), false));
        add(plan, new Task("DOCUMENTATION", "Write documentation", "documentation-agent", List.of("IMPLEMENTATION"), false));
        add(plan, new Task("RELEASE_READINESS", "Release checklist", "release-agent",
                List.of("TESTING", "SECURITY_REVIEW", "DOCUMENTATION"), false));
        add(plan, new Task("FINAL_SUMMARY", "Final summary after sign-off", "summary-agent", List.of("RELEASE_READINESS"), true));
        return plan;
    }

    private void add(Map<String, Task> plan, Task task) {
        plan.put(task.getId(), task);
    }

    private void afterWave(Run run, List<Task> wave) {
        if (wave.stream().noneMatch(t -> t.getId().equals("REQUIREMENTS"))) {
            return;
        }
        Analysis analysis = (Analysis) run.getContext().get("analysis");
        adaptPlan(run, analysis);

        Analysis before = run.previousAnalysis();
        if (before == null) {
            return;
        }
        run.rememberAnalysis(null);
        if (before.equals(analysis)) {
            run.audit("REQUIREMENTS", "REPLAN_NOOP", "analysis unchanged, downstream outputs are kept");
            return;
        }
        Set<String> stale = downstreamOf("REQUIREMENTS", run);
        for (String id : stale) {
            run.getTasks().get(id).reset();
        }
        run.audit("REQUIREMENTS", "REPLAN", "analysis changed, invalidated " + stale);
    }

    private void adaptPlan(Run run, Analysis analysis) {
        Map<String, Task> tasks = run.getTasks();
        Task design = tasks.get("DESIGN");
        run.setScenario(analysis.scenario());

        if (analysis.isBrownfield() && !tasks.containsKey(CODEBASE)) {
            Task codebase = new Task(CODEBASE, "Impact analysis of existing code", "codebase-agent", List.of("REQUIREMENTS"), false);
            Map<String, Task> rebuilt = new LinkedHashMap<>();
            tasks.forEach((id, t) -> {
                rebuilt.put(id, t);
                if (id.equals("REQUIREMENTS")) {
                    rebuilt.put(CODEBASE, codebase);
                }
            });
            run.setTasks(rebuilt);
            design.getDependsOn().add(CODEBASE);
            run.audit("-", "PLAN_ADAPTED", "brownfield: added " + CODEBASE + " before DESIGN");
        } else if (!analysis.isBrownfield() && tasks.containsKey(CODEBASE)) {
            Map<String, Task> rebuilt = new LinkedHashMap<>(tasks);
            rebuilt.remove(CODEBASE);
            run.setTasks(rebuilt);
            design.getDependsOn().remove(CODEBASE);
            run.audit("-", "PLAN_ADAPTED", "greenfield: removed " + CODEBASE);
        }

        Task impl = run.getTasks().get("IMPLEMENTATION");
        boolean needsReview = analysis.isBrownfield() || analysis.ambiguous();
        if (impl.isRequiresApproval() != needsReview) {
            impl.setRequiresApproval(needsReview);
            run.audit("IMPLEMENTATION", "PLAN_ADAPTED", "approval before coding = " + needsReview);
        }
    }

    private Set<String> downstreamOf(String rootId, Run run) {
        Set<String> stale = new HashSet<>();
        Set<String> known = new HashSet<>(Set.of(rootId));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Task t : run.getTasks().values()) {
                if (!known.contains(t.getId()) && t.getDependsOn().stream().anyMatch(known::contains)) {
                    known.add(t.getId());
                    stale.add(t.getId());
                    changed = true;
                }
            }
        }
        return stale;
    }
}
