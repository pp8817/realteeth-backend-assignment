package ai.realteeth.imagejobserver.global.config

import ai.realteeth.imagejobserver.client.mockworker.MockWorkerProperties
import ai.realteeth.imagejobserver.worker.config.WorkerProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
@EnableConfigurationProperties(value = [WorkerProperties::class, MockWorkerProperties::class])
class AppConfig
