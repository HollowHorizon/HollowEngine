import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.hollowhorizon.hollowengine.common.coroutines.SingleThreadDispatcher
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SingleThreadDispatcherTests {
    @Test
    fun `timeout executes on target tick`() {
        val dispatcher = SingleThreadDispatcher("test", Thread.currentThread())
        var hits = 0

        dispatcher.invokeOnTimeout(50, Runnable { hits++ }, EmptyCoroutineContext)
        dispatcher.runTasks()

        assertEquals(1, hits)
    }

    @Test
    fun `shutdown drops queued and delayed work`() {
        val dispatcher = SingleThreadDispatcher("test", Thread.currentThread())
        var immediateRan = false
        var delayedRan = false

        dispatcher.dispatch(EmptyCoroutineContext, Runnable { immediateRan = true })
        dispatcher.invokeOnTimeout(50, Runnable { delayedRan = true }, EmptyCoroutineContext)

        dispatcher.shutdown()
        repeat(3) { dispatcher.runTasks() }

        assertFalse(immediateRan)
        assertFalse(delayedRan)
    }

    @Test
    fun `delay continuation does not resume after shutdown`() {
        val dispatcher = SingleThreadDispatcher("test", Thread())
        var completed = false

        val job = CoroutineScope(Job() + dispatcher).launch {
            delay(50)
            completed = true
        }

        dispatcher.runTasks()
        dispatcher.shutdown()
        repeat(3) { dispatcher.runTasks() }

        assertFalse(completed)
        job.cancel()
    }
}
