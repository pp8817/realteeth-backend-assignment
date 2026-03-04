package ai.realteeth.imagejobserver.global.config

import ai.realteeth.imagejobserver.worker.config.WorkerProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
@Configuration
class WorkerExecutorConfig {

    @Bean("workerTaskExecutor")
    fun workerTaskExecutor(properties: WorkerProperties): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = properties.threads
        executor.maxPoolSize = properties.threads
        executor.setQueueCapacity(properties.batchSize * 10)
        executor.setThreadNamePrefix("job-worker-")
        executor.initialize()
        return executor
    }
}
