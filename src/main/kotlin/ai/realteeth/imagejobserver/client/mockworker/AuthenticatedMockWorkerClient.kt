package ai.realteeth.imagejobserver.client.mockworker

import ai.realteeth.imagejobserver.client.mockworker.dto.IssueKeyResponse
import ai.realteeth.imagejobserver.client.mockworker.dto.ProcessStartResponse
import ai.realteeth.imagejobserver.client.mockworker.dto.ProcessStatusResponse
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
        val apiKey = mockApiKeyProvider.resolveApiKey()
        return rawMockWorkerApiClient.startProcess(imageUrl, apiKey)
    }

    override fun getProcessStatus(externalJobId: String): ProcessStatusResponse {
        return rawMockWorkerApiClient.getProcessStatus(externalJobId)
    }
}
