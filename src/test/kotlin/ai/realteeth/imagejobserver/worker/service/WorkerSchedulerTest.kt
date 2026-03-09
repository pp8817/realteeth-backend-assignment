package ai.realteeth.imagejobserver.worker.service

import ai.realteeth.imagejobserver.job.service.JobService
import ai.realteeth.imagejobserver.worker.config.WorkerProperties
import ai.realteeth.imagejobserver.worker.repository.WorkerClaimRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.UUID

class WorkerSchedulerTest {

    private val workerClaimRepository = mock<WorkerClaimRepository>()
    private val jobService = mock<JobService>()
    private val workerExecutionService = mock<WorkerExecutionService>()
    private val workerTaskExecutor = mock<ThreadPoolTaskExecutor>()
    private val executedTasks = mutableListOf<Runnable>()

    private val workerProperties = WorkerProperties(
        enabled = true,
        id = "worker-1",
        leaseSeconds = 30,
        batchSize = 10,
        maxAttempts = 3,
        pollIntervalMs = 1000,
        statusPollIntervalMs = 2000,
        maxProcessingSeconds = 1800,
        threads = 4,
    )

    private val scheduler = WorkerScheduler(
        workerProperties = workerProperties,
        workerClaimRepository = workerClaimRepository,
        jobService = jobService,
        workerExecutionService = workerExecutionService,
        workerTaskExecutor = workerTaskExecutor,
    )

    init {
        whenever(workerTaskExecutor.activeCount).thenReturn(0)
        whenever(workerTaskExecutor.queueSize).thenReturn(0)
        doAnswer {
            executedTasks += it.getArgument<Runnable>(0)
            null
        }.whenever(workerTaskExecutor).execute(any())
        whenever(workerClaimRepository.findStaleRunningJobsAtOrOverMaxAttempts(any(), any())).thenReturn(emptyList())
        whenever(workerClaimRepository.requeueStaleRunningJobs(any(), any())).thenReturn(emptyList())
    }

    @Test
    fun `dispatch capacity는 active와 queue를 모두 반영한다`() {
        assertCapacity(threads = 4, activeCount = 0, queuedTaskCount = 0, expectedAvailableSlots = 4)
        assertCapacity(threads = 4, activeCount = 2, queuedTaskCount = 0, expectedAvailableSlots = 2)
        assertCapacity(threads = 4, activeCount = 1, queuedTaskCount = 2, expectedAvailableSlots = 1)
        assertCapacity(threads = 4, activeCount = 2, queuedTaskCount = 2, expectedAvailableSlots = 0)
        assertCapacity(threads = 4, activeCount = 0, queuedTaskCount = 5, expectedAvailableSlots = 0)
    }

    @Test
    fun `executor queue에 대기 작업이 있으면 추가 claim을 하지 않는다`() {
        whenever(workerTaskExecutor.activeCount).thenReturn(1)
        whenever(workerTaskExecutor.queueSize).thenReturn(3)

        scheduler.pollAndDispatch()

        verify(workerClaimRepository, never()).claimPollReadyRunningJobs(any(), any(), any())
        verify(workerClaimRepository, never()).claimQueuedJobs(any(), any(), any())
        verifyNoInteractions(workerExecutionService)
    }

    @Test
    fun `available slot이 1개여도 poll-ready 작업을 먼저 claim한다`() {
        val pollReadyJobId = UUID.randomUUID()
        val limitedProperties = workerProperties.copy(threads = 1)
        val scheduler = WorkerScheduler(
            workerProperties = limitedProperties,
            workerClaimRepository = workerClaimRepository,
            jobService = jobService,
            workerExecutionService = workerExecutionService,
            workerTaskExecutor = workerTaskExecutor,
        )

        whenever(workerClaimRepository.claimPollReadyRunningJobs("worker-1", 30, 1))
            .thenReturn(listOf(pollReadyJobId))

        scheduler.pollAndDispatch()
        executedTasks.forEach { it.run() }

        verify(workerClaimRepository).claimPollReadyRunningJobs("worker-1", 30, 1)
        verify(workerClaimRepository, never()).claimQueuedJobs(any(), any(), any())
        verify(workerExecutionService).execute(eq(pollReadyJobId))
    }

    @Test
    fun `예약된 poll-ready slot이 비면 queued 작업이 남은 slot을 채운다`() {
        val queuedJobId = UUID.randomUUID()
        val limitedProperties = workerProperties.copy(threads = 1)
        val scheduler = WorkerScheduler(
            workerProperties = limitedProperties,
            workerClaimRepository = workerClaimRepository,
            jobService = jobService,
            workerExecutionService = workerExecutionService,
            workerTaskExecutor = workerTaskExecutor,
        )

        whenever(workerClaimRepository.claimPollReadyRunningJobs("worker-1", 30, 1))
            .thenReturn(emptyList())
        whenever(workerClaimRepository.claimQueuedJobs("worker-1", 30, 1))
            .thenReturn(listOf(queuedJobId))

        scheduler.pollAndDispatch()
        executedTasks.forEach { it.run() }

        verify(workerClaimRepository).claimPollReadyRunningJobs("worker-1", 30, 1)
        verify(workerClaimRepository).claimQueuedJobs("worker-1", 30, 1)
        verify(workerExecutionService).execute(eq(queuedJobId))
    }

    @Test
    fun `poll-ready와 queued backlog가 함께 있어도 양쪽이 균형 있게 claim된다`() {
        val pollReadyJobId = UUID.randomUUID()
        val queuedJobId1 = UUID.randomUUID()
        val queuedJobId2 = UUID.randomUUID()
        val queuedJobId3 = UUID.randomUUID()

        whenever(workerClaimRepository.claimPollReadyRunningJobs("worker-1", 30, 2))
            .thenReturn(listOf(pollReadyJobId))
        whenever(workerClaimRepository.claimQueuedJobs("worker-1", 30, 2))
            .thenReturn(listOf(queuedJobId1, queuedJobId2))
        whenever(workerClaimRepository.claimQueuedJobs("worker-1", 30, 1))
            .thenReturn(listOf(queuedJobId3))

        scheduler.pollAndDispatch()
        executedTasks.forEach { it.run() }

        verify(workerClaimRepository).claimPollReadyRunningJobs("worker-1", 30, 2)
        verify(workerClaimRepository).claimQueuedJobs("worker-1", 30, 2)
        verify(workerClaimRepository).claimQueuedJobs("worker-1", 30, 1)
        verify(workerExecutionService).execute(eq(pollReadyJobId))
        verify(workerExecutionService).execute(eq(queuedJobId1))
        verify(workerExecutionService).execute(eq(queuedJobId2))
        verify(workerExecutionService).execute(eq(queuedJobId3))
    }

    private fun assertCapacity(
        threads: Int,
        activeCount: Int,
        queuedTaskCount: Int,
        expectedAvailableSlots: Int,
    ) {
        val capacity = scheduler.calculateDispatchCapacity(
            threads = threads,
            activeCount = activeCount,
            queuedTaskCount = queuedTaskCount,
        )
        assertEquals(expectedAvailableSlots, capacity.availableSlots)
        assertEquals(activeCount.coerceAtLeast(0), capacity.activeCount)
        assertEquals(queuedTaskCount.coerceAtLeast(0), capacity.queuedTaskCount)
    }
}
