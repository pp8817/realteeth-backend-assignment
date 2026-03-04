# Image Job Server

비동기 이미지 처리 오케스트레이션 서버(Kotlin + Spring Boot)입니다.  
클라이언트 요청을 즉시 수락한 뒤, 백그라운드 워커가 외부 Mock Worker에 작업을 위임하고 상태/결과를 DB에 저장합니다.

## 1. 상태 모델 설계 의도

내부 상태 모델:

`RECEIVED -> QUEUED -> RUNNING -> SUCCEEDED/FAILED`

설계 이유:
- 비동기 처리: API는 작업 생성 후 빠르게 응답하고, 실제 처리는 워커가 수행합니다.
- 요청 수신과 실행 분리: 클라이언트 요청 처리 경로와 외부 워커 호출 경로를 분리해 지연 전파를 줄입니다.
- 불변 최종 상태: `SUCCEEDED`, `FAILED`를 final state로 두어 완료 이후 상태 변조를 방지합니다.

진행률 매핑:
- `RECEIVED = 0`
- `QUEUED = 10`
- `RUNNING = 50`
- `SUCCEEDED = 100`
- `FAILED = 100`

허용 전이:
- `RECEIVED -> QUEUED`
- `QUEUED -> RUNNING`
- `RUNNING -> SUCCEEDED`
- `RUNNING -> FAILED`
- `RUNNING -> QUEUED` (lease 만료 복구 시에만 허용)

## 2. 실패 처리 전략

재시도 가능(Retryable):
- 네트워크 오류
- 타임아웃
- Mock Worker `5xx`
- Mock Worker `429`

재시도 불가(Non-retryable):
- `400`
- `401`
- 검증 오류(`422`)

재시도 정책:
- 지수 백오프(예: `0.5s -> 1s -> 2s`)
- 최대 시도 횟수 초과 시 `FAILED`로 종료
- 최대 시도 횟수는 환경변수 `APP_WORKER_MAX_ATTEMPTS`로 설정(기본 3)
- heartbeat(`extendLease`) 실패 시 현재 실행을 즉시 중단하고 lock 만료 후 stale 복구 경로로 이관
- `APP_WORKER_MAX_PROCESSING_SECONDS`(기본 1800초) 초과 시 무한 `PROCESSING` 보호를 위해 현재 실행을 중단하고 stale 복구 경로로 이관

## 3. 동시 요청 처리 전략

중복 요청 방지 전략:
- `Idempotency-Key` 헤더를 우선 사용
- 헤더가 없으면 `sha256(imageUrl)` fingerprint 사용
- DB 유니크 제약으로 최종 중복 차단
  - `UNIQUE(idempotency_key) WHERE idempotency_key IS NOT NULL`
  - `UNIQUE(fingerprint) WHERE fingerprint IS NOT NULL`
- 삽입 경합은 `ON CONFLICT` 기반 upsert/조회 패턴으로 기존 `jobId`를 반환하도록 설계

레이스 컨디션 대응:
- 애플리케이션 레벨 체크만으로는 동시성 경합을 완전히 막을 수 없으므로 DB 제약을 최종 방어선으로 사용합니다.

## 4. 트래픽 병목과 대응

예상 병목:
- 외부 워커 응답 지연(수 초~수십 초)
- 외부 워커 rate limiting(`429`)
- DB claim 구간 경쟁

완화 전략:
- 워커 동시 실행 수 제한(스레드풀/벌크헤드)
- `FOR UPDATE SKIP LOCKED` 기반 claim
- 재시도 + 백오프
- claim/requeue 배치 크기 제한(`batch_size`)
- 상태 폴링 간격 분리(`APP_WORKER_STATUS_POLL_INTERVAL_MS`)로 외부 조회 부하 제어
- stale + `attempt_count >= max_attempts` 작업은 `FAILED(TIMEOUT)`으로 종결하여 무한 재큐잉 방지

## 5. 외부 시스템 연동 방식

Mock Worker:
- Base URL: `https://dev.realteeth.ai/mock`
- `POST /mock/process`
- `GET /mock/process/{jobId}`

연동 모델:
- 내부 job이 `RUNNING` 상태가 되면 외부 작업 시작 요청(`POST /mock/process`)
- 외부 `jobId`를 저장하고 주기적으로 `GET /mock/process/{jobId}` 폴링
- 상태 매핑:
  - `PROCESSING -> RUNNING`
  - `COMPLETED -> SUCCEEDED`
  - `FAILED -> FAILED`
- `APP_MOCK_API_KEY` 미설정 시 `APP_MOCK_AUTO_ISSUE_ENABLED=true`이면 `/mock/auth/issue-key`를 지연 호출해 API Key를 자동 발급/캐시

## 6. 처리 보장 모델

- 외부 호출 관점: At-least-once (재시도/재시작으로 외부 호출 중복 가능)
- 내부 결과 관점: final state 불변성과 idempotent 생성 전략으로 사실상 exactly-once 결과 의미를 지향

## 7. 서버 재시작 시 동작과 정합성

재시작 시:
- 작업 상태는 DB에 남아 유실되지 않음
- 워커가 재기동 후 `QUEUED`/stale `RUNNING` 작업을 다시 처리
- `external_job_id`가 있으면 외부 상태를 재폴링해 내부 상태와 동기화

정합성 리스크 지점:
- 외부 시작 호출 직후 프로세스 크래시(외부 `jobId` 저장 전)
- 완료 응답 수신 직후 크래시(결과 저장 전)

완화:
- 트랜잭션 기반 상태/결과 저장
- lease 만료 복구
- final state 불변 규칙
- stale + max attempts 초과 시 `FAILED(TIMEOUT)` 종결로 정체 작업 정리

## 8. 실행 방법

사전 요구사항:
- Docker
- Docker Compose

실행:

```bash
cd docker
docker compose up --build
```

종료:

```bash
docker compose down
```

포트:
- App: `8080`
- Postgres: `5432`

주요 환경변수 (`docker/docker-compose.yml`):
- `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/image_jobs`
- `SPRING_DATASOURCE_USERNAME=image_jobs`
- `SPRING_DATASOURCE_PASSWORD=image_jobs`
- `APP_WORKER_ENABLED=true`
- `APP_WORKER_ID=worker-1`
- `APP_WORKER_LEASE_SECONDS=30`
- `APP_WORKER_BATCH_SIZE=5`
- `APP_WORKER_MAX_ATTEMPTS=3`
- `APP_WORKER_STATUS_POLL_INTERVAL_MS=2000`
- `APP_WORKER_MAX_PROCESSING_SECONDS=1800`
- `APP_MOCK_BASE_URL=https://dev.realteeth.ai/mock`
- `APP_MOCK_API_KEY=` (비우면 자동 발급 사용 가능)
- `APP_MOCK_AUTO_ISSUE_ENABLED=true`
- `APP_MOCK_CANDIDATE_NAME=박상민`
- `APP_MOCK_CANDIDATE_EMAIL=pp8817@naver.com`

API Key 발급 예시(Mock Worker):

```bash
curl -X POST "https://dev.realteeth.ai/mock/auth/issue-key" \
  -H "Content-Type: application/json" \
  -d '{"candidateName":"YOUR_NAME","email":"YOUR_EMAIL"}'
```

## 9. 검증 방법 및 결과 요약

자동 테스트:
- `./gradlew test` 통과
- HTTP 레벨 동시성 검증: `DuplicateRequestHttpRaceIntegrationTest`
- Postgres(Testcontainers) claim/lease E2E 검증: `WorkerClaimLeasePostgresIntegrationTest`
- heartbeat 안전 포기/최대 실행 시간 검증: `WorkerExecutionIntegrationTest`
- API Key 자동 발급/재사용 검증: `MockWorkerAutoIssueKeyIntegrationTest`

컨테이너 스모크:
- `cd docker && docker compose up --build -d`
- `GET /jobs` 응답 200 확인
- `POST /jobs` 생성 후 `GET /jobs/{jobId}` 조회 200 확인
- `cd docker && docker compose down -v`
