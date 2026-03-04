package ai.realteeth.imagejobserver.job.repository

import ai.realteeth.imagejobserver.job.domain.JobStatus
import org.springframework.dao.DataAccessException
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

        return if (supportsOnConflict()) {
            try {
                insertOrGetWithOnConflict(params)
            } catch (_: DataAccessException) {
                insertOrGetWithFallback(params)
            } catch (_: IllegalStateException) {
                insertOrGetWithFallback(params)
            }
        } else {
            insertOrGetWithFallback(params)
        }
    }

    private fun supportsOnConflict(): Boolean {
        dataSource.connection.use { connection ->
            val product = connection.metaData.databaseProductName
            return !product.contains("H2", ignoreCase = true)
        }
    }

    private fun insertOrGetWithOnConflict(params: MapSqlParameterSource): InsertOrGetJobResult {
        val sql = """
            WITH inserted AS (
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
            )
            SELECT i.id, i.status, TRUE AS created
            FROM inserted i
            UNION ALL
            SELECT j.id, j.status, FALSE AS created
            FROM job j
            WHERE NOT EXISTS (SELECT 1 FROM inserted)
              AND (
                  (:idempotency_key IS NOT NULL AND j.idempotency_key = :idempotency_key)
                  OR j.fingerprint = :fingerprint
              )
            LIMIT 1
        """.trimIndent()

        return querySingle(sql, params)
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
            val findSql = """
                SELECT id, status
                FROM job
                WHERE (
                    (:idempotency_key IS NOT NULL AND idempotency_key = :idempotency_key)
                    OR fingerprint = :fingerprint
                )
                LIMIT 1
            """.trimIndent()
            querySingle(findSql, params, created = false)
        }
    }

    private fun querySingle(
        sql: String,
        params: MapSqlParameterSource,
        created: Boolean? = null,
    ): InsertOrGetJobResult {
        return jdbcTemplate.query(sql, params) { rs, _ ->
            InsertOrGetJobResult(
                jobId = rs.getObject("id", UUID::class.java),
                status = JobStatus.valueOf(rs.getString("status")),
                created = created ?: rs.getBoolean("created"),
            )
        }.firstOrNull() ?: throw IllegalStateException("Failed to insert or retrieve job")
    }
}
