package ai.realteeth.imagejobserver.job.repository

import ai.realteeth.imagejobserver.job.domain.JobEntity
import ai.realteeth.imagejobserver.job.domain.JobStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface JobJpaRepository : JpaRepository<JobEntity, UUID> {

    fun findByIdempotencyKey(idempotencyKey: String): JobEntity?

    fun findByFingerprint(fingerprint: String): JobEntity?

    fun findAllByStatus(status: JobStatus, pageable: Pageable): Page<JobEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from JobEntity j where j.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): JobEntity?
}
