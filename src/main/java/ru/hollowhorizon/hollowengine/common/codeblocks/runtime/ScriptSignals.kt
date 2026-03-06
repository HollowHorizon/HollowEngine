package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksDSL
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

enum class SignalScope {
    LOCAL,
    GLOBAL,
}

data class ScriptSignal(
    val name: String,
    val scope: SignalScope,
    val owner: OwnerKey,
    val sourceScriptPath: String,
    val payload: Any? = null,
)

class ScriptSignalContextElement(
    val signal: ScriptSignal,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ScriptSignalContextElement>
}

@CodeBlocksDSL
suspend fun currentScriptSignal(): ScriptSignal? = coroutineContext[ScriptSignalContextElement]?.signal

interface ScriptSignalHandler {
    val signalName: String
    val signalScope: SignalScope
}
