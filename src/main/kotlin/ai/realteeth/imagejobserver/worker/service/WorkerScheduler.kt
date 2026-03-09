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
import java.util.UUID
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

        val reservedPollReadySlots = min(availableSlots, maxOf(1, availableSlots / 2))
        val reservedQueuedSlots = (availableSlots - reservedPollReadySlots).coerceAtLeast(0)

        val pollReadyClaimed = workerClaimRepository.claimPollReadyRunningJobs(
            workerId = workerProperties.id,
            leaseSeconds = workerProperties.leaseSeconds,
            batchSize = reservedPollReadySlots,
        )
        dispatch(pollReadyClaimed)

        var remainingSlots = availableSlots - pollReadyClaimed.size
        if (remainingSlots == 0) {
            return
        }

        val queuedClaimed = if (reservedQueuedSlots > 0) {
            workerClaimRepository.claimQueuedJobs(
                workerId = workerProperties.id,
                leaseSeconds = workerProperties.leaseSeconds,
                batchSize = min(reservedQueuedSlots, remainingSlots),
            )
        } else {
            emptyList()
        }
        dispatch(queuedClaimed)

        remainingSlots -= queuedClaimed.size
        if (remainingSlots == 0) {
            return
        }

        if (pollReadyClaimed.size < reservedPollReadySlots) {
            val extraQueued = workerClaimRepository.claimQueuedJobs(
                workerId = workerProperties.id,
                leaseSeconds = workerProperties.leaseSeconds,
                batchSize = remainingSlots,
            )
            dispatch(extraQueued)
            remainingSlots -= extraQueued.size
        }

        if (remainingSlots == 0) {
            return
        }

        if (queuedClaimed.size < reservedQueuedSlots || reservedQueuedSlots == 0) {
            val extraPollReady = workerClaimRepository.claimPollReadyRunningJobs(
                workerId = workerProperties.id,
                leaseSeconds = workerProperties.leaseSeconds,
                batchSize = remainingSlots,
            )
            dispatch(extraPollReady)
        }
    }

    private fun dispatch(jobIds: List<UUID>) {
        jobIds.forEach { jobId ->
            workerTaskExecutor.execute {
                workerExecutionService.execute(jobId)
            }
        }
    }
}
