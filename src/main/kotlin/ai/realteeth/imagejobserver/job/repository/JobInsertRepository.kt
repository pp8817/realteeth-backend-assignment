package ai.realteeth.imagejobserver.job.repository

import ai.realteeth.imagejobserver.job.domain.JobStatus
import java.util.UUID

data class InsertOrGetJobResult(
    val jobId: UUID,
    val status: JobStatus,
    val created: Boolean,
)

interface JobInsertRepository {

    fun insertOrGet(
        jobId: UUID,
        imageUrl: String,
        idempotencyKey: String?,
        fingerprint: String,
    ): InsertOrGetJobResult
}
