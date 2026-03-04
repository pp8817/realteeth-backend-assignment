package ai.realteeth.imagejobserver.job.domain

enum class JobErrorCode {
    MOCK_WORKER_FAILED,
    TIMEOUT,
    RATE_LIMITED,
    UNAUTHORIZED,
    BAD_REQUEST,
    INTERNAL,
}
