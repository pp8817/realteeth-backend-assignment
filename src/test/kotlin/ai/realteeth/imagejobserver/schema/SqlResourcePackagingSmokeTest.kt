package ai.realteeth.imagejobserver.schema

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class SqlResourcePackagingSmokeTest {

    @Test
    fun `worker와 schema SQL 파일이 classpath에 포함된다`() {
        listOf(
            "db/schema.sql",
            "db/claim.sql",
            "db/claim-queued.sql",
            "db/claim-poll-ready.sql",
            "db/requeue-stale.sql",
            "db/extend-lease.sql",
            "db/select-stale-exhausted.sql",
        ).forEach { path ->
            assertTrue(ClassPathResource(path).exists(), "Classpath SQL resource missing: $path")
        }
    }
}
