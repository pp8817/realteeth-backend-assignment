package ai.realteeth.imagejobserver.job.controller

import ai.realteeth.imagejobserver.job.controller.dto.CreateJobRequest
import ai.realteeth.imagejobserver.job.controller.dto.CreateJobResponse
import ai.realteeth.imagejobserver.job.controller.dto.JobListResponse
import ai.realteeth.imagejobserver.job.controller.dto.JobStatusResponse
import ai.realteeth.imagejobserver.job.domain.JobStatus
import ai.realteeth.imagejobserver.job.service.JobService
import jakarta.validation.Valid
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
        return ResponseEntity.status(status).body(response)
    }

    @GetMapping("/{jobId}")
    fun getJobStatus(
        @PathVariable jobId: UUID,
    ): ResponseEntity<JobStatusResponse> {
        return ResponseEntity.ok(jobService.getJobStatus(jobId))
    }

    @GetMapping("/{jobId}/result")
    fun getJobResult(
        @PathVariable jobId: UUID,
    ): ResponseEntity<Any> {
        return when (val result = jobService.getJobResult(jobId)) {
            is JobService.JobResultView.Pending -> ResponseEntity.status(HttpStatus.ACCEPTED).body(result.value)
            is JobService.JobResultView.Success -> ResponseEntity.ok(result.value)
            is JobService.JobResultView.Failure -> ResponseEntity.ok(result.value)
        }
    }

    @GetMapping
    fun listJobs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: JobStatus?,
    ): ResponseEntity<JobListResponse> {
        return ResponseEntity.ok(jobService.listJobs(page, size, status))
    }
}
