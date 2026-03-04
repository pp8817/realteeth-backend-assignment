package ai.realteeth.imagejobserver.job.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "job")
class JobEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: JobStatus = JobStatus.RECEIVED,

    @Column(name = "image_url", nullable = false)
    var imageUrl: String = "",

    @Column(name = "idempotency_key", length = 128, unique = true)
    var idempotencyKey: String? = null,

    @Column(name = "fingerprint", length = 64, unique = true)
    var fingerprint: String? = null,

    @Column(name = "external_job_id", length = 128)
    var externalJobId: String? = null,

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,

    @Column(name = "locked_by", length = 128)
    var lockedBy: String? = null,

    @Column(name = "locked_until")
    var lockedUntil: Instant? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant? = null,
)
