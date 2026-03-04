package ai.realteeth.imagejobserver.client.mockworker

import ai.realteeth.imagejobserver.client.mockworker.dto.IssueKeyRequest
import ai.realteeth.imagejobserver.client.mockworker.dto.IssueKeyResponse
import ai.realteeth.imagejobserver.client.mockworker.dto.MockWorkerErrorResponse
import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

@Component
class MockApiKeyProvider(
    private val mockWorkerWebClient: WebClient,
    private val properties: MockWorkerProperties,
) {

    private val cachedIssuedApiKey = AtomicReference<String?>()

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

    private fun issueKey(): String {
        try {
            return mockWorkerWebClient.post()
                .uri("/auth/issue-key")
                .bodyValue(
                    IssueKeyRequest(
                        candidateName = properties.candidateName,
                        email = properties.candidateEmail,
                    ),
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    response.bodyToMono(MockWorkerErrorResponse::class.java)
                        .defaultIfEmpty(MockWorkerErrorResponse("Mock Worker error"))
                        .flatMap { Mono.error(toException(response.statusCode().value(), it.detail)) }
                }
                .bodyToMono(IssueKeyResponse::class.java)
                .block()
                ?.apiKey
                ?.takeIf { it.isNotBlank() }
                ?: throw MockWorkerException(
                    errorCode = JobErrorCode.INTERNAL,
                    retryable = true,
                    message = "Failed to issue Mock Worker API key",
                )
        } catch (ex: MockWorkerException) {
            throw ex
        } catch (ex: WebClientResponseException) {
            throw toException(ex.statusCode.value(), ex.responseBodyAsString)
        } catch (ex: WebClientRequestException) {
            val timeout = ex.cause is TimeoutException
            throw if (timeout) {
                MockWorkerException(
                    errorCode = JobErrorCode.TIMEOUT,
                    retryable = true,
                    message = "Timeout while issuing Mock Worker API key",
                    cause = ex,
                )
            } else {
                MockWorkerException(
                    errorCode = JobErrorCode.INTERNAL,
                    retryable = true,
                    message = "Network error while issuing Mock Worker API key",
                    cause = ex,
                )
            }
        }
    }

    private fun toException(statusCode: Int, detail: String): MockWorkerException {
        return when (statusCode) {
            400, 422 -> MockWorkerException(JobErrorCode.BAD_REQUEST, retryable = false, message = detail)
            401 -> MockWorkerException(JobErrorCode.UNAUTHORIZED, retryable = false, message = detail)
            429 -> MockWorkerException(JobErrorCode.RATE_LIMITED, retryable = true, message = detail)
            in 500..599 -> MockWorkerException(JobErrorCode.INTERNAL, retryable = true, message = detail)
            else -> MockWorkerException(JobErrorCode.INTERNAL, retryable = false, message = detail)
        }
    }
}
