package com.demo.agentic.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.demo.agentic.model.Artifact;
import com.demo.agentic.model.Run;

@Component
public class ImplementationAgent implements Agent {

    private static final String GENERATOR = """
            @Component
            public class ShortCodeGenerator {
                private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
                private final SecureRandom random = new SecureRandom();

                public String next() {
                    StringBuilder code = new StringBuilder(7);
                    for (int i = 0; i < 7; i++) {
                        code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
                    }
                    return code.toString();
                }
            }
            """;

    private static final String VALIDATOR = """
            @Component
            public class UrlValidator {
                public void requireSafe(String url) {
                    URI uri = URI.create(url);
                    String scheme = uri.getScheme();
                    if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
                        throw new IllegalArgumentException("Only http and https URLs are allowed");
                    }
                    String host = uri.getHost();
                    if (host == null || host.equals("localhost") || host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")) {
                        throw new IllegalArgumentException("Private or missing host is not allowed");
                    }
                }
            }
            """;

    private static final String RATE_LIMIT = """
            @Component
            public class RateLimitFilter extends OncePerRequestFilter {
                private static final int MAX_PER_MINUTE = 30;
                private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();

                @Scheduled(fixedRate = 60_000)
                void reset() { hits.clear(); }

                @Override
                protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                        throws IOException, ServletException {
                    if (req.getMethod().equals("POST") && req.getRequestURI().equals("/api/urls")) {
                        int count = hits.computeIfAbsent(req.getRemoteAddr(), ip -> new AtomicInteger()).incrementAndGet();
                        if (count > MAX_PER_MINUTE) {
                            res.setStatus(429);
                            return;
                        }
                    }
                    chain.doFilter(req, res);
                }
            }
            """;

    @Override
    public String name() {
        return "implementation-agent";
    }

    @Override
    public List<String> requiredContext() {
        return List.of("design");
    }

    @Override
    public AgentResult execute(Run run) {
        Set<String> f = Set.copyOf(analysis(run).features());
        List<Artifact> out = new ArrayList<>();
        out.add(new Artifact("ShortCodeGenerator.java", "code", GENERATOR));
        out.add(new Artifact("ShortUrlService.java", "code", service(f)));
        if (f.contains("URL_VALIDATION")) out.add(new Artifact("UrlValidator.java", "code", VALIDATOR));
        if (f.contains("RATE_LIMIT")) out.add(new Artifact("RateLimitFilter.java", "code", RATE_LIMIT));

        Integer left = run.getFailureInjection().get("IMPLEMENTATION_POLICY");
        if (left != null && left > 0) {
            run.getFailureInjection().put("IMPLEMENTATION_POLICY", left - 1);
            out.add(new Artifact("Debug.java", "code",
                    "String password = \"admin123\";\nSystem.out.println(\"debug \" + password);"));
        }
        return new AgentResult(out, Map.of("code", out.stream().map(Artifact::name).toList()));
    }

    private String service(Set<String> f) {
        boolean alias = f.contains("CUSTOM_ALIAS");
        boolean expiry = f.contains("EXPIRY");
        boolean maxUses = f.contains("MAX_USES");
        boolean counter = maxUses || f.contains("ANALYTICS");

        StringBuilder s = new StringBuilder("@Service\npublic class ShortUrlService {\n");
        s.append("    private final ShortUrlRepository repository;\n    private final ShortCodeGenerator generator;\n");
        if (f.contains("URL_VALIDATION")) s.append("    private final UrlValidator validator;\n");

        s.append("\n    public ShortUrl shorten(String url");
        if (alias) s.append(", String alias");
        if (expiry) s.append(", Integer expiresInDays");
        if (maxUses) s.append(", Integer maxUses");
        s.append(") {\n");
        if (f.contains("URL_VALIDATION")) s.append("        validator.requireSafe(url);\n");
        if (alias) {
            s.append("        String code = alias != null ? alias : generator.next();\n");
            s.append("        if (repository.existsByCode(code)) throw new IllegalStateException(\"alias already taken\");\n");
        } else {
            s.append("        String code = generator.next();\n");
            s.append("        while (repository.existsByCode(code)) code = generator.next();\n");
        }
        s.append("        ShortUrl link = new ShortUrl(code, url);\n");
        if (expiry) s.append("        if (expiresInDays != null) link.setExpiresAt(Instant.now().plus(expiresInDays, ChronoUnit.DAYS));\n");
        if (maxUses) s.append("        link.setMaxUses(maxUses);\n");
        s.append("        return repository.save(link);\n    }\n\n");

        if (f.contains("CACHING")) s.append("    @Cacheable(\"redirects\")\n");
        s.append("    public String resolve(String code) {\n");
        s.append("        ShortUrl link = repository.findByCode(code).orElseThrow(() -> new NoSuchElementException(code));\n");
        if (expiry) s.append("        if (link.isExpired()) throw new LinkExpiredException(\"link has expired\");\n");
        if (maxUses) {
            s.append("        if (repository.tryRecordClick(code) == 0) throw new LinkExpiredException(\"link reached its maximum number of uses\");\n");
        } else if (counter) {
            s.append("        repository.tryRecordClick(code);\n");
        }
        s.append("        return link.getOriginalUrl();\n    }\n}\n");
        return s.toString();
    }
}
