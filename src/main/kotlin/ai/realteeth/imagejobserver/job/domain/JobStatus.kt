package ai.realteeth.imagejobserver.job.domain

enum class JobStatus {
    RECEIVED,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    ;

    fun isFinal(): Boolean = this == SUCCEEDED || this == FAILED
}
