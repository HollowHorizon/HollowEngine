package ru.hollowhorizon.hollowengine.common.scripting.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.nbt.CompoundTag
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class StateExecutor(val scope: CoroutineScope) {
    private val states = mutableMapOf<String, suspend () -> Unit>()

    fun addState(name: String, state: suspend () -> Unit) {
        states[name] = state
    }

    fun start(context: StateContext, start: CoroutineStart = CoroutineStart.DEFAULT) {
        context.executor = this

        context.job = scope.launch(context, start) {
            while (true) {
                val nextState = context.nextState ?: break
                states[nextState]?.invoke()

                context.children.values.forEach {
                    if (it.job.isActive) it.job.cancel()
                }
                context.tag.allKeys.forEach { key ->
                    states.remove(key)
                }

                if (nextState == context.nextState) break
            }
        }
    }
}

suspend fun async(name: String, start: CoroutineStart = CoroutineStart.DEFAULT, block: suspend () -> Unit): Job {
    val state = stateContext()
    val context = state.children.computeIfAbsent(name) {
        StateContext(CompoundTag(), nextState = name)
    }
    state.executor.addState(name, block)
    state.executor.start(context, start)
    return context.job
}

class StateContext(
    val tag: CompoundTag = CompoundTag(),
    val children: MutableMap<String, StateContext> = mutableMapOf(),
    var nextState: String? = null,
) : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<StateContext>

    internal lateinit var executor: StateExecutor
    internal lateinit var job: Job

    fun serialize(): CompoundTag {
        val tag = CompoundTag()
        nextState?.let { tag.putString("next_state", it) }
        tag.put("children", CompoundTag().apply {
            children.forEach { (name, context) -> put(name, context.serialize()) }
        })
        tag.put("self", this.tag)
        return tag
    }

    companion object {

        fun deserialize(serialized: CompoundTag): StateContext {
            val children = serialized.getCompound("children")
            return StateContext(
                serialized.getCompound("self"),
                children.allKeys.associateWith { deserialize(children.getCompound(it)) }.toMutableMap(),
                serialized.getString("next_state")
            )
        }
    }
}