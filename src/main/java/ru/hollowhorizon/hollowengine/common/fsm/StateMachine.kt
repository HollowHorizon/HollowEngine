package ru.hollowhorizon.hollowengine.common.fsm

import kotlinx.coroutines.*
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.scripting.scene.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

class StateContext(
    private val machine: StateMachine,
    val name: String,
) {
    fun transition(target: String) {
        machine.nextState = target
    }

}

data class StateDefinition(
    val name: String,
    val loop: Boolean,
    val block: suspend StateContext.() -> Unit,
)

open class StateMachine(
    private val scope: CoroutineScope = currentServer.coroutineScope,
) {
    private val states = mutableMapOf<String, StateDefinition>()
    internal lateinit var current: String
    internal var nextState: String? = null
    internal var onFinish = {}

    fun <T> async(
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> T,
    ): Deferred<T> = scope.async(start = start, block = block)

    fun state(
        name: String,
        loop: Boolean = false,
        block: suspend StateContext.() -> Unit,
    ) {
        states[name] = StateDefinition(name, loop, block)
    }

    fun start(initial: String = current) = scope.launch {
        current = initial
        while (true) {
            nextState = null
            val def = states[current]
                ?: error("State '$current' is not defined")

            // Исполнение состояния
            val ctx = StateContext(this@StateMachine, current)
            try {
                def.block.invoke(ctx)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HollowCore.LOGGER.error("Error in state '$current': ${'$'}e")
                break
            }

            if (nextState != null) current = nextState!!
            else if (!def.loop) break
        }
        onFinish()
    }

    open fun serialize(tag: CompoundTag) {
        tag.putString("\$state", current)
    }

    open fun deserialize(tag: CompoundTag) {
        current = tag.getString("\$state")
    }
}