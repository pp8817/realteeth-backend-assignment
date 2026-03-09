package ai.realteeth.imagejobserver.worker.service

import ai.realteeth.imagejobserver.job.domain.JobEntity
import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import ai.realteeth.imagejobserver.job.domain.JobStatus
import ai.realteeth.imagejobserver.job.repository.JobJpaRepository
import ai.realteeth.imagejobserver.job.repository.JobResultJpaRepository
import ai.realteeth.imagejobserver.worker.config.WorkerProperties
import ai.realteeth.imagejobserver.worker.repository.WorkerClaimRepository
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
class WorkerExecutionIntegrationTest {

    @Autowired
    private lateinit var workerExecutionService: WorkerExecutionService

    @Autowired
    private lateinit var jobRepository: JobJpaRepository

    @Autowired
    private lateinit var jobResultRepository: JobResultJpaRepository

    @Autowired
    private lateinit var workerProperties: WorkerProperties

    @MockBean
    private lateinit var workerClaimRepository: WorkerClaimRepository

    @BeforeEach
    fun setUp() {
        jobResultRepository.deleteAll()
        jobRepository.deleteAll()
        whenever(workerClaimRepository.extendLease(any(), any(), any())).thenReturn(true)
    }

    @Test
    fun `mock worker가 COMPLETED를 반환하면 job은 SUCCEEDED가 된다`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"jobId\":\"ext-1\",\"status\":\"PROCESSING\"}"),
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"jobId\":\"ext-1\",\"status\":\"COMPLETED\",\"result\":\"done\"}"),
        )

        val jobId = insertRunningJob()

        workerExecutionService.execute(jobId)

        val scheduled = jobRepository.findById(jobId).orElseThrow()
        assertEquals(JobStatus.RUNNING, scheduled.status)
        assertEquals("ext-1", scheduled.externalJobId)
        assertNotNull(scheduled.nextPollAt)
        assertNull(scheduled.lockedBy)
        assertNull(scheduled.lockedUntil)

        reclaimForPoll(jobId)
        workerExecutionService.execute(jobId)

        val updated = jobRepository.findById(jobId).orElseThrow()
        assertEquals(JobStatus.SUCCEEDED, updated.status)

        val result = jobResultRepository.findByJobId(jobId)
        assertEquals("done", result?.resultPayload)
        assertEquals(null, result?.errorCode)
    }

    @Test
    fun `mock worker가 FAILED를 반환하면 job은 FAILED가 된다`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"jobId\":\"ext-2\",\"status\":\"PROCESSING\"}"),
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"jobId\":\"ext-2\",\"status\":\"FAILED\",\"result\":\"gpu error\"}"),
        )

        val jobId = insertRunningJob()

        workerExecutionService.execute(jobId)

        val scheduled = jobRepository.findById(jobId).orElseThrow()
        assertEquals(JobStatus.RUNNING, scheduled.status)
        assertEquals("ext-2", scheduled.externalJobId)
        assertNotNull(scheduled.nextPollAt)

        reclaimForPoll(jobId)
        workerExecutionService.execute(jobId)

        val updated = jobRepository.findById(jobId).orElseThrow()
        assertEquals(JobStatus.FAILED, updated.status)

        val result = jobResultRepository.findByJobId(jobId)
        assertEquals(JobErrorCode.MOCK_WORKER_FAILED, result?.errorCode)
        assertEquals("gpu error", result?.errorMessage)
    }

    @Test
    fun `mock worker가 COMPLETED지만 result가 null이면 job은 SUCCEEDED가 된다`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"jobId\":\"ext-null-result\",\"status\":\"PROCESSING\"}"),
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"jobId\":\"ext-null-result\",\"status\":\"COMPLETED\",\"result\":null}"),
        )

        val jobId = insertRunningJob()

        workerExecutionService.execute(jobId)

        val scheduled = jobRepository.findById(jobId).orElseThrow()
        assertEquals(JobStatus.RUNNING, scheduled.status)
        assertEquals("ext-null-result", scheduled.externalJobId)
        assertNotNull(scheduled.nextPollAt)

        reclaimForPoll(jobId)
        workerExecutionService.execute(jobId)

        val updated = jobRepository.findById(jobId).orElseThrow()
        assertEquals(JobStatus.SUCCEEDED, updated.status)

        val result = jobResultRepository.findByJobId(jobId)
        assertNull(result?.errorCode)
        assertNull(result?.errorMessage)
        assertNull(result?.resultPayload)
    }

    @Test
    fun `retry 가능한 에러 이후 성공하면 job은 SUCCEEDED가 된다`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"detail\":\"temporary failure\"}"),
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"jobId\":\"ext-3\",\"status\":\"PROCESSING\"}"),
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"jobId\":\"ext-3\",\"status\":\"COMPLETED\",\"result\":\"retry-done\"}"),
        )

        val jobId = insertRunningJob()

        workerExecutionService.execute(jobId)

        val scheduled = jobRepository.findById(jobId).orElseThrow()
        assertEquals(JobStatus.RUNNING, scheduled.status)
        assertEquals("ext-3", scheduled.externalJobId)
        assertTrue(scheduled.attemptCount >= 1)
        assertNotNull(scheduled.nextPollAt)

        reclaimForPoll(jobId)
        workerExecutionService.execute(jobId)

        val updated = jobRepository.findById(jobId).orElseThrow()
        assertEquals(JobStatus.SUCCEEDED, updated.status)
        assertTrue(updated.attemptCount >= 1)

        val result = jobResultRepository.findByJobId(jobId)
        assertEquals("retry-done", result?.resultPayload)
    }

    @Test
    fun `retry 최대 횟수를 초과하면 job은 FAILED가 된다`() {
        repeat(3) {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .addHeader("Content-Type", "application/json")
                    .setBody("{\"detail\":\"server error\"}"),
            )
        }

        val jobId = insertRunningJob()

        workerExecutionService.execute(jobId)

        val updated = jobRepository.findById(jobId).orElseThrow()
        assertEquals(JobStatus.FAILED, updated.status)
        assertTrue(updated.attemptCount >= 3)

        val result = jobResultRepository.findByJobId(jobId)
        assertEquals(JobErrorCode.INTERNAL, result?.errorCode)
    }

    @Test
    fun `lease 연장 실패 시 작업을 포기하고 stale 복구로 넘긴다`() {
        whenever(workerClaimRepository.extendLease(any(), any(), any())).thenReturn(false)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"detail\":\"temporary error\"}"),
        )

        val jobId = insertRunningJob()

        workerExecutionService.execute(jobId)

        val updated = jobRepository.findById(jobId).orElseThrow()
        assertEquals(JobStatus.RUNNING, updated.status)
        assertNull(updated.externalJobId)
        assertNull(jobResultRepository.findByJobId(jobId))
    }

    @Test
    fun `lease 연장 예외 시 작업을 포기하고 stale 복구로 넘긴다`() {
        whenever(workerClaimRepository.extendLease(any(), any(), any()))
            .thenThrow(RuntimeException("db unavailable"))

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"detail\":\"temporary error\"}"),
        )

        val jobId = insertRunningJob()

        workerExecutionService.execute(jobId)

        val updated = jobRepository.findById(jobId).orElseThrow()
        assertEquals(JobStatus.RUNNING, updated.status)
        assertNull(updated.externalJobId)
        assertNull(jobResultRepository.findByJobId(jobId))
    }

    @Test
    fun `최대 실행 시간을 초과하면 작업을 포기하고 stale 복구로 넘긴다`() {
        val originalMaxProcessingSeconds = workerProperties.maxProcessingSeconds
        val originalStatusPollIntervalMs = workerProperties.statusPollIntervalMs

        workerProperties.maxProcessingSeconds = 1
        workerProperties.statusPollIntervalMs = 50

        try {
            val jobId = insertRunningJob(
                processingStartedAt = Instant.now().minusSeconds(5),
            )

            workerExecutionService.execute(jobId)

            val updated = jobRepository.findById(jobId).orElseThrow()
            assertEquals(JobStatus.RUNNING, updated.status)
            assertNull(updated.externalJobId)
            assertNull(jobResultRepository.findByJobId(jobId))
        } finally {
            workerProperties.maxProcessingSeconds = originalMaxProcessingSeconds
            workerProperties.statusPollIntervalMs = originalStatusPollIntervalMs
        }
    }

    private fun insertRunningJob(
        processingStartedAt: Instant = Instant.now(),
    ): UUID {
        val entity = JobEntity(
            id = UUID.randomUUID(),
            status = JobStatus.RUNNING,
            imageUrl = "https://example.com/worker.png",
            attemptCount = 0,
            lockedBy = "worker-1",
            lockedUntil = Instant.now().plusSeconds(60),
            processingStartedAt = processingStartedAt,
        )
        return jobRepository.saveAndFlush(entity).id
    }

    private fun reclaimForPoll(jobId: UUID) {
        val job = jobRepository.findById(jobId).orElseThrow()
        job.lockedBy = "worker-1"
        job.lockedUntil = Instant.now().plusSeconds(60)
        job.nextPollAt = null
        jobRepository.saveAndFlush(job)
    }

    companion object {
        private val mockWebServer = MockWebServer().apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerMock(registry: DynamicPropertyRegistry) {
            registry.add("app.mock.base-url") { mockWebServer.url("/mock").toString().removeSuffix("/") }
            registry.add("app.mock.api-key") { "test-api-key" }
            registry.add("app.mock.auto-issue-enabled") { false }
            registry.add("app.worker.enabled") { false }
            registry.add("app.worker.id") { "worker-1" }
            registry.add("app.worker.max-processing-seconds") { 300 }
            registry.add("app.worker.status-poll-interval-ms") { 100 }
        }
    }
}
