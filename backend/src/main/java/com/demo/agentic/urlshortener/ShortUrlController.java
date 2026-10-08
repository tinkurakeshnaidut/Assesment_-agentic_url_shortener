package com.demo.agentic.urlshortener;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShortUrlController {

    public record CreateRequest(String url, Integer expiresInDays, Integer maxUses) { }

    public record LinkInfo(String code, String shortPath, String originalUrl, int clickCount,
                           Integer maxUses, Instant expiresAt, Instant createdAt) { }

    private final ShortUrlService service;

    public ShortUrlController(ShortUrlService service) {
        this.service = service;
    }

    @PostMapping("/api/urls")
    public ResponseEntity<LinkInfo> create(@RequestBody CreateRequest request) {
        ShortUrl link = service.shorten(request.url(), request.expiresInDays(), request.maxUses());
        return ResponseEntity.status(HttpStatus.CREATED).body(toInfo(link));
    }

    @GetMapping("/r/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String target = service.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(target).toString())
                .build();
    }

    @GetMapping("/api/urls/{code}/stats")
    public LinkInfo stats(@PathVariable String code) {
        return toInfo(service.stats(code));
    }

    private LinkInfo toInfo(ShortUrl link) {
        return new LinkInfo(link.getCode(), "/r/" + link.getCode(), link.getOriginalUrl(),
                link.getClickCount(), link.getMaxUses(), link.getExpiresAt(), link.getCreatedAt());
    }
}
