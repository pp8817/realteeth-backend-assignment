package ai.realteeth.imagejobserver.worker.repository

import java.time.Instant
import java.util.UUID

interface WorkerClaimRepository {

    fun claimQueuedJobs(workerId: String, leaseSeconds: Int, batchSize: Int): List<UUID>

    fun requeueStaleRunningJobs(batchSize: Int, maxAttempts: Int): List<UUID>

    fun findStaleRunningJobsAtOrOverMaxAttempts(batchSize: Int, maxAttempts: Int): List<UUID>

    fun extendLease(jobId: UUID, workerId: String, leaseSeconds: Int): Boolean

    fun now(): Instant = Instant.now()
}
