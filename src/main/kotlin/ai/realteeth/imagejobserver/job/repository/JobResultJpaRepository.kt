package ai.realteeth.imagejobserver.job.repository

import ai.realteeth.imagejobserver.job.domain.JobResultEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JobResultJpaRepository : JpaRepository<JobResultEntity, UUID> {

    fun findByJobId(jobId: UUID): JobResultEntity?
}
