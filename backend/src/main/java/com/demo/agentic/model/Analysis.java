package com.demo.agentic.model;

import java.util.List;

public record Analysis(String scenario, boolean ambiguous, String problemStatement,
                       List<String> features, List<String> ambiguities, List<String> assumptions) {

    public boolean isBrownfield() {
        return "BROWNFIELD".equals(scenario);
    }
}
