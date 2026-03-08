package ai.realteeth.imagejobserver.client.mockworker

import ai.realteeth.imagejobserver.client.mockworker.dto.MockWorkerErrorParser
import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientRequestException
import java.util.concurrent.TimeoutException

@Component
class MockWorkerErrorTranslator(
    private val objectMapper: ObjectMapper,
) {

    fun translateResponse(statusCode: Int, body: String): MockWorkerException {
        val detail = extractDetail(body)
        return when (statusCode) {
            400, 422 -> MockWorkerException(JobErrorCode.BAD_REQUEST, retryable = false, message = detail)
            401 -> MockWorkerException(JobErrorCode.UNAUTHORIZED, retryable = false, message = detail)
            429 -> MockWorkerException(JobErrorCode.RATE_LIMITED, retryable = true, message = detail)
            in 500..599 -> MockWorkerException(JobErrorCode.INTERNAL, retryable = true, message = detail)
            else -> MockWorkerException(JobErrorCode.INTERNAL, retryable = false, message = detail)
        }
    }

    fun translateRequest(ex: WebClientRequestException, timeoutMessage: String, networkMessage: String): MockWorkerException {
        val timeout = ex.cause is TimeoutException
        return if (timeout) {
            MockWorkerException(
                errorCode = JobErrorCode.TIMEOUT,
                retryable = true,
                message = timeoutMessage,
                cause = ex,
            )
        } else {
            MockWorkerException(
                errorCode = JobErrorCode.INTERNAL,
                retryable = true,
                message = networkMessage,
                cause = ex,
            )
        }
    }

    private fun extractDetail(body: String): String {
        return runCatching { objectMapper.readTree(body) }
            .map(MockWorkerErrorParser::extractDetail)
            .getOrElse { body.ifBlank { "Mock Worker error" } }
    }
}
