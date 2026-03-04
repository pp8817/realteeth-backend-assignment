package ai.realteeth.imagejobserver.global.util

import ai.realteeth.imagejobserver.job.domain.JobStatus

object ProgressMapper {

    fun toProgress(status: JobStatus): Int = when (status) {
        JobStatus.RECEIVED -> 0
        JobStatus.QUEUED -> 10
        JobStatus.RUNNING -> 50
        JobStatus.SUCCEEDED -> 100
        JobStatus.FAILED -> 100
    }
}
