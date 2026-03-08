package ai.realteeth.imagejobserver.client.mockworker

import ai.realteeth.imagejobserver.client.mockworker.dto.MockWorkerJobStatus
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
class MockWorkerAutoIssueKeyIntegrationTest {

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
    }

    @Test
    fun `api key 미설정 시 자동 발급 후 process 호출에 키를 재사용한다`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"apiKey\":\"mock_auto_issued\"}"),
        )
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
                .setBody("{\"jobId\":\"ext-2\",\"status\":\"PROCESSING\"}"),
        )

        val first = mockWorkerClient.startProcess("https://example.com/a.png")
        val second = mockWorkerClient.startProcess("https://example.com/b.png")

        assertEquals(MockWorkerJobStatus.PROCESSING, first.status)
        assertEquals(MockWorkerJobStatus.PROCESSING, second.status)

        val issueRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        val processRequest1 = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        val processRequest2 = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        val noMore = mockWebServer.takeRequest(300, TimeUnit.MILLISECONDS)
        val issueBody = issueRequest?.body?.readUtf8() ?: ""

        assertEquals("/mock/auth/issue-key", issueRequest?.path)
        assertTrue(issueBody.contains("\"candidateName\":\"박상민\""))
        assertTrue(issueBody.contains("\"email\":\"pp8817@naver.com\""))

        assertEquals("/mock/process", processRequest1?.path)
        assertEquals("mock_auto_issued", processRequest1?.getHeader("X-API-KEY"))

        assertEquals("/mock/process", processRequest2?.path)
        assertEquals("mock_auto_issued", processRequest2?.getHeader("X-API-KEY"))

        assertNull(noMore)
    }

    @Test
    fun `설정된 api key가 있으면 issue-key를 호출하지 않는다`() {
        mockWorkerProperties.apiKey = "mock_configured_key"
        mockWorkerProperties.autoIssueEnabled = false

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("{\"jobId\":\"ext-configured\",\"status\":\"PROCESSING\"}"),
        )

        val response = mockWorkerClient.startProcess("https://example.com/configured.png")

        assertEquals(MockWorkerJobStatus.PROCESSING, response.status)

        val processRequest = mockWebServer.takeRequest(1, TimeUnit.SECONDS)
        val noMore = mockWebServer.takeRequest(300, TimeUnit.MILLISECONDS)

        assertEquals("/mock/process", processRequest?.path)
        assertEquals("mock_configured_key", processRequest?.getHeader("X-API-KEY"))
        assertNull(noMore)
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
