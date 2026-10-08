package com.demo.agentic.urlshortener;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class ShortUrl {

    @Id
    @GeneratedValue
    private Long id;

    @Column(unique = true, nullable = false, length = 16)
    private String code;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    private Instant createdAt = Instant.now();
    private Instant expiresAt;
    private Integer maxUses;
    private int clickCount;

    protected ShortUrl() { }

    public ShortUrl(String code, String originalUrl) {
        this.code = code;
        this.originalUrl = originalUrl;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public String getCode() { return code; }
    public String getOriginalUrl() { return originalUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
    public int getClickCount() { return clickCount; }
}
