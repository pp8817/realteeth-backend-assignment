package ai.realteeth.imagejobserver.schema

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.sql.Connection

@Testcontainers(disabledWithoutDocker = true)
class SchemaSqlMigrationSmokeTest {

    @Test
    fun `기존 job 테이블에도 next_poll_at과 processing_started_at 컬럼이 idempotent하게 보강된다`() {
        postgres.createConnection("").use { connection ->
            applyLegacySchema(connection)
            applyCurrentSchema(connection)
            assertColumnExists(connection, "job", "next_poll_at")
            assertColumnExists(connection, "job", "processing_started_at")

            applyCurrentSchema(connection)
            assertColumnExists(connection, "job", "next_poll_at")
            assertColumnExists(connection, "job", "processing_started_at")
        }
    }

    private fun applyLegacySchema(connection: Connection) {
        val sql = """
            CREATE TABLE job (
                id UUID PRIMARY KEY,
                status VARCHAR(32) NOT NULL,
                image_url TEXT NOT NULL,
                idempotency_key VARCHAR(128) NULL,
                fingerprint VARCHAR(64) NULL,
                external_job_id VARCHAR(128) NULL,
                attempt_count INT NOT NULL DEFAULT 0,
                locked_by VARCHAR(128) NULL,
                locked_until TIMESTAMPTZ NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            );
            CREATE TABLE job_result (
                job_id UUID PRIMARY KEY REFERENCES job(id) ON DELETE CASCADE,
                result_payload TEXT NULL,
                error_code VARCHAR(64) NULL,
                error_message TEXT NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            );
        """.trimIndent()

        connection.createStatement().use { statement ->
            sql.split(';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach(statement::execute)
        }
    }

    private fun applyCurrentSchema(connection: Connection) {
        val statements = File("db/schema.sql")
            .readText()
            .split(";;")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        connection.createStatement().use { statement ->
            statements.forEach(statement::execute)
        }
    }

    private fun assertColumnExists(connection: Connection, table: String, column: String) {
        val exists = connection.prepareStatement(
            """
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = ?
              AND column_name = ?
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, table)
            ps.setString(2, column)
            ps.executeQuery().use { rs -> rs.next() }
        }

        assertTrue(exists, "Expected column $column to exist on $table")
    }

    companion object {
        @Container
        @JvmStatic
        private val postgres = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("image_jobs")
            .withUsername("image_jobs")
            .withPassword("image_jobs")
    }
}
