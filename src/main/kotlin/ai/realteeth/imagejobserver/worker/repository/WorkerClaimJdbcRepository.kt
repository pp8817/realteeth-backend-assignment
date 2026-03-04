package ai.realteeth.imagejobserver.worker.repository

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class WorkerClaimJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val claimSqlProvider: ClaimSqlProvider,
) : WorkerClaimRepository {

    override fun claimQueuedJobs(workerId: String, leaseSeconds: Int, batchSize: Int): List<UUID> {
        val params = MapSqlParameterSource()
            .addValue("worker_id", workerId)
            .addValue("lease_seconds", leaseSeconds)
            .addValue("batch_size", batchSize)

        return jdbcTemplate.query(claimSqlProvider.claimQueuedSql, params) { rs, _ ->
            rs.getObject("id", UUID::class.java)
        }
    }

    override fun requeueStaleRunningJobs(batchSize: Int, maxAttempts: Int): List<UUID> {
        val params = MapSqlParameterSource()
            .addValue("batch_size", batchSize)
            .addValue("max_attempts", maxAttempts)

        return jdbcTemplate.query(claimSqlProvider.requeueStaleSql, params) { rs, _ ->
            rs.getObject("id", UUID::class.java)
        }
    }

    override fun findStaleRunningJobsAtOrOverMaxAttempts(batchSize: Int, maxAttempts: Int): List<UUID> {
        val params = MapSqlParameterSource()
            .addValue("batch_size", batchSize)
            .addValue("max_attempts", maxAttempts)

        return jdbcTemplate.query(claimSqlProvider.selectStaleExhaustedSql, params) { rs, _ ->
            rs.getObject("id", UUID::class.java)
        }
    }

    override fun extendLease(jobId: UUID, workerId: String, leaseSeconds: Int): Boolean {
        val params = MapSqlParameterSource()
            .addValue("job_id", jobId)
            .addValue("worker_id", workerId)
            .addValue("lease_seconds", leaseSeconds)

        return jdbcTemplate.query(claimSqlProvider.extendLeaseSql, params) { rs, _ ->
            rs.getObject("id", UUID::class.java)
        }.isNotEmpty()
    }
}
