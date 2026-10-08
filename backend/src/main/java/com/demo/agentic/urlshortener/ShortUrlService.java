package com.demo.agentic.urlshortener;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

@Service
public class ShortUrlService {

    private static final String LETTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int CODE_LENGTH = 7;

    private final ShortUrlRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ShortUrlService(ShortUrlRepository repository) {
        this.repository = repository;
    }

    public ShortUrl shorten(String url, Integer expiresInDays, Integer maxUses) {
        checkUrl(url);
        if (expiresInDays != null && expiresInDays < 1) {
            throw new IllegalArgumentException("expiresInDays must be at least 1");
        }
        if (maxUses != null && maxUses < 1) {
            throw new IllegalArgumentException("maxUses must be at least 1");
        }

        String code = newCode();
        while (repository.existsByCode(code)) {
            code = newCode();
        }

        ShortUrl link = new ShortUrl(code, url);
        if (expiresInDays != null) {
            link.setExpiresAt(Instant.now().plus(expiresInDays, ChronoUnit.DAYS));
        }
        link.setMaxUses(maxUses);
        return repository.save(link);
    }

    public String resolve(String code) {
        ShortUrl link = find(code);
        if (link.isExpired()) {
            throw new LinkExpiredException("link has expired");
        }
        if (repository.tryRecordClick(code) == 0) {
            throw new LinkExpiredException("link reached its maximum number of uses");
        }
        return link.getOriginalUrl();
    }

    public ShortUrl stats(String code) {
        return find(code);
    }

    private ShortUrl find(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("unknown code: " + code));
    }

    private void checkUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException("invalid url");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            throw new IllegalArgumentException("only http and https URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || isPrivateHost(host)) {
            throw new IllegalArgumentException("this host is not allowed");
        }
    }

    private boolean isPrivateHost(String host) {
        return host.equals("localhost")
                || host.startsWith("127.")
                || host.startsWith("10.")
                || host.startsWith("192.168.")
                || host.startsWith("169.254.")
                || host.matches("172\\.(1[6-9]|2[0-9]|3[01])\\..*");
    }

    private String newCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        }
        return code.toString();
    }
}
