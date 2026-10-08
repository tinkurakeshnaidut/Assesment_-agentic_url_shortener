Agentic Software Engineering System – URL Shortener
Overview

This project implements both parts of the assignment:

A working URL Shortener
An Agentic Software Engineering System that takes a software requirement in plain English and guides it through analysis, design, implementation planning, testing, security review, documentation, and release readiness.

The system is designed around controlled autonomy: agents analyze and propose actions, while humans approve risky or important steps before changes are accepted.

What I Built
1. URL Shortener

The application provides a functional URL shortening service.

Features include:

Convert a long URL into a short link
URL validation
Link expiration after a configurable number of days
Maximum-use limits for generated links
Usage tracking
Rate limiting for link creation based on IP address
Appropriate error handling for invalid or expired links

For example, a link configured with a maximum of 2 uses will return HTTP 410 (Gone) when accessed for the third time.

2. Agentic Software Engineering System

The agentic system accepts a software requirement in plain English and processes it through a sequence of specialized tasks.

The workflow covers:

Requirement analysis
Task decomposition
Codebase analysis
Design
Implementation planning
Testing
Security review
Documentation
Release readiness
Human approval gates

The workflow is represented as a dependency graph, allowing independent tasks such as testing, security review, and documentation to run in parallel before converging at the release stage.

How It Meets the Requirements
Requirement Understanding

The system analyzes natural-language requirements to identify:

Requested features
Constraints
Assumptions
Open questions
Ambiguous requirements

For example, vague requirements such as:

"Make it faster, more secure, and scalable."

are identified as ambiguous. Terms such as "faster", "secure", and "scalable" are converted into explicit questions, along with assumptions where necessary.

Task Decomposition

The system decomposes requirements into a dependency graph.

Tasks can run independently when there are no dependencies between them. For example:

Requirement
    |
    v
Analysis
    |
    v
Design
    |
    +------------+-------------+
    |            |             |
    v            v             v
 Testing   Security Review  Documentation
    |            |             |
    +------------+-------------+
                 |
                 v
          Release Checklist


This allows independent activities to be executed in parallel and joined before release.

Codebase Reasoning

For brownfield scenarios, the system analyzes the existing codebase rather than assuming the feature already exists.

The agent identifies:

What functionality already exists
What functionality is missing
Which files are relevant
Which files would need to be changed
Potential implementation considerations

For example, when given:

"Add custom alias support."

the system determines that custom alias support is not currently available and identifies the relevant files that would need modification.

Orchestration and Recovery

The orchestration layer provides several controls:

Entry and exit gates
Human approval gates
Up to 3 attempts per task
Fallback handling
Rollback handling
Safe-stop behavior
Dependency-aware execution
Requirement-change propagation

If a task fails repeatedly, the system attempts its fallback strategy and can eventually roll back and safely stop instead of continuing in an unsafe state.

When a requirement changes, only the tasks that depend on the changed requirement are re-run.

Guardrails

Before outputs are accepted, the system performs basic safety and quality checks.

The guardrails scan for patterns such as:

Hard-coded passwords or secrets
SQL queries constructed through unsafe string concatenation
System.out usage

These checks help prevent common implementation and security issues from being accepted into the workflow.

Traceability and Metrics

The system maintains an audit trail of the workflow.

It records:

Task execution
Task retries
Human approvals
Task outcomes
Rollbacks
Recovery actions

The system also tracks metrics such as:

Success rate
Number of retries
Number of rollbacks
Recovery time
Total execution time

This provides visibility into how the agentic workflow performed.

Controlled Autonomy

The system follows a human-in-the-loop approach.

Agents can:

Analyze requirements
Propose designs
Identify changes
Generate recommendations
Perform reviews

However, agents do not automatically write changes into the codebase.

Human approval is required for the appropriate steps, especially those involving potentially risky changes.

Demonstration Scenarios

The application includes three scenarios demonstrating different aspects of the system.

1. Greenfield Scenario

Goal: Build the URL shortener from scratch.

The system processes the requirement through the complete workflow and eventually produces a GO decision on the release checklist.

2. Brownfield Scenario

Requirement:

"Add custom alias support."

The system analyzes the existing URL shortener implementation and determines:

Custom aliases are currently missing
What
