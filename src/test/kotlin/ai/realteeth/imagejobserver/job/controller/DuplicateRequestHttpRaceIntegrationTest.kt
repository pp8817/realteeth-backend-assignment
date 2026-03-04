package ai.realteeth.imagejobserver.job.controller

import ai.realteeth.imagejobserver.job.repository.JobJpaRepository
import ai.realteeth.imagejobserver.job.repository.JobResultJpaRepository
import ai.realteeth.imagejobserver.support.PostgresContainerSupport
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DuplicateRequestHttpRaceIntegrationTest : PostgresContainerSupport() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jobRepository: JobJpaRepository

    @Autowired
    private lateinit var jobResultRepository: JobResultJpaRepository

    @LocalServerPort
    private var port: Int = 0

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    @BeforeEach
    fun setUp() {
        jobResultRepository.deleteAll()
        jobRepository.deleteAll()
    }

    @Test
    fun `동일 Idempotency-Key 동시 요청은 HTTP 레벨에서 단일 job만 생성된다`() {
        val threadCount = 8
        val responses = runConcurrentCreateRequests(
            threadCount = threadCount,
            imageUrl = "https://example.com/http-race-idem.png",
            idempotencyKey = "idem-race-001",
        )

        assertCreatedAndDedupedResponses(responses, threadCount)
        assertEquals(1L, jobRepository.count())
    }

    @Test
    fun `Idempotency-Key 없이 동일 imageUrl 동시 요청도 HTTP 레벨에서 단일 job만 생성된다`() {
        val threadCount = 8
        val responses = runConcurrentCreateRequests(
            threadCount = threadCount,
            imageUrl = "https://example.com/http-race-fingerprint.png",
            idempotencyKey = null,
        )

        assertCreatedAndDedupedResponses(responses, threadCount)
        assertEquals(1L, jobRepository.count())
    }

    private fun runConcurrentCreateRequests(
        threadCount: Int,
        imageUrl: String,
        idempotencyKey: String?,
    ): List<ResponseSnapshot> {
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val responses = Collections.synchronizedList(mutableListOf<ResponseSnapshot>())

        repeat(threadCount) {
            executor.submit {
                try {
                    startLatch.await(5, TimeUnit.SECONDS)
                    val response = postJob(imageUrl, idempotencyKey)
                    val json = objectMapper.readTree(response.body())
                    responses.add(
                        ResponseSnapshot(
                            statusCode = response.statusCode(),
                            jobId = json.path("jobId").asText(),
                        ),
                    )
                } catch (t: Throwable) {
                    errors.add(t)
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(40, TimeUnit.SECONDS)
        executor.shutdownNow()

        assertTrue(finished, "모든 HTTP 요청 스레드가 종료되어야 합니다")
        assertTrue(errors.isEmpty(), "HTTP 요청 처리 중 예외가 없어야 합니다: $errors")
        return responses.toList()
    }

    private fun postJob(imageUrl: String, idempotencyKey: String?): HttpResponse<String> {
        val requestBody = objectMapper.writeValueAsString(mapOf("imageUrl" to imageUrl))

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$port/jobs"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))

        if (!idempotencyKey.isNullOrBlank()) {
            requestBuilder.header("Idempotency-Key", idempotencyKey)
        }

        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun assertCreatedAndDedupedResponses(responses: List<ResponseSnapshot>, expectedCount: Int) {
        assertEquals(expectedCount, responses.size)
        assertEquals(1, responses.count { it.statusCode == 201 })
        assertEquals(expectedCount - 1, responses.count { it.statusCode == 200 })
        assertTrue(responses.all { it.statusCode == 200 || it.statusCode == 201 })
        assertEquals(1, responses.map { it.jobId }.toSet().size)
    }

    private data class ResponseSnapshot(
        val statusCode: Int,
        val jobId: String,
    )
}
