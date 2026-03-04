package ai.realteeth.imagejobserver.worker.service

import ai.realteeth.imagejobserver.job.domain.JobEntity
import ai.realteeth.imagejobserver.job.domain.JobStatus
import ai.realteeth.imagejobserver.job.repository.JobJpaRepository
import ai.realteeth.imagejobserver.job.repository.JobResultJpaRepository
import ai.realteeth.imagejobserver.support.PostgresContainerSupport
import ai.realteeth.imagejobserver.worker.repository.WorkerClaimRepository
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
class WorkerClaimLeasePostgresIntegrationTest : PostgresContainerSupport() {

    @Autowired
    private lateinit var workerExecutionService: WorkerExecutionService

    @Autowired
    private lateinit var workerClaimRepository: WorkerClaimRepository

    @Autowired
    private lateinit var jobRepository: JobJpaRepository

    @Autowired
    private lateinit var jobResultRepository: JobResultJpaRepository

    @BeforeEach
    fun setUp() {
        jobResultRepository.deleteAll()
        jobRepository.deleteAll()
    }

    @Test
    fun `claim한 queued job은 execute 이후 SUCCEEDED로 완료되고 결과가 저장된다`() {
        val jobId = insertQueuedJob()

        val claimed = workerClaimRepository.claimQueuedJobs(
            workerId = "worker-1",
            leaseSeconds = 30,
            batchSize = 5,
        )
        assertTrue(claimed.contains(jobId))

        val running = jobRepository.findById(jobId).orElseThrow()
        assertEquals(JobStatus.RUNNING, running.status)
        assertEquals("worker-1", running.lockedBy)
        assertNotNull(running.lockedUntil)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"jobId\":\"ext-success-1\",\"status\":\"PROCESSING\"}"),
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"jobId\":\"ext-success-1\",\"status\":\"COMPLETED\",\"result\":\"done\"}"),
        )

        workerExecutionService.execute(jobId)

        val completed = jobRepository.findById(jobId).orElseThrow()
        assertEquals(JobStatus.SUCCEEDED, completed.status)
        assertEquals("ext-success-1", completed.externalJobId)
        assertNull(completed.lockedBy)
        assertNull(completed.lockedUntil)

        val result = jobResultRepository.findByJobId(jobId)
        assertEquals("done", result?.resultPayload)
        assertNull(result?.errorCode)

        val firstRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        val secondRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        assertEquals("/mock/process", firstRequest?.path)
        assertEquals("/mock/process/ext-success-1", secondRequest?.path)
    }

    @Test
    fun `stale RUNNING job은 max attempts 미만일 때만 requeue 된다`() {
        val staleRequeueTarget = insertRunningJob(
            attemptCount = 1,
            lockedUntil = Instant.now().minusSeconds(30),
        )
        val staleMaxedOut = insertRunningJob(
            attemptCount = 3,
            lockedUntil = Instant.now().minusSeconds(30),
        )

        val requeued = workerClaimRepository.requeueStaleRunningJobs(
            batchSize = 10,
            maxAttempts = 3,
        )

        assertTrue(requeued.contains(staleRequeueTarget))
        assertFalse(requeued.contains(staleMaxedOut))

        val requeuedJob = jobRepository.findById(staleRequeueTarget).orElseThrow()
        assertEquals(JobStatus.QUEUED, requeuedJob.status)
        assertEquals(2, requeuedJob.attemptCount)
        assertNull(requeuedJob.lockedBy)
        assertNull(requeuedJob.lockedUntil)

        val maxedOutJob = jobRepository.findById(staleMaxedOut).orElseThrow()
        assertEquals(JobStatus.RUNNING, maxedOutJob.status)
        assertEquals(3, maxedOutJob.attemptCount)
    }

    @Test
    fun `extend lease는 locked_until을 미래 시점으로 연장한다`() {
        val jobId = insertRunningJob(
            attemptCount = 0,
            lockedUntil = Instant.now().plusSeconds(1),
        )
        val before = jobRepository.findById(jobId).orElseThrow().lockedUntil
            ?: throw IllegalStateException("lockedUntil must exist")

        val updated = workerClaimRepository.extendLease(
            jobId = jobId,
            workerId = "worker-1",
            leaseSeconds = 60,
        )

        assertTrue(updated)
        val after = jobRepository.findById(jobId).orElseThrow().lockedUntil
            ?: throw IllegalStateException("lockedUntil must exist")
        assertTrue(after.isAfter(before))
    }

    private fun insertQueuedJob(): UUID {
        val entity = JobEntity(
            id = UUID.randomUUID(),
            status = JobStatus.QUEUED,
            imageUrl = "https://example.com/postgres-claim.png",
            fingerprint = UUID.randomUUID().toString().replace("-", ""),
            attemptCount = 0,
        )
        return jobRepository.saveAndFlush(entity).id
    }

    private fun insertRunningJob(attemptCount: Int, lockedUntil: Instant): UUID {
        val entity = JobEntity(
            id = UUID.randomUUID(),
            status = JobStatus.RUNNING,
            imageUrl = "https://example.com/postgres-running.png",
            fingerprint = UUID.randomUUID().toString().replace("-", ""),
            attemptCount = attemptCount,
            lockedBy = "worker-1",
            lockedUntil = lockedUntil,
        )
        return jobRepository.saveAndFlush(entity).id
    }

    companion object {
        private val mockWebServer = MockWebServer().apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerMockProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.mock.base-url") { mockWebServer.url("/mock").toString().removeSuffix("/") }
            registry.add("app.mock.api-key") { "test-api-key" }
            registry.add("app.worker.id") { "worker-1" }
            registry.add("app.worker.lease-seconds") { 30 }
            registry.add("app.worker.max-attempts") { 3 }
            registry.add("app.worker.batch-size") { 5 }
            registry.add("app.worker.poll-interval-ms") { 60000 }
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            mockWebServer.shutdown()
        }
    }
}
