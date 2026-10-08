package com.demo.agentic.model;

import java.time.Instant;

public record AuditEvent(Instant time, String taskId, String type, String detail) { }
