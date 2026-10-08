package com.demo.agentic.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.demo.agentic.model.Analysis;
import com.demo.agentic.model.Artifact;
import com.demo.agentic.model.Run;

@Component
public class CodebaseAgent implements Agent {

    private record FeatureCheck(String feature, String marker, String change, List<String> files) { }

    private static final List<FeatureCheck> CHECKS = List.of(
            new FeatureCheck("EXPIRY", "expiresAt", "expires_at column and 410 for expired links",
                    List.of("ShortUrl", "ShortUrlService", "ShortUrlController")),
            new FeatureCheck("MAX_USES", "maxUses", "max_uses column and an atomic conditional counter update",
                    List.of("ShortUrl", "ShortUrlRepository", "ShortUrlService", "ShortUrlController")),
            new FeatureCheck("ANALYTICS", "clickCount", "click_count column and a stats endpoint",
                    List.of("ShortUrl", "ShortUrlRepository", "ShortUrlController")),
            new FeatureCheck("CUSTOM_ALIAS", "alias", "optional alias field and a uniqueness check",
                    List.of("ShortUrlService", "ShortUrlController")),
            new FeatureCheck("RATE_LIMIT", "RateLimitFilter", "per-IP limiter in front of POST /api/urls",
                    List.of("RateLimitFilter")),
            new FeatureCheck("CACHING", "Cacheable", "cache for resolve(code); stale-after-expiry risk",
                    List.of("ShortUrlService")),
            new FeatureCheck("URL_VALIDATION", "checkUrl", "scheme and host validation before saving",
                    List.of("ShortUrlService")));

    private final Path sourceDir;

    public CodebaseAgent() {
        this(Path.of("src/main/java/com/demo/agentic/urlshortener"));
    }

    public CodebaseAgent(Path sourceDir) {
        this.sourceDir = sourceDir;
    }

    @Override
    public String name() {
        return "codebase-agent";
    }

    @Override
    public List<String> requiredContext() {
        return List.of("analysis");
    }

    @Override
    public AgentResult execute(Run run) throws IOException {
        Analysis analysis = analysis(run);
        Map<String, String> sources = readSources();
        boolean scanned = !sources.isEmpty();

        StringBuilder md = new StringBuilder("# Impact analysis\n\n");
        md.append("## Existing modules\n");
        if (scanned) {
            sources.forEach((name, text) -> md.append("- ").append(name).append(" (").append(text.lines().count()).append(" lines)\n"));
            md.append("\n## Endpoints found in the code\n");
            findEndpoints(sources).forEach(e -> md.append("- ").append(e).append("\n"));
        } else {
            md.append("- source folder not found (").append(sourceDir).append("), feature status is UNKNOWN\n");
        }

        List<String> impacted = new ArrayList<>();
        md.append("\n## Feature check against the real code\n");
        for (String feature : analysis.features()) {
            FeatureCheck check = CHECKS.stream().filter(c -> c.feature().equals(feature)).findFirst().orElse(null);
            if (check == null) {
                continue;
            }
            List<String> owners = filesContaining(sources, check.marker());
            if (!scanned) {
                md.append("- ").append(feature).append(": UNKNOWN. Needed change: ").append(check.change()).append("\n");
                check.files().forEach(f -> addOnce(impacted, f));
            } else if (!owners.isEmpty()) {
                md.append("- ").append(feature).append(": ALREADY PRESENT in ").append(owners).append(". Only verify with tests.\n");
            } else {
                md.append("- ").append(feature).append(": MISSING. Change: ").append(check.change())
                        .append(". Files to change: ").append(check.files()).append("\n");
                check.files().forEach(f -> addOnce(impacted, f));
            }
        }
        if (run.getRequirement().toLowerCase().contains("fix")) {
            md.append("- Bug fix requested: write a failing regression test first, then fix.\n");
            addOnce(impacted, "ShortUrlService");
        }
        if (impacted.isEmpty()) {
            md.append("- Nothing to change for the requested features.\n");
        }

        md.append("\n## Data flows touched\n");
        md.append("- Create: client -> ShortUrlController -> ShortUrlService -> ShortUrlRepository -> SHORT_URL\n");
        md.append("- Redirect: client -> ShortUrlController (GET /r/{code}) -> ShortUrlService.resolve -> 302\n");
        md.append("\n## Risk\n");
        if (impacted.contains("ShortUrl")) {
            md.append("- Schema change on a live table: use nullable/defaulted columns and deploy the migration before the code.\n");
        }
        md.append("- Existing callers of POST /api/urls must keep working: all new request fields stay optional.\n");

        Artifact doc = new Artifact("impact-analysis.md", "impact", md.toString());
        return new AgentResult(List.of(doc), Map.of("impact", impacted));
    }

    private Map<String, String> readSources() throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        if (!Files.isDirectory(sourceDir)) {
            return sources;
        }
        try (Stream<Path> files = Files.list(sourceDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                String fileName = file.getFileName().toString();
                sources.put(fileName.substring(0, fileName.length() - 5), Files.readString(file));
            }
        }
        return sources;
    }

    private List<String> filesContaining(Map<String, String> sources, String marker) {
        List<String> owners = new ArrayList<>();
        sources.forEach((name, text) -> {
            if (text.toLowerCase().contains(marker.toLowerCase())) {
                owners.add(name);
            }
        });
        return owners;
    }

    private List<String> findEndpoints(Map<String, String> sources) {
        List<String> endpoints = new ArrayList<>();
        Pattern pattern = Pattern.compile("@(Post|Get)Mapping\\(\"([^\"]+)\"\\)");
        sources.forEach((name, text) -> {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                endpoints.add(m.group(1).toUpperCase() + " " + m.group(2) + " (" + name + ")");
            }
        });
        return endpoints;
    }

    private void addOnce(List<String> list, String value) {
        if (!list.contains(value)) {
            list.add(value);
        }
    }
}
