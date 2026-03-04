package ai.realteeth.imagejobserver.job.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "job_result")
class JobResultEntity(
    @Id
    @Column(name = "job_id", nullable = false)
    var jobId: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "job_id")
    var job: JobEntity? = null,

    @Column(name = "result_payload")
    var resultPayload: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 64)
    var errorCode: JobErrorCode? = null,

    @Column(name = "error_message")
    var errorMessage: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    var createdAt: Instant? = null,
)
