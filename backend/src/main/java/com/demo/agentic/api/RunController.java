package com.demo.agentic.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.demo.agentic.model.Run;
import com.demo.agentic.orchestrator.MetricsService;
import com.demo.agentic.orchestrator.Orchestrator;
import com.demo.agentic.orchestrator.RunStore;

@RestController
@RequestMapping("/api")
public class RunController {

    public record StartRequest(String requirement, Map<String, Integer> failureInjection) { }
    public record DecisionRequest(boolean approve, String reviewer, String comment) { }
    public record ReplanRequest(String requirement) { }
    public record Scenario(String name, String requirement, String hint) { }

    private static final List<Scenario> SCENARIOS = List.of(
            new Scenario("Greenfield",
                    "Build a URL shortener: a user submits a long URL and gets a short link. Links can expire after a number of days or after a maximum number of uses, clicks are counted, URLs are validated and link creation is rate limited per IP.",
                    "New system. Straight path; only the final sign-off needs approval. Release check ends in GO."),
            new Scenario("Brownfield",
                    "Enhance the existing URL shortener: add custom alias support and make sure expired links return 410.",
                    "Scans the real source code, finds what is missing, adds an impact step and asks for approval before coding."),
            new Scenario("Ambiguous",
                    "Improve the URL shortener: make it faster, more secure and scalable, with good test coverage etc.",
                    "Vague terms become open questions plus explicit assumptions; approval before coding."));

    private final Orchestrator orchestrator;
    private final RunStore store;
    private final MetricsService metrics;

    public RunController(Orchestrator orchestrator, RunStore store, MetricsService metrics) {
        this.orchestrator = orchestrator;
        this.store = store;
        this.metrics = metrics;
    }

    @GetMapping("/scenarios")
    public List<Scenario> scenarios() {
        return SCENARIOS;
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public Run start(@RequestBody StartRequest request) {
        return orchestrator.start(request.requirement(), request.failureInjection());
    }

    @GetMapping("/runs")
    public List<Run> list() {
        return store.all();
    }

    @GetMapping("/runs/{id}")
    public Run get(@PathVariable String id) {
        return store.get(id);
    }

    @PostMapping("/runs/{id}/tasks/{taskId}/decision")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void decide(@PathVariable String id, @PathVariable String taskId, @RequestBody DecisionRequest request) {
        orchestrator.decide(id, taskId, request.approve(), request.reviewer(), request.comment());
    }

    @PostMapping("/runs/{id}/replan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void replan(@PathVariable String id, @RequestBody ReplanRequest request) {
        orchestrator.replan(id, request.requirement());
    }

    @PostMapping("/runs/{id}/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void stop(@PathVariable String id) {
        orchestrator.stop(id);
    }

    @GetMapping("/metrics")
    public MetricsService.Snapshot metrics() {
        return metrics.snapshot();
    }
}
