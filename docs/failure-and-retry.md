# Failure & Retry Policy

## Retryable Errors (will retry)
- Network errors (connection reset, DNS, etc.)
- Read timeout / connect timeout
- HTTP 5xx from Mock Worker
- HTTP 429 Too Many Requests

Retry Strategy:
- Exponential backoff (e.g., 0.5s, 1s, 2s)
- Max attempts per job: configurable (default 3)
- Environment variable: `APP_WORKER_MAX_ATTEMPTS`
- Worker claim/requeue SQL uses `:max_attempts` parameter from configuration
- On each retry, update attempt_count
- If attempts exceed max, mark FAILED

## Non-retryable Errors (fail fast)
- HTTP 401 Unauthorized (bad API key)
- HTTP 400 Bad Request (invalid payload)
- Validation errors (422)

## Error Mapping (for our API)
- 401 -> errorCode=UNAUTHORIZED
- 429 -> errorCode=RATE_LIMITED
- 5xx -> errorCode=INTERNAL (retryable until exhausted)
- timeout -> errorCode=TIMEOUT (retryable until exhausted)
- 400/422 -> errorCode=BAD_REQUEST

## Job Finalization Rules
- SUCCEEDED and FAILED are final states.
- Once a job enters final state, never transition out.
- result is saved only when transitioning to SUCCEEDED
- error is saved only when transitioning to FAILED
