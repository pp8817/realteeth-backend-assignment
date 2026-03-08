package ai.realteeth.imagejobserver.client.mockworker

import ai.realteeth.imagejobserver.client.mockworker.dto.IssueKeyRequest
import ai.realteeth.imagejobserver.client.mockworker.dto.IssueKeyResponse
import ai.realteeth.imagejobserver.client.mockworker.dto.ProcessRequest
import ai.realteeth.imagejobserver.client.mockworker.dto.ProcessStartResponse
import ai.realteeth.imagejobserver.client.mockworker.dto.ProcessStatusResponse
import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Component
class RawMockWorkerApiClient(
    private val mockWorkerWebClient: WebClient,
    private val errorTranslator: MockWorkerErrorTranslator,
) {

    fun issueKey(candidateName: String, email: String): IssueKeyResponse {
        return execute("Timeout while issuing Mock Worker API key", "Network error while issuing Mock Worker API key") {
            mockWorkerWebClient.post()
                .uri("/auth/issue-key")
                .bodyValue(IssueKeyRequest(candidateName, email))
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    response.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .flatMap { Mono.error(errorTranslator.translateResponse(response.statusCode().value(), it)) }
                }
                .bodyToMono(IssueKeyResponse::class.java)
                .block() ?: throw MockWorkerException(
                errorCode = JobErrorCode.INTERNAL,
                retryable = false,
                message = "Empty response from issue-key",
            )
        }
    }

    fun startProcess(imageUrl: String, apiKey: String?): ProcessStartResponse {
        return execute("Timeout while calling Mock Worker", "Network error while calling Mock Worker") {
            mockWorkerWebClient.post()
                .uri("/process")
                .headers {
                    if (!apiKey.isNullOrBlank()) {
                        it.set("X-API-KEY", apiKey)
                    }
                }
                .bodyValue(ProcessRequest(imageUrl))
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    response.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .flatMap { Mono.error(errorTranslator.translateResponse(response.statusCode().value(), it)) }
                }
                .bodyToMono(ProcessStartResponse::class.java)
                .block() ?: throw MockWorkerException(
                errorCode = JobErrorCode.INTERNAL,
                retryable = false,
                message = "Empty response from process start",
            )
        }
    }

    fun getProcessStatus(externalJobId: String): ProcessStatusResponse {
        return execute("Timeout while calling Mock Worker", "Network error while calling Mock Worker") {
            mockWorkerWebClient.get()
                .uri("/process/{job_id}", externalJobId)
                .retrieve()
                .onStatus(HttpStatusCode::isError) { response ->
                    response.bodyToMono(String::class.java)
                        .defaultIfEmpty("")
                        .flatMap { Mono.error(errorTranslator.translateResponse(response.statusCode().value(), it)) }
                }
                .bodyToMono(ProcessStatusResponse::class.java)
                .block() ?: throw MockWorkerException(
                errorCode = JobErrorCode.INTERNAL,
                retryable = false,
                message = "Empty response from process status",
            )
        }
    }

    private fun <T> execute(timeoutMessage: String, networkMessage: String, call: () -> T): T {
        try {
            return call()
        } catch (ex: MockWorkerException) {
            throw ex
        } catch (ex: WebClientResponseException) {
            throw errorTranslator.translateResponse(ex.statusCode.value(), ex.responseBodyAsString)
        } catch (ex: WebClientRequestException) {
            throw errorTranslator.translateRequest(ex, timeoutMessage, networkMessage)
        }
    }
}
