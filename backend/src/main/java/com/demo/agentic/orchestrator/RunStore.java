package com.demo.agentic.orchestrator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.demo.agentic.model.Run;

@Component
public class RunStore {

    private final Map<String, Run> runs = new ConcurrentHashMap<>();

    public void save(Run run) {
        runs.put(run.getId(), run);
    }

    public Run get(String id) {
        Run run = runs.get(id);
        if (run == null) {
            throw new NoSuchElementException("run not found: " + id);
        }
        return run;
    }

    public List<Run> all() {
        return runs.values().stream().sorted(Comparator.comparing(Run::getCreatedAt).reversed()).toList();
    }
}
