import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.hollowhorizon.hollowengine.common.graph.Graph
import ru.hollowhorizon.hollowengine.common.graph.Status
import ru.hollowhorizon.hollowengine.common.graph.graph
import kotlin.test.assertEquals

class GraphTest {
    @Test
    fun `Graph starts with initial state`() = runTest {
        val enterCalled = mutableListOf<String>()
        val graph = graph {
            initialState("A")
            state("A") {
                onEnter {
                    enterCalled += "enterA"
                }
            }
            state("B") {
                onEnter {
                    enterCalled += "enterB"
                }
            }
        }
        graph.start()
        graph.updateAwait(this)

        assertEquals("A", graph.currentState.name)
        assertTrue("enterA" in enterCalled)
    }

    @Test
    fun `Transition moves to next state`() = runTest {
        val entered = mutableListOf<String>()
        val exited = mutableListOf<String>()

        val graph = graph {
            initialState("A")
            state("A") {
                onEnter {
                    entered.add("A")
                }

                onExit {
                    exited.add("A")
                }
            }
            state("B") {
                onEnter {
                    entered.add("B")
                }

                onExit {
                    exited.add("B")
                }
            }
        }

        graph.start()
        graph.updateAwait(this)

        graph.transition("B")
        graph.updateAwait(this) // EXIT A
        graph.updateAwait(this) // ENTER B

        assertEquals("B", graph.currentState.name, "Current state should be B after transition")
        assertTrue("State A must be exited", "A" in exited)
        assertTrue("State B must be entered", "B" in entered)
    }

    @Test
    fun `Exit can be canceled`() = runTest {
        var canceled = false

        val graph = graph {
            initialState("A")
            state("A") {
                onExit {
                    cancel()
                    canceled = true
                }
            }
            state("B") {

            }
        }
        graph.start()
        graph.updateAwait(this)

        graph.transition("B")
        graph.updateAwait(this) // EXIT A

        assertTrue("Transition must be canceled", canceled)
        assertEquals( "A", graph.currentState.name, "State must be A")
        assertEquals(Status.UPDATE, graph.currentState.status, "State A must be in UPDATE status")
    }

    suspend fun Graph.updateAwait(scope: CoroutineScope) {
        update(scope)
        await()
    }
}