package ai.realteeth.imagejobserver.job.service

import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import ai.realteeth.imagejobserver.job.domain.JobStatus
import java.time.Instant
import java.util.UUID

data class CreateJobResult(
    val jobId: UUID,
    val status: JobStatus,
    val deduped: Boolean,
)

data class JobStatusView(
    val jobId: UUID,
    val status: JobStatus,
    val progress: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val attemptCount: Int,
)

data class PendingJobResultView(
    val status: JobStatus,
)

data class SuccessJobResultView(
    val result: String?,
)

data class FailureJobResultView(
    val errorCode: JobErrorCode,
    val message: String,
)

data class JobListItemView(
    val jobId: UUID,
    val status: JobStatus,
    val progress: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class JobListView(
    val page: Int,
    val size: Int,
    val total: Long,
    val items: List<JobListItemView>,
)

sealed interface JobResultView {
    data class Pending(val value: PendingJobResultView) : JobResultView
    data class Success(val value: SuccessJobResultView) : JobResultView
    data class Failure(val value: FailureJobResultView) : JobResultView
}
