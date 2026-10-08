package com.demo.agentic.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.demo.agentic.model.Analysis;
import com.demo.agentic.model.Artifact;
import com.demo.agentic.model.Run;

@Component
public class RequirementAgent implements Agent {

    private record Vague(String regex, String question, String assumption, List<String> features) { }

    private static final List<Vague> VAGUE_TERMS = List.of(
            new Vague("\\bfast(er)?\\b", "'faster' has no target. How fast?",
                    "Target p95 redirect latency under 50 ms using an in-memory cache", List.of("CACHING")),
            new Vague("\\bsecure\\b", "'secure' is not defined. Against which threats?",
                    "Reject non http/https and private-network URLs, and rate limit link creation",
                    List.of("URL_VALIDATION", "RATE_LIMIT")),
            new Vague("\\bscalab(le|ility)\\b", "No load figure given for 'scalable'",
                    "Assume 100 requests/second at peak and keep the service stateless", List.of()),
            new Vague("\\bgood\\b", "'good' is not measurable",
                    "Assume at least 80% line coverage on new code", List.of()),
            new Vague("\\betc\\b", "'etc' leaves the scope open",
                    "Scope is limited to what is listed explicitly", List.of()));

    @Override
    public String name() {
        return "requirement-agent";
    }

    @Override
    public AgentResult execute(Run run) {
        String text = run.getRequirement().toLowerCase();

        Set<String> features = new TreeSet<>();
        if (has(text, "expir|\\bttl\\b")) features.add("EXPIRY");
        if (has(text, "max(imum)?( number of)? (uses|clicks|visits)|max.?uses|usage limit|use limit|retry count|retries|(\\d+|n) (uses|times|visits)")) features.add("MAX_USES");
        if (has(text, "analytic|click|statistic")) features.add("ANALYTICS");
        if (has(text, "alias|custom (code|link|slug)")) features.add("CUSTOM_ALIAS");
        if (has(text, "rate.?limit|throttl|abuse")) features.add("RATE_LIMIT");
        if (has(text, "cach|latency")) features.add("CACHING");
        if (has(text, "validat|malicious")) features.add("URL_VALIDATION");

        List<String> ambiguities = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();
        for (Vague v : VAGUE_TERMS) {
            if (has(text, v.regex())) {
                ambiguities.add(v.question());
                assumptions.add(v.assumption());
                features.addAll(v.features());
            }
        }
        assumptions.add("Short codes are 7 random Base62 characters with a collision check");
        assumptions.add("No authentication in this version (public shortener)");
        assumptions.add("Relational database storage");

        boolean brownfield = has(text, "\\b(existing|enhance|fix|refactor|improve|add|update|bug)\\b");
        String scenario = brownfield ? "BROWNFIELD" : "GREENFIELD";
        String problem = scenario + ": deliver " + (features.isEmpty() ? "the core flow" : features)
                + " for the URL shortener (create short link, redirect to original URL).";

        Analysis analysis = new Analysis(scenario, !ambiguities.isEmpty(), problem,
                new ArrayList<>(features), ambiguities, assumptions);
        return result(analysis);
    }

    @Override
    public AgentResult fallback(Run run) {
        Analysis minimal = new Analysis("GREENFIELD", true, "Fallback: core flow only.", List.of(),
                List.of("Requirement could not be analysed automatically"), List.of("Core scope only"));
        return result(minimal);
    }

    private AgentResult result(Analysis a) {
        StringBuilder md = new StringBuilder("# Requirement analysis\n\n");
        md.append("**Scenario:** ").append(a.scenario()).append("\n\n");
        md.append("**Problem statement:** ").append(a.problemStatement()).append("\n\n");
        md.append("## Features\n").append(bullets(a.features())).append("\n");
        md.append("## Open questions (ambiguities)\n").append(a.ambiguities().isEmpty() ? "- none\n" : bullets(a.ambiguities())).append("\n");
        md.append("## Assumptions made\n").append(bullets(a.assumptions()));
        Artifact doc = new Artifact("requirement-analysis.md", "analysis", md.toString());
        return new AgentResult(List.of(doc), Map.of("analysis", a));
    }

    private static String bullets(List<String> items) {
        StringBuilder sb = new StringBuilder();
        items.forEach(i -> sb.append("- ").append(i).append("\n"));
        return sb.toString();
    }

    private static boolean has(String text, String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }
}
