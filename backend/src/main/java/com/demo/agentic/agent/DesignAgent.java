package com.demo.agentic.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.demo.agentic.model.Artifact;
import com.demo.agentic.model.Run;

@Component
public class DesignAgent implements Agent {

    @Override
    public String name() {
        return "design-agent";
    }

    @Override
    public List<String> requiredContext() {
        return List.of("analysis");
    }

    @Override
    public AgentResult execute(Run run) {
        return build(Set.copyOf(analysis(run).features()));
    }

    /** Fallback designs only the core flow. */
    @Override
    public AgentResult fallback(Run run) {
        return build(Set.of());
    }

    private AgentResult build(Set<String> f) {
        List<Artifact> out = List.of(
                new Artifact("openapi.yaml", "api", openApi(f)),
                new Artifact("schema.sql", "schema", schema(f)),
                new Artifact("adr.md", "adr", adr(f)));
        return new AgentResult(out, Map.of("design", decisions(f)));
    }

    private String openApi(Set<String> f) {
        StringBuilder api = new StringBuilder("""
                openapi: 3.0.3
                info:
                  title: URL Shortener API
                  version: 1.1.0
                paths:
                  /api/urls:
                    post:
                      summary: Create a short URL
                      requestBody:
                        content:
                          application/json:
                            schema:
                              type: object
                              required: [url]
                              properties:
                                url: { type: string, format: uri }
                """);
        if (f.contains("CUSTOM_ALIAS")) api.append("                alias: { type: string, maxLength: 16 }\n");
        if (f.contains("EXPIRY")) api.append("                expiresInDays: { type: integer, minimum: 1 }\n");
        if (f.contains("MAX_USES")) api.append("                maxUses: { type: integer, minimum: 1 }\n");
        api.append("      responses:\n        '201': { description: Created }\n        '400': { description: Invalid URL }\n");
        if (f.contains("CUSTOM_ALIAS")) api.append("        '409': { description: Alias already taken }\n");
        if (f.contains("RATE_LIMIT")) api.append("        '429': { description: Too many requests }\n");
        api.append("  /r/{code}:\n    get:\n      summary: Redirect to the original URL\n      responses:\n");
        api.append("        '302': { description: Redirect }\n        '404': { description: Unknown code }\n");
        if (f.contains("EXPIRY") || f.contains("MAX_USES")) api.append("        '410': { description: Link expired or used up }\n");
        if (f.contains("ANALYTICS")) {
            api.append("  /api/urls/{code}/stats:\n    get:\n      summary: Click statistics\n      responses:\n        '200': { description: OK }\n");
        }
        return api.toString();
    }

    private String schema(Set<String> f) {
        StringBuilder sql = new StringBuilder("CREATE TABLE short_url (\n");
        sql.append("    id           BIGINT PRIMARY KEY,\n");
        sql.append("    code         VARCHAR(16) NOT NULL UNIQUE,\n");
        sql.append("    original_url VARCHAR(2048) NOT NULL,\n");
        if (f.contains("EXPIRY")) sql.append("    expires_at   TIMESTAMP NULL,\n");
        if (f.contains("MAX_USES")) sql.append("    max_uses     INT NULL,\n");
        if (f.contains("ANALYTICS") || f.contains("MAX_USES")) sql.append("    click_count  BIGINT NOT NULL DEFAULT 0,\n");
        sql.append("    created_at   TIMESTAMP NOT NULL\n);\n");
        return sql.toString();
    }

    private List<String> decisions(Set<String> f) {
        List<String> d = new ArrayList<>();
        d.add("ADR-1 Redirect with 302, not 301: browsers cache 301 and would bypass expiry and click counting.");
        d.add("ADR-2 Random Base62 codes (7 chars, about 3.5 trillion values) with a collision check: not guessable, costs one extra lookup on write.");
        d.add("ADR-3 Relational DB with a unique index on code: simple and consistent; revisit sharding only at much higher load.");
        if (f.contains("CACHING")) d.add("ADR-4 In-process cache for resolve: no new infrastructure, but each node holds its own copy so expiry can be stale until the cache TTL passes.");
        if (f.contains("MAX_USES")) d.add("ADR-6 Max uses enforced by one conditional UPDATE (count only if below the limit): no read-then-write, so concurrent clicks cannot exceed the limit.");
        if (f.contains("ANALYTICS")) d.add("ADR-5 Atomic 'click_count = click_count + 1' update: safe under concurrency, but one write per redirect.");
        return d;
    }

    private String adr(Set<String> f) {
        return "# Architecture decisions\n\n" + String.join("\n\n", decisions(f)) + "\n";
    }
}
