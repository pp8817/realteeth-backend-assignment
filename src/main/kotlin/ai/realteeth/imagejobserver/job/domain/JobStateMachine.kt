package ai.realteeth.imagejobserver.job.domain

import ai.realteeth.imagejobserver.global.exception.IllegalStateTransitionException

object JobStateMachine {

    private val allowedTransitions: Map<JobStatus, Set<JobStatus>> = mapOf(
        JobStatus.RECEIVED to setOf(JobStatus.QUEUED),
        JobStatus.QUEUED to setOf(JobStatus.RUNNING),
        JobStatus.RUNNING to setOf(JobStatus.SUCCEEDED, JobStatus.FAILED, JobStatus.QUEUED),
        JobStatus.SUCCEEDED to emptySet(),
        JobStatus.FAILED to emptySet(),
    )

    fun requireTransition(from: JobStatus, to: JobStatus) {
        if (from == to) {
            return
        }

        val allowed = allowedTransitions[from].orEmpty()
        if (!allowed.contains(to)) {
            throw IllegalStateTransitionException("Invalid transition: $from -> $to")
        }
    }
}
