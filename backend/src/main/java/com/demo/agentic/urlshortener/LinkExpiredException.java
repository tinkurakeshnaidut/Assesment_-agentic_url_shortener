package com.demo.agentic.urlshortener;

public class LinkExpiredException extends RuntimeException {

    public LinkExpiredException(String message) {
        super(message);
    }
}
