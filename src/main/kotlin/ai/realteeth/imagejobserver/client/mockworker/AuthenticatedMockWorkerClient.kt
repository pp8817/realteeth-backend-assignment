package ai.realteeth.imagejobserver.client.mockworker

import ai.realteeth.imagejobserver.client.mockworker.dto.IssueKeyResponse
import ai.realteeth.imagejobserver.client.mockworker.dto.ProcessStartResponse
import ai.realteeth.imagejobserver.client.mockworker.dto.ProcessStatusResponse
import ai.realteeth.imagejobserver.job.domain.JobErrorCode
import org.springframework.stereotype.Component

@Component
class AuthenticatedMockWorkerClient(
    private val rawMockWorkerApiClient: RawMockWorkerApiClient,
    private val mockApiKeyProvider: MockApiKeyProvider,
) : MockWorkerClient {

    override fun issueKey(candidateName: String, email: String): IssueKeyResponse {
        return rawMockWorkerApiClient.issueKey(candidateName, email)
    }

    override fun startProcess(imageUrl: String): ProcessStartResponse {
        val configuredKey = mockApiKeyProvider.hasConfiguredApiKey()
        val apiKey = mockApiKeyProvider.resolveApiKey()

        return try {
            rawMockWorkerApiClient.startProcess(imageUrl, apiKey)
        } catch (ex: MockWorkerException) {
            if (configuredKey || ex.errorCode != JobErrorCode.UNAUTHORIZED || apiKey.isNullOrBlank()) {
                throw ex
            }

            val refreshedApiKey = mockApiKeyProvider.refreshIssuedApiKey()
            if (refreshedApiKey.isNullOrBlank()) {
                throw ex
            }

            rawMockWorkerApiClient.startProcess(imageUrl, refreshedApiKey)
        }
    }

    override fun getProcessStatus(externalJobId: String): ProcessStatusResponse {
        return rawMockWorkerApiClient.getProcessStatus(externalJobId)
    }
}
