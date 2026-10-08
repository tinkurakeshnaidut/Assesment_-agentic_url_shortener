package com.demo.agentic.urlshortener;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final int maxPerMinute;
    private final Map<String, Integer> hitsByIp = new HashMap<>();
    private long windowStart = System.currentTimeMillis();

    public RateLimitFilter(@Value("${shortener.rate-limit-per-minute:30}") int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean isCreate = request.getMethod().equals("POST") && request.getRequestURI().equals("/api/urls");
        if (isCreate && !allow(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"too many requests, try again in a minute\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allow(String ip) {
        long now = System.currentTimeMillis();
        if (now - windowStart > 60_000) {
            hitsByIp.clear();
            windowStart = now;
        }
        int hits = hitsByIp.merge(ip, 1, Integer::sum);
        return hits <= maxPerMinute;
    }
}
