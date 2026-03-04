package ai.realteeth.imagejobserver.job.controller.dto

import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import ai.realteeth.imagejobserver.job.domain.JobStatus
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreateJobRequest(
    @field:NotBlank
    val imageUrl: String,
)

data class CreateJobResponse(
    val jobId: UUID,
    val status: JobStatus,
    val deduped: Boolean,
)

data class JobStatusResponse(
    val jobId: UUID,
    val status: JobStatus,
    val progress: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val attemptCount: Int,
)

data class PendingResultResponse(
    val status: JobStatus,
)

data class SuccessResultResponse(
    val result: String,
)

data class FailureResultResponse(
    val errorCode: JobErrorCode,
    val message: String,
)

data class JobListItemResponse(
    val jobId: UUID,
    val status: JobStatus,
    val progress: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class JobListResponse(
    val page: Int,
    val size: Int,
    val total: Long,
    val items: List<JobListItemResponse>,
)
