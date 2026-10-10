package ru.hollowhorizon.hollowengine.common.scripting.nodes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NodeLifecycleTest {
    @Test
    fun `replacement waits for cleanup and onStop completion`() = runTest {
        val events = mutableListOf<String>()
        val node = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
        node.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    delay(10)
                    events += "cleanup"
                }
            }
        }
        node.coroutineContext.job.invokeOnCompletion { events += "stop" }
        runCurrent()

        val lifecycle = NodeLifecycle { this }
        lifecycle.stop("node", node.coroutineContext.job)
        lifecycle.start("node") { events += "start" }
        runCurrent()
        assertTrue(events.isEmpty())
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("cleanup", "stop", "start"), events)
        assertFalse(lifecycle.isPending("node"))
    }

    @Test
    fun `detaching a node cancels its queued restart`() = runTest {
        val node = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
        val stopping = node.coroutineContext.job
        val child = node.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { delay(10) }
            }
        }
        runCurrent()
        var starts = 0
        val lifecycle = NodeLifecycle { this }
        lifecycle.stop("node", stopping)
        lifecycle.start("node") { starts++ }
        lifecycle.cancelStart("node")
        testScheduler.advanceUntilIdle()

        assertTrue(child.isCompleted)
        assertEquals(0, starts)
        assertTrue(lifecycle.pendingPaths().isEmpty())
    }

    @Test
    fun `repeated restart requests only start the latest replacement`() = runTest {
        val node = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
        val stopping = node.coroutineContext.job
        node.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { delay(10) }
            }
        }
        runCurrent()
        val starts = mutableListOf<Int>()
        val lifecycle = NodeLifecycle { this }
        lifecycle.stop("node", stopping)
        lifecycle.start("node") { starts += 1 }
        lifecycle.start("node") { starts += 2 }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(2), starts)
    }

    @Test
    fun `removing the owner cancels pending starts without resurrecting its nodes`() = runTest {
        val node = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
        node.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { delay(10) }
            }
        }
        runCurrent()
        val owner = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
        val lifecycle = NodeLifecycle { owner }
        var starts = 0
        lifecycle.stop("node", node.coroutineContext.job)
        lifecycle.start("node") { starts++ }
        owner.cancel()
        testScheduler.advanceUntilIdle()

        lifecycle.start("node") { starts++ }
        assertEquals(0, starts)
        assertTrue(lifecycle.pendingPaths().isEmpty())
    }
}
