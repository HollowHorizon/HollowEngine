package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.events.Event
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class SkipScriptEventExecution : RuntimeException()

interface EventDrivenStartBlock<E : Event> {
    val eventType: Class<E>

    fun shouldHandle(event: E): Boolean = true

    fun resolveScopeEntity(event: E): Entity?
}

class ScriptEventContextElement(
    val event: Event,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ScriptEventContextElement>
}

suspend inline fun <reified E : Event> currentScriptEvent(): E? {
    val event = coroutineContext[ScriptEventContextElement]?.event ?: return null
    return event as? E
}

fun skipScriptEventExecution(): Nothing = throw SkipScriptEventExecution()
