package com.demo.agentic.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.demo.agentic.model.Artifact;
import com.demo.agentic.model.Run;

@Component
public class TestAgent implements Agent {

    @Override
    public String name() {
        return "test-agent";
    }

    @Override
    public List<String> requiredContext() {
        return List.of("analysis");
    }

    @Override
    public AgentResult execute(Run run) {
        Map<String, List<String>> matrix = new LinkedHashMap<>();
        StringBuilder code = new StringBuilder("class ShortUrlServiceTest {\n\n");

        addTest(matrix, code, "CORE", "shortenReturnsSevenCharCode", "assertEquals(7, service.shorten(URL).getCode().length());");
        addTest(matrix, code, "CORE", "resolveUnknownCodeThrowsNotFound", "assertThrows(NotFoundException.class, () -> service.resolve(\"nope\"));");

        for (String feature : analysis(run).features()) {
            switch (feature) {
                case "EXPIRY" -> {
                    addTest(matrix, code, feature, "resolveExpiredLinkThrowsGone", "assertThrows(LinkExpiredException.class, () -> service.resolve(expiredCode));");
                    addTest(matrix, code, feature, "linkWithoutExpiryNeverExpires", "assertDoesNotThrow(() -> service.resolve(permanentCode));");
                }
                case "MAX_USES" -> {
                    addTest(matrix, code, feature, "linkStopsWorkingAfterMaxUses", "assertThrows(LinkExpiredException.class, () -> useLinkTimes(maxUses + 1));");
                    addTest(matrix, code, feature, "concurrentClicksNeverExceedMaxUses", "assertEquals(maxUses, successfulClicksFromManyThreads());");
                }
                case "ANALYTICS" -> addTest(matrix, code, feature, "resolveIncrementsClickCount", "verify(repository).incrementClicks(code);");
                case "CUSTOM_ALIAS" -> addTest(matrix, code, feature, "duplicateAliasIsRejected", "assertThrows(CodeTakenException.class, () -> service.shorten(URL, \"taken\"));");
                case "RATE_LIMIT" -> addTest(matrix, code, feature, "thirtyFirstRequestGets429", "assertEquals(429, statusOfRequestNumber(31));");
                case "CACHING" -> addTest(matrix, code, feature, "secondResolveHitsCacheNotDatabase", "verify(repository, times(1)).findByCode(code);");
                case "URL_VALIDATION" -> addTest(matrix, code, feature, "ftpAndPrivateHostsAreRejected", "assertThrows(IllegalArgumentException.class, () -> validator.requireSafe(\"ftp://x.com\"));");
                default -> { }
            }
        }
        code.append("}\n");

        StringBuilder trace = new StringBuilder("# Test traceability\n\n| Feature | Tests |\n|---|---|\n");
        matrix.forEach((feature, tests) -> trace.append("| ").append(feature).append(" | ").append(String.join(", ", tests)).append(" |\n"));

        int total = matrix.values().stream().mapToInt(List::size).sum();
        List<Artifact> out = List.of(
                new Artifact("ShortUrlServiceTest.java", "test", code.toString()),
                new Artifact("test-traceability.md", "test", trace.toString()));
        return new AgentResult(out, Map.of("tests", total));
    }

    private void addTest(Map<String, List<String>> matrix, StringBuilder code, String feature, String name, String body) {
        matrix.computeIfAbsent(feature, k -> new ArrayList<>()).add(name);
        code.append("    @Test\n    void ").append(name).append("() {\n        ").append(body).append("\n    }\n\n");
    }
}
