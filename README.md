# Agentic Software Engineering System - URL Shortener

## What I built

The assignment has two parts, so I built both. First, a working URL shortener: paste a long URL and get a short link. Links can expire after some days or after a maximum number of uses. Every use is counted, bad URLs are rejected and link creation is rate limited per IP. Second, an agentic system: you type a requirement in plain English and a set of small agents carries it through analysis, design, code, tests, risk review, docs and a release checklist, while a human approves the risky steps.

## How it meets the requirements

- **Requirement understanding:** finds the features, flags vague words like "faster" or "secure" as open questions and writes down the assumptions it made.
- **Task decomposition:** the work is a dependency graph. Testing, security review and documentation run in parallel and join before release.
- **Codebase reasoning:** for existing code, an agent reads the real shortener source and reports what is already there, what is missing and which files must change.
- **Orchestration:** entry and exit gates, human approvals, up to 3 attempts per task, then a fallback, then rollback and a safe stop. Changing the requirement re-runs only the tasks that depend on it.
- **Guardrails:** every output is scanned for hard-coded passwords, SQL built by string joining and System.out before it is accepted.
- **Traceability:** an audit trail logs every step, retry and approval. Metrics show success rate, retries, rollbacks, recovery time and total time.
- **Controlled autonomy:** agents propose, humans approve. Nothing is written into the code automatically.

## The three scenarios

- **Greenfield:** build the shortener from scratch. It ends with a GO on the release checklist.
- **Brownfield:** "add custom alias support". The agent finds it missing and lists the files to change.
- **Ambiguous:** "make it faster, more secure, scalable". Each vague word becomes a question with an assumption.

## How to set it up

You need JDK 17+, Maven and Node 18+.

1. Backend (run it from the backend folder): `cd backend`, then `mvn spring-boot:run`.
2. UI in a second terminal: `cd ui`, `npm install`, `npm start`.
3. Open `http://localhost:4200`.
4. Click a scenario, press Start run and approve when a task shows `WAITING_APPROVAL`.
5. To test the shortener, use the "Try the shortener" box: set Max uses to 2 and open the link three times. The third one gives 410.
6. Run the tests with `mvn test` in the backend folder.

## Limitations

The agents are rule based, not AI model calls and code is build successfully and compiled. Runs and links are kept in memory and the API has no login. I chose these to keep the prototype simple and predictable.
