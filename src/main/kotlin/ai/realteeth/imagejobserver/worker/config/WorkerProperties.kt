package ai.realteeth.imagejobserver.worker.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.worker")
data class WorkerProperties(
    var enabled: Boolean = true,
    var id: String = "worker-1",
    var leaseSeconds: Int = 30,
    var batchSize: Int = 5,
    var maxAttempts: Int = 3,
    var pollIntervalMs: Long = 1000,
    var threads: Int = 4,
)
