package ai.realteeth.imagejobserver.client.mockworker

import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
@ActiveProfiles("test")
class MockWorkerValidationErrorIntegrationTest {

    @Autowired
    private lateinit var mockWorkerClient: MockWorkerClient

    @Autowired
    private lateinit var mockApiKeyProvider: MockApiKeyProvider

    @Autowired
    private lateinit var mockWorkerProperties: MockWorkerProperties

    @BeforeEach
    fun setUp() {
        mockApiKeyProvider.invalidateCachedApiKey()
        mockWorkerProperties.apiKey = ""
        mockWorkerProperties.autoIssueEnabled = true
        mockWorkerProperties.candidateName = "박상민"
        mockWorkerProperties.candidateEmail = "pp8817@naver.com"
    }

    @Test
    fun `process 요청의 422 validation error는 BAD_REQUEST로 매핑된다`() {
        mockWorkerProperties.apiKey = "mock_configured_key"
        mockWorkerProperties.autoIssueEnabled = false

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(422)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "detail": [
                        {
                          "loc": ["body", "imageUrl"],
                          "msg": "Field required",
                          "type": "missing"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val exception = assertThrows<MockWorkerException> {
            mockWorkerClient.startProcess("https://example.com/invalid.png")
        }

        assertEquals(JobErrorCode.BAD_REQUEST, exception.errorCode)
        assertTrue(exception.message?.contains("body.imageUrl: Field required") == true)
        assertTrue(exception.retryable.not())
    }

    @Test
    fun `issue-key 요청의 422 validation error는 BAD_REQUEST로 매핑된다`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(422)
                .addHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "detail": [
                        {
                          "loc": ["body", "candidateName"],
                          "msg": "Input should be a valid string",
                          "type": "string_type"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )

        val exception = assertThrows<MockWorkerException> {
            mockApiKeyProvider.resolveApiKey()
        }

        assertEquals(JobErrorCode.BAD_REQUEST, exception.errorCode)
        assertTrue(exception.message?.contains("body.candidateName: Input should be a valid string") == true)
        assertTrue(exception.retryable.not())
    }

    companion object {
        private val mockWebServer = MockWebServer().apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerMock(registry: DynamicPropertyRegistry) {
            registry.add("app.mock.base-url") { mockWebServer.url("/mock").toString().removeSuffix("/") }
            registry.add("app.mock.api-key") { "" }
            registry.add("app.mock.auto-issue-enabled") { true }
            registry.add("app.mock.candidate-name") { "박상민" }
            registry.add("app.mock.candidate-email") { "pp8817@naver.com" }
            registry.add("app.worker.enabled") { false }
        }

        @JvmStatic
        @AfterAll
        fun shutdownServer() {
            mockWebServer.shutdown()
        }
    }
}
