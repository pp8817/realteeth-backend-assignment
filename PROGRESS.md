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

- [x] Create Spring Boot project (Kotlin + Gradle)
- [x] Set base package `ai.realteeth.imagejobserver`
- [x] Add dependencies
    - spring-boot-starter-web
    - spring-boot-starter-validation
    - spring-boot-starter-data-jpa
    - postgresql
    - test dependencies
- [x] Create project package structure

src/main/kotlin/ai/realteeth/imagejobserver
```
├ job
├ worker
├ client.mockworker
└ global
```
- [x] Commit initial project structure

---

# Phase 1 — Database Layer

Goal: Implement persistence according to db/schema.sql.

Tasks:

- [x] Apply `db/schema.sql`
- [x] Create Job entity
- [x] Create JobResult entity
- [x] Implement JobRepository
- [x] Implement JobResultRepository
- [x] Validate unique constraints
    - idempotency_key
    - fingerprint
- [x] Add entity tests

Completion criteria:

- Job row can be inserted
- Unique constraints prevent duplicates

---

# Phase 2 — REST API

Goal: Implement server endpoints defined in docs/api.md.

Endpoints:

- [x] POST `/jobs`
- [x] GET `/jobs/{jobId}`
- [x] GET `/jobs/{jobId}/result`
- [x] GET `/jobs`

Tasks:

- [x] Implement JobController
- [x] Implement JobService
- [x] Implement request validation
- [x] Implement idempotency handling
- [x] Implement fingerprint fallback

Tests:

- [x] API integration test for job creation
- [x] Duplicate request returns same jobId

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

- [x] Implement MockWorkerClient
- [x] Implement start processing request
- [x] Implement job status polling
- [x] Implement timeout configuration
- [x] Implement response mapping

Tests:

- [x] Mock worker success scenario
- [x] Mock worker failure scenario

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

- [x] Implement WorkerScheduler
- [x] Implement job claim logic using `db/claim.sql`
- [x] Implement lease mechanism
- [x] Implement external jobId storage
- [x] Implement polling loop
- [x] Implement status mapping
- [x] Implement lease extension

Completion criteria:

- Worker picks queued jobs
- Job transitions RUNNING → SUCCEEDED/FAILED

---

# Phase 5 — Failure Handling

Goal: Implement retry and failure mapping.

Tasks:

- [x] Implement retry policy
- [x] Handle HTTP 429
- [x] Handle timeouts
- [x] Handle HTTP 5xx
- [x] Stop retry after max attempts

Tests:

- [x] retry test
- [x] max attempts failure test

Completion criteria:

- Retryable errors retry
- Non-retryable errors fail immediately

---

# Phase 6 — Restart Recovery

Goal: Ensure job consistency after server restart.

Tasks:

- [x] Detect stale RUNNING jobs
- [x] Requeue expired leases
- [x] Poll external job status if external_job_id exists
- [x] Ensure final states remain immutable

Completion criteria:

- Restart does not lose jobs
- Worker resumes processing

---

# Phase 7 — Concurrency

Goal: Prevent duplicate jobs under concurrent requests.

Tasks:

- [x] Implement ON CONFLICT logic
- [x] Implement concurrency test
- [x] Simulate multiple POST /jobs requests

Completion criteria:

- Only one job row created for identical requests

---

# Phase 8 — Containerization

Goal: Ensure evaluator can run system locally.

Tasks:

- [x] Create Dockerfile
- [x] Implement docker-compose
- [x] Configure Postgres container
- [x] Configure application container
- [x] Add run instructions to README

Completion criteria:

System runs with:
- docker compose up

---

# Phase 9 — Testing

Goal: Provide reliable test coverage.

Tests required:

Unit Tests

- [x] state transition validation
- [x] idempotency logic

Integration Tests

- [x] worker success
- [x] worker failure

Concurrency Tests

- [x] duplicate request race test

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

Last updated: 2026-03-04 15:47 UTC

Current Phase: Phase 9 (Testing) - Completed

Completed:
- Documentation consistency fixes (API contract, retry configurability, stale requeue batching, package naming consistency)
- README baseline for assignment evaluation
- Commit message convention documented (`{type}: {message}`, no branch-name prefix)
- Phase 0, 1, 2, 3, 4, 5, 6, 8, 9 주요 구현 및 테스트 반영
- Phase 7 ON CONFLICT 기반 중복 삽입 처리 반영

In Progress:
- None

---

# Resume Instructions

If implementation stops unexpectedly:

1. Read AGENTS.md
2. Read PLAYBOOK.md
3. Read PROGRESS.md
4. Resume from first unchecked task
