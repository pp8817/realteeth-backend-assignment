package ai.realteeth.imagejobserver.job.repository

import ai.realteeth.imagejobserver.job.domain.JobStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import javax.sql.DataSource
import java.util.UUID

@Repository
class JobInsertJdbcRepository(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    private val dataSource: DataSource,
) : JobInsertRepository {

    private val supportsOnConflict: Boolean by lazy {
        dataSource.connection.use { connection ->
            val product = connection.metaData.databaseProductName
            !product.contains("H2", ignoreCase = true)
        }
    }

    override fun insertOrGet(
        jobId: UUID,
        imageUrl: String,
        idempotencyKey: String?,
        fingerprint: String,
    ): InsertOrGetJobResult {
        val params = MapSqlParameterSource()
            .addValue("job_id", jobId)
            .addValue("image_url", imageUrl)
            .addValue("idempotency_key", idempotencyKey)
            .addValue("fingerprint", fingerprint)

        return if (supportsOnConflict) {
            insertOrGetWithOnConflict(params)
        } else {
            insertOrGetWithFallback(params)
        }
    }

    private fun insertOrGetWithOnConflict(params: MapSqlParameterSource): InsertOrGetJobResult {
        val sql = """
            INSERT INTO job (
                id,
                status,
                image_url,
                idempotency_key,
                fingerprint,
                attempt_count,
                created_at,
                updated_at
            ) VALUES (
                :job_id,
                'RECEIVED',
                :image_url,
                :idempotency_key,
                :fingerprint,
                0,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT DO NOTHING
            RETURNING id, status
        """.trimIndent()

        val inserted = jdbcTemplate.query(sql, params) { rs, _ ->
            InsertOrGetJobResult(
                jobId = rs.getObject("id", UUID::class.java),
                status = JobStatus.valueOf(rs.getString("status")),
                created = true,
            )
        }.firstOrNull()

        if (inserted != null) {
            return inserted
        }

        // Another transaction may have inserted the same key/fingerprint and committed just after
        // this statement snapshot. Retry short lookup to absorb that visibility race.
        return findExistingWithRetry(params)
    }

    private fun insertOrGetWithFallback(params: MapSqlParameterSource): InsertOrGetJobResult {
        val insertSql = """
            INSERT INTO job (
                id,
                status,
                image_url,
                idempotency_key,
                fingerprint,
                attempt_count,
                created_at,
                updated_at
            ) VALUES (
                :job_id,
                'RECEIVED',
                :image_url,
                :idempotency_key,
                :fingerprint,
                0,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
        """.trimIndent()

        return try {
            jdbcTemplate.update(insertSql, params)
            InsertOrGetJobResult(
                jobId = params.getValue("job_id") as UUID,
                status = JobStatus.RECEIVED,
                created = true,
            )
        } catch (_: DataIntegrityViolationException) {
            findExistingWithRetry(params)
        }
    }

    private fun findExistingWithRetry(
        params: MapSqlParameterSource,
    ): InsertOrGetJobResult {
        repeat(20) { index ->
            findExisting(params)?.let { return it }

            if (index < 19) {
                try {
                    Thread.sleep(10)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IllegalStateException("Interrupted while waiting for duplicate row visibility", ie)
                }
            }
        }

        throw IllegalStateException("Failed to insert or retrieve job")
    }

    private fun findExisting(params: MapSqlParameterSource): InsertOrGetJobResult? {
        val idempotencyKey = params.getValue("idempotency_key") as String?
        val fingerprint = params.getValue("fingerprint") as String

        val findSql: String
        val findParams: MapSqlParameterSource
        if (idempotencyKey.isNullOrBlank()) {
            findSql = """
                SELECT id, status
                FROM job
                WHERE fingerprint = :fingerprint
                LIMIT 1
            """.trimIndent()
            findParams = MapSqlParameterSource()
                .addValue("fingerprint", fingerprint)
        } else {
            findSql = """
                SELECT id, status
                FROM job
                WHERE idempotency_key = :idempotency_key
                   OR fingerprint = :fingerprint
                LIMIT 1
            """.trimIndent()
            findParams = MapSqlParameterSource()
                .addValue("idempotency_key", idempotencyKey)
                .addValue("fingerprint", fingerprint)
        }

        return jdbcTemplate.query(findSql, findParams) { rs, _ ->
            InsertOrGetJobResult(
                jobId = rs.getObject("id", UUID::class.java),
                status = JobStatus.valueOf(rs.getString("status")),
                created = false,
            )
        }.firstOrNull()
    }
}
