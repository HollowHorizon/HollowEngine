import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.EventBus
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
        graph.start(this)
        graph.updateAwait(this)
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

        graph.start(this)
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
        graph.start(this)
        graph.updateAwait(this)

        graph.transition("B")
        graph.updateAwait(this) // EXIT A

        assertTrue("Transition must be canceled", canceled)
        assertEquals("A", graph.currentState.name, "State must be A")
        assertEquals(Status.UPDATE, graph.currentState.status, "State A must be in UPDATE status")
    }

    @Test
    fun `Events in scope`() = runTest {
        class TestEvent : Event

        var catched = false
        val graph = graph {
            eventScope(this@runTest)
            initialState("A")
            state("A") {
                onEnter {
                    println("Enter A in thread ${Thread.currentThread().name}")
                }

                on<TestEvent> {
                    catched = true
                }

            }
            state("B") {

            }
        }
        graph.start(this)
        graph.updateAwait(this)
        graph.updateAwait(this)

        EventBus.post(TestEvent())
        graph.await()
        assertTrue("Event must be catched in state A", catched)
        catched = false

        graph.transition("B")
        graph.updateAwait(this)
        graph.updateAwait(this)

        EventBus.post(TestEvent())
        graph.await()
        assertTrue("Transition must be not catched in state B", !catched)
    }

    @Test
    fun `Saving and Loading context of graph`() = runTest {
        val graph = graph {
            var counter by remember { 0 }

            initialState("A")

            state("A") {
                onEnter {
                    println("Counter in A: ${counter++}")
                    transition("B")
                }
            }

            state("B") {
                onEnter {
                    println("Counter in B: $counter")
                    counter = 32_30_10
                }
            }
        }

        graph.start(this)
        graph.updateAwait(this)
        graph.updateAwait(this)
        graph.updateAwait(this)
        graph.updateAwait(this)
        assertEquals(graph.serialize().getCompound("variables").getInt("counter"), 32_30_10, "Variable 'counter' must be equal '323010'")
    }

    // TODO: Нужно сделать более продвинутый обработчик для тестов, чем спамить по 2-4 итерации, пока он дойдёт до нужного шага
    suspend fun Graph.updateAwait(scope: CoroutineScope) {
        update(scope)
        await()
    }
}