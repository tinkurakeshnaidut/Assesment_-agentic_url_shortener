package com.demo.agentic.orchestrator;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.demo.agentic.model.RunStatus;

@Component
public class MetricsService {

    public record Snapshot(int runsStarted, int runsCompleted, int runsFailed, int runsStopped,
                           double successRatePercent, int taskAttempts, int retries, int fallbacks,
                           int rollbacks, int replans, double avgMttrMs, double avgEndToEndMs) { }

    private int started, completed, failed, stopped, attempts, retries, fallbacks, rollbacks, replans;
    private final List<Long> recoveryMillis = new ArrayList<>();
    private final List<Long> endToEndMillis = new ArrayList<>();

    public synchronized void runStarted() { started++; }
    public synchronized void attempt() { attempts++; }
    public synchronized void retry() { retries++; }
    public synchronized void fallback() { fallbacks++; }
    public synchronized void rollback() { rollbacks++; }
    public synchronized void replan() { replans++; }

    public synchronized void recovered(long millis) { recoveryMillis.add(millis); }

    public synchronized void runFinished(RunStatus status, long millis) {
        switch (status) {
            case COMPLETED -> { completed++; endToEndMillis.add(millis); }
            case FAILED -> failed++;
            case STOPPED -> stopped++;
            default -> { }
        }
    }

    public synchronized Snapshot snapshot() {
        int finished = completed + failed + stopped;
        double successRate = finished == 0 ? 0 : 100.0 * completed / finished;
        return new Snapshot(started, completed, failed, stopped, round(successRate), attempts, retries,
                fallbacks, rollbacks, replans, round(avg(recoveryMillis)), round(avg(endToEndMillis)));
    }

    private static double avg(List<Long> values) {
        return values.isEmpty() ? 0 : values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
