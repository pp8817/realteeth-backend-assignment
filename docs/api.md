# API Contract (Our Server)

Base path: `/`

## 1) Create Job
POST `/jobs`

Headers:
- `Idempotency-Key` (optional, preferred)

Request JSON:
```json
{
  "imageUrl": "https://..."
}
```

Response (201 Created, new job):
```json
{
  "jobId": "uuid",
  "status": "RECEIVED",
  "deduped": false
}
```

Response (200 OK, duplicate request):
```json
{
  "jobId": "uuid",
  "status": "RECEIVED|QUEUED|RUNNING|SUCCEEDED|FAILED",
  "deduped": true
}
```

## 2) Get Job Status

GET /jobs/{jobId}

Response (200):
```json
{
  "jobId": "uuid",
  "status": "RECEIVED|QUEUED|RUNNING|SUCCEEDED|FAILED",
  "progress": 0,
  "createdAt": "ISO-8601",
  "updatedAt": "ISO-8601",
  "attemptCount": 0
}
```

Response (404 Not Found):
jobId does not exist.

Progress mapping:
- RECEIVED = 0
- QUEUED = 10
- RUNNING = 50
- SUCCEEDED = 100
- FAILED = 100

## 3) Get Job Result

GET /jobs/{jobId}/result

If not finished:
- 202 Accepted with body:
```json
{
  "status": "RECEIVED|QUEUED|RUNNING"
}
```

If finished and succeeded (200 OK):
```json
{
  "result": "string"
}
```

If finished and failed (200 OK):
```json
{
  "errorCode": "MOCK_WORKER_FAILED|TIMEOUT|RATE_LIMITED|UNAUTHORIZED|BAD_REQUEST|INTERNAL",
  "message": "string"
}
```

## 4) List Jobs

GET /jobs?page=0&size=20&status=RUNNING

Response (200):
```json
{
  "page": 0,
  "size": 20,
  "total": 100,
  "items": [
    {
      "jobId": "uuid",
      "status": "RECEIVED|QUEUED|RUNNING|SUCCEEDED|FAILED",
      "progress": 50,
      "createdAt": "ISO-8601",
      "updatedAt": "ISO-8601"
    }
  ]
}
```
