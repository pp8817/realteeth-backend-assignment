package ai.realteeth.imagejobserver.worker.service

import ai.realteeth.imagejobserver.client.mockworker.MockWorkerClient
import ai.realteeth.imagejobserver.client.mockworker.dto.MockWorkerJobStatus
import ai.realteeth.imagejobserver.job.domain.JobEntity
import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import ai.realteeth.imagejobserver.job.service.JobService
import ai.realteeth.imagejobserver.worker.config.WorkerProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class WorkerProcessRunner(
    private val jobService: JobService,
    private val mockWorkerClient: MockWorkerClient,
    private val workerRetryExecutor: WorkerRetryExecutor,
    private val workerLeaseManager: WorkerLeaseManager,
    private val workerProperties: WorkerProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun run(jobId: UUID, initialJob: JobEntity) {
        if (!workerLeaseManager.isOwnedRunningJob(initialJob)) {
            return
        }

        if (isProcessingTimedOut(initialJob)) {
            log.warn(
                "Processing timeout reached for job {}, abandon current execution for stale recovery",
                jobId,
            )
            return
        }

        if (initialJob.externalJobId == null) {
            val startResponse = workerRetryExecutor.execute(jobId) {
                mockWorkerClient.startProcess(initialJob.imageUrl)
            }
            jobService.saveExternalJobId(jobId, startResponse.jobId)
            handleStatus(
                jobId = jobId,
                status = startResponse.status,
                result = null,
            )
            return
        }

        val externalJobId = initialJob.externalJobId ?: return
        val statusResponse = workerRetryExecutor.execute(jobId) {
            mockWorkerClient.getProcessStatus(externalJobId)
        }
        handleStatus(
            jobId = jobId,
            status = statusResponse.status,
            result = statusResponse.result,
        )
    }

    private fun handleStatus(jobId: UUID, status: MockWorkerJobStatus, result: String?) {
        when (status) {
            MockWorkerJobStatus.PROCESSING -> {
                val scheduled = jobService.scheduleNextPoll(
                    jobId = jobId,
                    workerId = workerLeaseManager.workerId(),
                    nextPollAt = workerLeaseManager.nextPollAt(),
                )
                if (!scheduled) {
                    log.warn("Failed to schedule next poll for job {}, abandon for recovery", jobId)
                }
            }

            MockWorkerJobStatus.COMPLETED -> {
                jobService.completeSucceeded(jobId, result)
            }

            MockWorkerJobStatus.FAILED -> {
                jobService.completeFailed(
                    jobId = jobId,
                    errorCode = JobErrorCode.MOCK_WORKER_FAILED,
                    message = result ?: "Mock Worker returned FAILED",
                )
            }
        }
    }

    private fun isProcessingTimedOut(job: JobEntity): Boolean {
        val startedAt = job.processingStartedAt ?: job.updatedAt ?: job.createdAt ?: return false
        return Duration.between(startedAt, Instant.now()).seconds >= workerProperties.maxProcessingSeconds.toLong()
    }
}
