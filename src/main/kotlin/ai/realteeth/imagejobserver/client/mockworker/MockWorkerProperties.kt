package ai.realteeth.imagejobserver.client.mockworker

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.mock")
data class MockWorkerProperties(
    var baseUrl: String = "https://dev.realteeth.ai/mock",
    var apiKey: String = "",
    var autoIssueEnabled: Boolean = true,
    var candidateName: String = "박상민",
    var candidateEmail: String = "pp8817@naver.com",
    var timeoutMs: Long = 5000,
)
