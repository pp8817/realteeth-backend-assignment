package ai.realteeth.imagejobserver.client.mockworker

import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

@Component
class MockApiKeyProvider(
    private val properties: MockWorkerProperties,
    private val rawMockWorkerApiClient: RawMockWorkerApiClient,
) {

    private val cachedIssuedApiKey = AtomicReference<String?>()

    fun invalidateCachedApiKey() {
        cachedIssuedApiKey.set(null)
    }

    fun hasConfiguredApiKey(): Boolean = properties.apiKey.trim().isNotBlank()

    fun resolveApiKey(): String? {
        val configured = properties.apiKey.trim()
        if (configured.isNotBlank()) {
            return configured
        }

        cachedIssuedApiKey.get()?.takeIf { it.isNotBlank() }?.let { return it }

        if (!properties.autoIssueEnabled) {
            return null
        }

        synchronized(this) {
            cachedIssuedApiKey.get()?.takeIf { it.isNotBlank() }?.let { return it }
            val issued = issueKey()
            cachedIssuedApiKey.set(issued)
            return issued
        }
    }

    fun refreshIssuedApiKey(): String? {
        if (hasConfiguredApiKey() || !properties.autoIssueEnabled) {
            return null
        }

        synchronized(this) {
            cachedIssuedApiKey.set(null)
            val issued = issueKey()
            cachedIssuedApiKey.set(issued)
            return issued
        }
    }

    private fun issueKey(): String {
        return rawMockWorkerApiClient.issueKey(
            candidateName = properties.candidateName,
            email = properties.candidateEmail,
        ).apiKey
            .takeIf { it.isNotBlank() }
            ?: throw MockWorkerException(
                errorCode = JobErrorCode.INTERNAL,
                retryable = true,
                message = "Failed to issue Mock Worker API key",
            )
    }
}
