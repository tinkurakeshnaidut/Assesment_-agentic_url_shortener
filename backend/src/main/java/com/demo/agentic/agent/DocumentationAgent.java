package com.demo.agentic.agent;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.demo.agentic.model.Artifact;
import com.demo.agentic.model.Run;

@Component
public class DocumentationAgent implements Agent {

    @Override
    public String name() {
        return "documentation-agent";
    }

    @Override
    public List<String> requiredContext() {
        return List.of("analysis");
    }

    @Override
    public AgentResult execute(Run run) {
        Set<String> f = Set.copyOf(analysis(run).features());
        StringBuilder md = new StringBuilder("# URL Shortener\n\n## Create a short link\n```\n");
        md.append("curl -X POST localhost:8080/api/urls -H 'Content-Type: application/json' \\\n  -d '{\"url\":\"https://example.com\"");
        if (f.contains("CUSTOM_ALIAS")) md.append(",\"alias\":\"my-link\"");
        if (f.contains("EXPIRY")) md.append(",\"expiresInDays\":7");
        if (f.contains("MAX_USES")) md.append(",\"maxUses\":3");
        md.append("}'\n```\n\n## Follow a link\n`GET /r/{code}` answers 302");
        if (f.contains("EXPIRY") || f.contains("MAX_USES")) md.append(", or 410 when the link expired or reached its maximum uses");
        md.append(".\n");
        if (f.contains("ANALYTICS")) md.append("\n## Statistics\n`GET /api/urls/{code}/stats` returns the click count.\n");
        if (f.contains("RATE_LIMIT")) md.append("\n## Limits\nLink creation is limited to 30 requests per minute per IP (HTTP 429).\n");

        String changelog = "## 1.1.0\n- " + analysis(run).problemStatement() + "\n";
        List<Artifact> out = List.of(
                new Artifact("URL_SHORTENER.md", "doc", md.toString()),
                new Artifact("CHANGELOG.md", "doc", changelog));
        return new AgentResult(out, Map.of("docs", out.size()));
    }
}
