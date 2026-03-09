package ai.realteeth.imagejobserver.job.controller

import ai.realteeth.imagejobserver.job.controller.dto.CreateJobRequest
import ai.realteeth.imagejobserver.job.controller.dto.CreateJobResponse
import ai.realteeth.imagejobserver.job.controller.dto.FailureResultResponse
import ai.realteeth.imagejobserver.job.controller.dto.JobListItemResponse
import ai.realteeth.imagejobserver.job.controller.dto.JobListResponse
import ai.realteeth.imagejobserver.job.controller.dto.JobStatusResponse
import ai.realteeth.imagejobserver.job.controller.dto.PendingResultResponse
import ai.realteeth.imagejobserver.job.controller.dto.SuccessResultResponse
import ai.realteeth.imagejobserver.job.domain.JobStatus
import ai.realteeth.imagejobserver.job.service.CreateJobResult
import ai.realteeth.imagejobserver.job.service.JobListView
import ai.realteeth.imagejobserver.job.service.JobResultView
import ai.realteeth.imagejobserver.job.service.JobStatusView
import ai.realteeth.imagejobserver.job.service.JobService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/jobs")
class JobController(
    private val jobService: JobService,
) {

    @PostMapping
    fun createJob(
        @Valid @RequestBody request: CreateJobRequest,
        @RequestHeader(name = "Idempotency-Key", required = false) idempotencyKey: String?,
    ): ResponseEntity<CreateJobResponse> {
        val response = jobService.createJob(request.imageUrl, idempotencyKey)
        val status = if (response.deduped) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(response.toResponse())
    }

    @GetMapping("/{jobId}")
    fun getJobStatus(
        @PathVariable jobId: UUID,
    ): ResponseEntity<JobStatusResponse> {
        return ResponseEntity.ok(jobService.getJobStatus(jobId).toResponse())
    }

    @GetMapping("/{jobId}/result")
    fun getJobResult(
        @PathVariable jobId: UUID,
    ): ResponseEntity<Any> {
        return when (val result = jobService.getJobResult(jobId)) {
            is JobResultView.Pending -> ResponseEntity.status(HttpStatus.ACCEPTED).body(
                PendingResultResponse(result.value.status),
            )
            is JobResultView.Success -> ResponseEntity.ok(
                SuccessResultResponse(result.value.result),
            )
            is JobResultView.Failure -> ResponseEntity.ok(
                FailureResultResponse(
                    errorCode = result.value.errorCode,
                    message = result.value.message,
                ),
            )
        }
    }

    @GetMapping
    fun listJobs(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
        @RequestParam(required = false) status: JobStatus?,
    ): ResponseEntity<JobListResponse> {
        return ResponseEntity.ok(jobService.listJobs(page, size, status).toResponse())
    }

    private fun CreateJobResult.toResponse(): CreateJobResponse {
        return CreateJobResponse(
            jobId = jobId,
            status = status,
            deduped = deduped,
        )
    }

    private fun JobStatusView.toResponse(): JobStatusResponse {
        return JobStatusResponse(
            jobId = jobId,
            status = status,
            progress = progress,
            createdAt = createdAt,
            updatedAt = updatedAt,
            attemptCount = attemptCount,
        )
    }

    private fun JobListView.toResponse(): JobListResponse {
        return JobListResponse(
            page = page,
            size = size,
            total = total,
            items = items.map {
                JobListItemResponse(
                    jobId = it.jobId,
                    status = it.status,
                    progress = it.progress,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
        )
    }
}
