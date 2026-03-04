# Architecture

## 1. Problem
Image processing is heavy and has variable latency. External service (Mock Worker) can be slow/unreliable. Client must be able to:
- create jobs
- track progress/status
- fetch results
- list jobs

## 2. High-level Design
We implement an asynchronous Job orchestration server:
- REST API stores Job and returns internal jobId quickly
- background worker claims jobs from DB and orchestrates Mock Worker
- status/result are stored in DB for observability and restart recovery

## 3. Internal State Model
States:
- RECEIVED: created and persisted
- QUEUED: waiting for worker
- RUNNING: claimed by worker and processing/polling Mock Worker
- SUCCEEDED: completed successfully (final)
- FAILED: completed with failure (final)

Allowed transitions:
- RECEIVED -> QUEUED
- QUEUED -> RUNNING
- RUNNING -> SUCCEEDED
- RUNNING -> FAILED
- RUNNING -> QUEUED (only stale lease recovery)

Final states are immutable: SUCCEEDED, FAILED.

## 4. External (Mock Worker) Model
OpenAPI:
- POST /mock/process returns: { jobId, status=PROCESSING }
- GET /mock/process/{job_id} returns: { jobId, status=PROCESSING|COMPLETED|FAILED, result }

Mapping:
- PROCESSING -> RUNNING
- COMPLETED  -> SUCCEEDED
- FAILED     -> FAILED

## 5. Duplicate Requests (Idempotency)
We support duplicates:
- Preferred: `Idempotency-Key` header (client provided UUID)
- Fallback: fingerprint = sha256(imageUrl)

DB enforces uniqueness:
- UNIQUE(idempotency_key) where not null
- UNIQUE(fingerprint)

Behavior:
- If duplicate detected, return existing jobId (no new job created).

## 6. Worker Claim & Lease
We use DB-based queue with SKIP LOCKED:
- Worker selects QUEUED jobs and locks rows to claim them.
- Claim sets:
    - status=RUNNING
    - locked_by=workerId
    - locked_until=now + leaseDuration
- Worker periodically extends lease while processing.
- Worker polls Mock Worker in a loop while status is PROCESSING.
- If lease heartbeat fails (DB error or lease lost), worker safely abandons current execution and lets stale recovery re-claim the job.
- If processing exceeds `APP_WORKER_MAX_PROCESSING_SECONDS` (default 1800), worker also abandons and defers to stale recovery.

Stale recovery:
- RUNNING jobs with locked_until < now are considered stale.
- Worker re-queues them:
    - status=QUEUED
    - attempt_count += 1
    - only while attempt_count < max_attempts (configured by `APP_WORKER_MAX_ATTEMPTS`)
    - clear lease fields or reset lease
- Stale RUNNING jobs with `attempt_count >= max_attempts` are finalized as `FAILED(TIMEOUT)` (no more requeue).

## 7. Restart Behavior
On server restart:
- jobs remain in DB
- worker resumes polling
- if external_job_id exists, worker polls GET /mock/process/{external_job_id} to resync
- stale RUNNING jobs get re-queued and retried
- stale RUNNING jobs at/over max attempts are completed as FAILED(TIMEOUT)

Data integrity risk points:
- crash after POST /mock/process but before saving external_job_id
- crash after receiving COMPLETED but before persisting result
  Mitigation:
- transactional updates for status/result
- retries + final state immutability
- lease-based recovery

## 8. Guarantee Model
Processing guarantee:
- At-least-once processing (external calls may repeat due to retries/restart)
  We provide effective exactly-once *result* semantics by:
- idempotent job creation
- final states immutable
- result persisted once when reaching final state

## 9. Scalability & Bottlenecks
Bottlenecks:
- external latency (seconds to tens of seconds)
- rate limiting (429)
- DB contention on claim queries
- potentially unbounded PROCESSING duration

Mitigations:
- worker concurrency limit (thread pool / bulkhead)
- exponential backoff retries
- FOR UPDATE SKIP LOCKED for claim
- paging LIMIT for claim batch sizes
- max processing timeout + stale recovery handoff

## 10. Mock API Key Strategy
- If `APP_MOCK_API_KEY` is configured, worker uses it directly.
- If not configured and `APP_MOCK_AUTO_ISSUE_ENABLED=true`, worker lazily calls `POST /mock/auth/issue-key` on first need.
- Issued key is cached in memory and reused for subsequent `/mock/process` calls.
