# Implementation Progress

This document tracks the current implementation state of the Image Job Server.

Purpose:
- Allow Codex to resume implementation if the session or thread changes.
- Ensure no phase in PLAYBOOK.md is skipped.
- Provide a clear checkpoint system for long-running implementation.

Rules for Codex:
1. Always read this file before starting implementation.
2. Resume from the first unchecked item.
3. After completing a task, mark it as completed.
4. Do not skip phases unless explicitly instructed.
5. If unsure about implementation details, consult:
    - docs/architecture.md
    - docs/api.md
    - docs/failure-and-retry.md
    - db/schema.sql
    - db/claim.sql

---

# Phase 0 — Project Bootstrap

Goal: Create the initial Spring Boot project and repository structure.

Tasks:

- [ ] Create Spring Boot project (Kotlin + Gradle)
- [ ] Set base package `ai.realteeth.imagejobserver`
- [ ] Add dependencies
    - spring-boot-starter-web
    - spring-boot-starter-validation
    - spring-boot-starter-data-jpa
    - postgresql
    - test dependencies
- [ ] Create project package structure

src/main/kotlin/ai/realteeth/imagejobserver
```
├ job
├ worker
├ client.mockworker
└ global
```
- [ ] Commit initial project structure

---

# Phase 1 — Database Layer

Goal: Implement persistence according to db/schema.sql.

Tasks:

- [ ] Apply `db/schema.sql`
- [ ] Create Job entity
- [ ] Create JobResult entity
- [ ] Implement JobRepository
- [ ] Implement JobResultRepository
- [ ] Validate unique constraints
    - idempotency_key
    - fingerprint
- [ ] Add entity tests

Completion criteria:

- Job row can be inserted
- Unique constraints prevent duplicates

---

# Phase 2 — REST API

Goal: Implement server endpoints defined in docs/api.md.

Endpoints:

- [ ] POST `/jobs`
- [ ] GET `/jobs/{jobId}`
- [ ] GET `/jobs/{jobId}/result`
- [ ] GET `/jobs`

Tasks:

- [ ] Implement JobController
- [ ] Implement JobService
- [ ] Implement request validation
- [ ] Implement idempotency handling
- [ ] Implement fingerprint fallback

Tests:

- [ ] API integration test for job creation
- [ ] Duplicate request returns same jobId

Completion criteria:

- Job creation persists to DB
- Duplicate requests do not create new jobs

---

# Phase 3 — Mock Worker Client

Goal: Implement external service integration.

Mock Worker endpoints:
- POST /mock/auth/issue-key
- POST /mock/process
- GET  /mock/process/{job_id}

Tasks:

- [ ] Implement MockWorkerClient
- [ ] Implement start processing request
- [ ] Implement job status polling
- [ ] Implement timeout configuration
- [ ] Implement response mapping

Tests:

- [ ] Mock worker success scenario
- [ ] Mock worker failure scenario

Completion criteria:

- Client correctly starts job
- Client can poll job status

---

# Phase 4 — Worker Execution

Goal: Implement asynchronous worker processing.

Worker responsibilities:

- claim jobs
- call external worker
- poll status
- persist results

Tasks:

- [ ] Implement WorkerScheduler
- [ ] Implement job claim logic using `db/claim.sql`
- [ ] Implement lease mechanism
- [ ] Implement external jobId storage
- [ ] Implement polling loop
- [ ] Implement status mapping
- [ ] Implement lease extension

Completion criteria:

- Worker picks queued jobs
- Job transitions RUNNING → SUCCEEDED/FAILED

---

# Phase 5 — Failure Handling

Goal: Implement retry and failure mapping.

Tasks:

- [ ] Implement retry policy
- [ ] Handle HTTP 429
- [ ] Handle timeouts
- [ ] Handle HTTP 5xx
- [ ] Stop retry after max attempts

Tests:

- [ ] retry test
- [ ] max attempts failure test

Completion criteria:

- Retryable errors retry
- Non-retryable errors fail immediately

---

# Phase 6 — Restart Recovery

Goal: Ensure job consistency after server restart.

Tasks:

- [ ] Detect stale RUNNING jobs
- [ ] Requeue expired leases
- [ ] Poll external job status if external_job_id exists
- [ ] Ensure final states remain immutable

Completion criteria:

- Restart does not lose jobs
- Worker resumes processing

---

# Phase 7 — Concurrency

Goal: Prevent duplicate jobs under concurrent requests.

Tasks:

- [ ] Implement ON CONFLICT logic
- [ ] Implement concurrency test
- [ ] Simulate multiple POST /jobs requests

Completion criteria:

- Only one job row created for identical requests

---

# Phase 8 — Containerization

Goal: Ensure evaluator can run system locally.

Tasks:

- [ ] Create Dockerfile
- [ ] Implement docker-compose
- [ ] Configure Postgres container
- [ ] Configure application container
- [x] Add run instructions to README

Completion criteria:

System runs with:
- docker compose up

---

# Phase 9 — Testing

Goal: Provide reliable test coverage.

Tests required:

Unit Tests

- [ ] state transition validation
- [ ] idempotency logic

Integration Tests

- [ ] worker success
- [ ] worker failure

Concurrency Tests

- [ ] duplicate request race test

Completion criteria:
```bash
./gradlew test
```
passes.

---

# Phase 10 — Documentation

Goal: Provide complete README for evaluation.

Tasks:

- [x] Explain architecture
- [x] Explain state model
- [x] Explain idempotency strategy
- [x] Explain retry strategy
- [x] Explain restart recovery
- [x] Explain bottlenecks
- [x] Provide run instructions

Completion criteria:

README includes all assignment design explanations.

---

# Current Status

Update this section when work progresses.

Last updated: 2026-03-04 14:56 UTC

Current Phase: Phase 10 (Documentation)

Completed:
- Documentation consistency fixes (API contract, retry configurability, stale requeue batching, package naming consistency)
- README baseline for assignment evaluation

In Progress:
- Implementation phases (Phase 0-9)

---

# Resume Instructions

If implementation stops unexpectedly:

1. Read AGENTS.md
2. Read PLAYBOOK.md
3. Read PROGRESS.md
4. Resume from first unchecked task
