package ai.realteeth.imagejobserver.job.controller

import ai.realteeth.imagejobserver.job.domain.JobEntity
import ai.realteeth.imagejobserver.job.domain.JobStatus
import ai.realteeth.imagejobserver.job.repository.JobJpaRepository
import ai.realteeth.imagejobserver.job.repository.JobResultJpaRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var jobRepository: JobJpaRepository

    @Autowired
    private lateinit var jobResultRepository: JobResultJpaRepository

    @BeforeEach
    fun setUp() {
        jobResultRepository.deleteAll()
        jobRepository.deleteAll()
    }

    @Test
    fun `동일 idempotency key 요청은 기존 jobId를 반환한다`() {
        val requestBody = """
            {
              "imageUrl": "https://example.com/image.png"
            }
        """.trimIndent()

        val first = mockMvc.perform(
            post("/jobs")
                .header("Idempotency-Key", "idem-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        )
            .andExpect(status().isCreated)
            .andReturn()

        val second = mockMvc.perform(
            post("/jobs")
                .header("Idempotency-Key", "idem-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody),
        )
            .andExpect(status().isOk)
            .andReturn()

        val firstJson = objectMapper.readTree(first.response.contentAsString)
        val secondJson = objectMapper.readTree(second.response.contentAsString)

        assertEquals(firstJson.path("jobId").asText(), secondJson.path("jobId").asText())
        assertEquals(false, firstJson.path("deduped").asBoolean())
        assertEquals(true, secondJson.path("deduped").asBoolean())
    }

    @Test
    fun `SUCCEEDED 인데 결과 row가 없으면 result 조회는 500을 반환한다`() {
        val jobId = jobRepository.saveAndFlush(
            JobEntity(
                id = UUID.randomUUID(),
                status = JobStatus.SUCCEEDED,
                imageUrl = "https://example.com/succeeded-no-result.png",
                fingerprint = UUID.randomUUID().toString().replace("-", ""),
            ),
        ).id

        mockMvc.perform(get("/jobs/{jobId}/result", jobId))
            .andExpect(status().isInternalServerError)
    }

    @Test
    fun `list jobs의 page가 음수면 400을 반환한다`() {
        mockMvc.perform(get("/jobs").param("page", "-1"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `list jobs의 size가 범위를 벗어나면 400을 반환한다`() {
        mockMvc.perform(get("/jobs").param("size", "0"))
            .andExpect(status().isBadRequest)

        mockMvc.perform(get("/jobs").param("size", "101"))
            .andExpect(status().isBadRequest)
    }
}
