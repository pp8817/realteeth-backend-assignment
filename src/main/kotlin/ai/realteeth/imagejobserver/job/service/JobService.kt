package ai.realteeth.imagejobserver.job.service

import ai.realteeth.imagejobserver.global.exception.DataIntegrityException
import ai.realteeth.imagejobserver.global.exception.ResourceNotFoundException
import ai.realteeth.imagejobserver.global.util.HashUtils
import ai.realteeth.imagejobserver.global.util.ProgressMapper
import ai.realteeth.imagejobserver.job.controller.dto.CreateJobResponse
import ai.realteeth.imagejobserver.job.controller.dto.FailureResultResponse
import ai.realteeth.imagejobserver.job.controller.dto.JobListItemResponse
import ai.realteeth.imagejobserver.job.controller.dto.JobListResponse
import ai.realteeth.imagejobserver.job.controller.dto.JobStatusResponse
import ai.realteeth.imagejobserver.job.controller.dto.PendingResultResponse
import ai.realteeth.imagejobserver.job.controller.dto.SuccessResultResponse
import ai.realteeth.imagejobserver.job.domain.JobEntity
import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import ai.realteeth.imagejobserver.job.domain.JobResultEntity
import ai.realteeth.imagejobserver.job.domain.JobStateMachine
import ai.realteeth.imagejobserver.job.domain.JobStatus
import ai.realteeth.imagejobserver.job.repository.JobInsertRepository
import ai.realteeth.imagejobserver.job.repository.JobJpaRepository
import ai.realteeth.imagejobserver.job.repository.JobResultJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class JobService(
    private val jobRepository: JobJpaRepository,
    private val jobResultRepository: JobResultJpaRepository,
    private val jobInsertRepository: JobInsertRepository,
) {

    @Transactional
    fun createJob(imageUrl: String, idempotencyKey: String?): CreateJobResponse {
        val normalizedKey = idempotencyKey?.trim()?.takeIf { it.isNotBlank() }
        val fingerprint = HashUtils.sha256(imageUrl)

        val insertResult = jobInsertRepository.insertOrGet(
            jobId = UUID.randomUUID(),
            imageUrl = imageUrl,
            idempotencyKey = normalizedKey,
            fingerprint = fingerprint,
        )

        if (!insertResult.created) {
            return CreateJobResponse(
                jobId = insertResult.jobId,
                status = insertResult.status,
                deduped = true,
            )
        }

        val newJob = jobRepository.findById(insertResult.jobId)
            .orElseThrow { ResourceNotFoundException("Job not found: ${insertResult.jobId}") }
        JobStateMachine.requireTransition(newJob.status, JobStatus.QUEUED)
        newJob.status = JobStatus.QUEUED
        jobRepository.save(newJob)

        return CreateJobResponse(jobId = newJob.id, status = JobStatus.RECEIVED, deduped = false)
    }

    @Transactional(readOnly = true)
    fun getJobStatus(jobId: UUID): JobStatusResponse {
        val job = getJobOrThrow(jobId)
        return JobStatusResponse(
            jobId = job.id,
            status = job.status,
            progress = ProgressMapper.toProgress(job.status),
            createdAt = job.createdAt ?: throw IllegalStateException("createdAt is null"),
            updatedAt = job.updatedAt ?: throw IllegalStateException("updatedAt is null"),
            attemptCount = job.attemptCount,
        )
    }

    @Transactional(readOnly = true)
    fun getJobResult(jobId: UUID): JobResultView {
        val job = getJobOrThrow(jobId)

        return when (job.status) {
            JobStatus.SUCCEEDED -> {
                val result = jobResultRepository.findByJobId(job.id)
                    ?.resultPayload
                    ?: throw DataIntegrityException("Job result missing for succeeded job: $jobId")
                JobResultView.Success(SuccessResultResponse(result))
            }

            JobStatus.FAILED -> {
                val result = jobResultRepository.findByJobId(job.id)
                JobResultView.Failure(
                    FailureResultResponse(
                        errorCode = result?.errorCode ?: JobErrorCode.INTERNAL,
                        message = result?.errorMessage ?: "Job failed",
                    ),
                )
            }

            else -> JobResultView.Pending(PendingResultResponse(job.status))
        }
    }

    @Transactional(readOnly = true)
    fun listJobs(page: Int, size: Int, status: JobStatus?): JobListResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val paged = if (status == null) {
            jobRepository.findAll(pageable)
        } else {
            jobRepository.findAllByStatus(status, pageable)
        }

        return JobListResponse(
            page = page,
            size = size,
            total = paged.totalElements,
            items = paged.content.map {
                JobListItemResponse(
                    jobId = it.id,
                    status = it.status,
                    progress = ProgressMapper.toProgress(it.status),
                    createdAt = it.createdAt ?: throw IllegalStateException("createdAt is null"),
                    updatedAt = it.updatedAt ?: throw IllegalStateException("updatedAt is null"),
                )
            },
        )
    }

    @Transactional
    fun getJobForUpdate(jobId: UUID): JobEntity {
        return jobRepository.findByIdForUpdate(jobId)
            ?: throw ResourceNotFoundException("Job not found: $jobId")
    }

    @Transactional
    fun incrementAttemptCount(jobId: UUID) {
        val job = getJobForUpdate(jobId)
        job.attemptCount += 1
        jobRepository.save(job)
    }

    @Transactional(readOnly = true)
    fun findById(jobId: UUID): JobEntity? = jobRepository.findById(jobId).orElse(null)

    @Transactional
    fun saveExternalJobId(jobId: UUID, externalJobId: String) {
        val job = getJobForUpdate(jobId)
        if (job.status.isFinal()) {
            return
        }

        if (job.externalJobId == null) {
            job.externalJobId = externalJobId
            jobRepository.save(job)
        }
    }

    @Transactional
    fun completeSucceeded(jobId: UUID, payload: String) {
        val job = getJobForUpdate(jobId)
        if (job.status.isFinal()) {
            return
        }

        JobStateMachine.requireTransition(job.status, JobStatus.SUCCEEDED)
        job.status = JobStatus.SUCCEEDED
        job.lockedBy = null
        job.lockedUntil = null
        jobRepository.save(job)

        val jobResult = jobResultRepository.findByJobId(jobId) ?: JobResultEntity(job = job)
        jobResult.resultPayload = payload
        jobResult.errorCode = null
        jobResult.errorMessage = null
        jobResultRepository.save(jobResult)
    }

    @Transactional
    fun completeFailed(jobId: UUID, errorCode: JobErrorCode, message: String) {
        val job = getJobForUpdate(jobId)
        if (job.status.isFinal()) {
            return
        }

        JobStateMachine.requireTransition(job.status, JobStatus.FAILED)
        job.status = JobStatus.FAILED
        job.lockedBy = null
        job.lockedUntil = null
        jobRepository.save(job)

        val jobResult = jobResultRepository.findByJobId(jobId) ?: JobResultEntity(job = job)
        jobResult.resultPayload = null
        jobResult.errorCode = errorCode
        jobResult.errorMessage = message
        jobResultRepository.save(jobResult)
    }

    private fun getJobOrThrow(jobId: UUID): JobEntity {
        return jobRepository.findById(jobId)
            .orElseThrow { ResourceNotFoundException("Job not found: $jobId") }
    }

    sealed interface JobResultView {
        data class Pending(val value: PendingResultResponse) : JobResultView
        data class Success(val value: SuccessResultResponse) : JobResultView
        data class Failure(val value: FailureResultResponse) : JobResultView
    }
}
