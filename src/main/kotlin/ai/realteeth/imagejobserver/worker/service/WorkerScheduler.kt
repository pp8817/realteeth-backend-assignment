package ai.realteeth.imagejobserver.worker.service

import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import ai.realteeth.imagejobserver.job.service.JobService
import ai.realteeth.imagejobserver.worker.config.WorkerProperties
import ai.realteeth.imagejobserver.worker.repository.WorkerClaimRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import kotlin.math.min

@Component
class WorkerScheduler(
    private val workerProperties: WorkerProperties,
    private val workerClaimRepository: WorkerClaimRepository,
    private val jobService: JobService,
    private val workerExecutionService: WorkerExecutionService,
    @Qualifier("workerTaskExecutor") private val workerTaskExecutor: ThreadPoolTaskExecutor,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.worker.poll-interval-ms:1000}")
    fun pollAndDispatch() {
        if (!workerProperties.enabled) {
            return
        }

        val exhausted = workerClaimRepository.findStaleRunningJobsAtOrOverMaxAttempts(
            batchSize = workerProperties.batchSize,
            maxAttempts = workerProperties.maxAttempts,
        )
        exhausted.forEach { jobId ->
            runCatching {
                jobService.completeFailed(
                    jobId = jobId,
                    errorCode = JobErrorCode.TIMEOUT,
                    message = "Lease expired and max attempts exceeded",
                )
            }.onFailure {
                log.warn("Failed to finalize stale exhausted job {}", jobId, it)
            }
        }

        val stale = workerClaimRepository.requeueStaleRunningJobs(
            batchSize = workerProperties.batchSize,
            maxAttempts = workerProperties.maxAttempts,
        )
        if (stale.isNotEmpty()) {
            log.info("Requeued {} stale jobs", stale.size)
        }

        val availableSlots = (workerProperties.threads - workerTaskExecutor.activeCount).coerceAtLeast(0)
        if (availableSlots == 0) {
            return
        }

        val claimed = workerClaimRepository.claimQueuedJobs(
            workerId = workerProperties.id,
            leaseSeconds = workerProperties.leaseSeconds,
            batchSize = min(workerProperties.batchSize, availableSlots),
        )

        claimed.forEach { jobId ->
            workerTaskExecutor.execute {
                workerExecutionService.execute(jobId)
            }
        }
    }
}
