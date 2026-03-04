package ai.realteeth.imagejobserver.worker.repository

import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.FileSystemResource
import org.springframework.stereotype.Component

@Component
class ClaimSqlProvider {

    private lateinit var _claimQueuedSql: String

    private lateinit var _requeueStaleSql: String

    private lateinit var _extendLeaseSql: String

    private lateinit var _selectStaleExhaustedSql: String

    val claimQueuedSql: String
        get() = _claimQueuedSql

    val requeueStaleSql: String
        get() = _requeueStaleSql

    val extendLeaseSql: String
        get() = _extendLeaseSql

    val selectStaleExhaustedSql: String
        get() = _selectStaleExhaustedSql

    @PostConstruct
    fun init() {
        val script = loadScript()
        val statements = script
            .lineSequence()
            .filterNot { it.trim().startsWith("--") }
            .joinToString("\n")
            .split(';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { "$it;" }

        require(statements.size >= 4) { "db/claim.sql must contain at least 4 SQL statements" }

        _claimQueuedSql = statements[0]
        _requeueStaleSql = statements[1]
        _extendLeaseSql = statements[2]
        _selectStaleExhaustedSql = statements[3]
    }

    private fun loadScript(): String {
        val fileResource = FileSystemResource("db/claim.sql")
        if (fileResource.exists()) {
            return fileResource.inputStream.bufferedReader().use { it.readText() }
        }

        val classPathResource = ClassPathResource("db/claim.sql")
        if (classPathResource.exists()) {
            return classPathResource.inputStream.bufferedReader().use { it.readText() }
        }

        throw IllegalStateException("Unable to find db/claim.sql")
    }
}
