# PLAYBOOK.md

## Goal
Implement the entire assignment end-to-end with high design completeness using the repository documents as source of truth.

## Source of Truth
- Design: `docs/architecture.md`
- API contract: `docs/api.md`
- Failure/Retry: `docs/failure-and-retry.md`
- DB DDL: `db/schema.sql`
- Worker claim SQL: `db/claim.sql`

Codex must not invent requirements outside these docs.

---

## Phase 0 — Bootstrap
1) Create Spring Boot project (Kotlin, Gradle).
2) Set base package: `ai.realteeth.imagejobserver`.
3) Add dependencies:
- spring-boot-starter-web
- spring-boot-starter-validation
- spring-boot-starter-data-jpa
- postgresql driver
- test: spring-boot-starter-test
- okhttp or spring WebClient
- resilience4j (retry, bulkhead, circuitbreaker) OR implement minimal retry manually (documented)

---

## Phase 1 — Database & Entities
1) Apply `db/schema.sql` as DDL.
2) Create JPA entities for:
- `job` table
- `job_result` table
3) Implement repositories:
- JobRepository
- JobResultRepository

Validation:
- Job can be inserted
- Uniqueness constraints enforce dedup

---

## Phase 2 — REST API (no worker yet)
Implement endpoints from `docs/api.md`:
- POST /jobs
- GET /jobs/{jobId}
- GET /jobs/{jobId}/result
- GET /jobs

Validation:
- POST persists job with status RECEIVED then immediately QUEUED
- Duplicate request returns existing jobId

---

## Phase 3 — Mock Worker Client
Implement client in `client.mockworker`:
- Issue key endpoint support (optional runtime helper, not required for tests)
- Start processing: POST /mock/process
- Poll status: GET /mock/process/{job_id}

Add:
- timeouts
- response mapping
- error mapping per `docs/failure-and-retry.md`

Validation:
- Can call mocked endpoints in tests.

---

## Phase 4 — Worker (claim/lease/polling)
Implement background worker:
1) Claim jobs using SQL in `db/claim.sql`
2) For each claimed job:
    - if external_job_id is null: call POST /mock/process, save external_job_id
    - poll GET /mock/process/{external_job_id}
    - if PROCESSING: extend lease and keep RUNNING
    - if COMPLETED: save result, set SUCCEEDED
    - if FAILED: save error, set FAILED
3) Retry on retryable errors; stop after max attempts, mark FAILED.

Validation:
- A queued job eventually becomes SUCCEEDED/FAILED when mock returns.
- Stale RUNNING jobs are re-queued.

---

## Phase 5 — Tests
Must include:
1) Unit: state transition guard
2) Unit: idempotency + fingerprint selection
3) Integration: worker succeeds path (mock COMPLETED)
4) Integration: worker failure path (mock FAILED)
5) Concurrency: multi-thread POST /jobs same payload -> single job row

---

## Phase 6 — Containerization
1) docker-compose with:
- Postgres
- App
2) Environment variables for DB
3) Provide run steps in README

---

## Phase 10 — Documentation
Goal: Produce README required for the assignment evaluation.

Tasks:
- Write README.md explaining the architecture and design decisions.
- README.md must be written in Korean.
- Include the following sections required by the assignment:

### 1. State Model Design
Explain why the internal state model is:
RECEIVED -> QUEUED -> RUNNING -> SUCCEEDED/FAILED

Explain:
- asynchronous processing
- separation of client request and external worker execution
- immutable final states

### 2. Failure Handling Strategy
Explain retry strategy and error classification:

Retryable:
- network error
- timeout
- 5xx
- 429

Non-retryable:
- 400
- 401
- validation errors

Explain exponential backoff.

### 3. Concurrent Request Handling
Explain duplicate request strategy:
- Idempotency-Key
- fingerprint hashing
- DB unique constraints
- ON CONFLICT logic

Explain race condition protection.

### 4. Traffic Bottlenecks
Explain possible bottlenecks:
- external worker latency
- rate limiting (429)
- database contention

Explain mitigation:
- worker concurrency limits
- SKIP LOCKED
- retry with backoff

### 5. External System Integration
Explain Mock Worker integration:
- POST /mock/process
- GET /mock/process/{jobId}
- polling model
- external <-> internal state mapping

### 6. Running the System
Explain:
`docker compose up`

Explain ports and environment variables.

Completion criteria:
README.md must contain all sections required in the assignment description.

---

## Done Criteria
- `./gradlew test` passes
- `docker compose up` runs
- Endpoints respond as documented
- README explains required design points (from assignment)
