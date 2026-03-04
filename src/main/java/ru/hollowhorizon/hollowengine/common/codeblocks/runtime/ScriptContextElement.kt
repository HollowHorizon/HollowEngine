package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksDSL
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.VariableContainer
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class ScriptContextElement(val instance: ScriptInstance) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ScriptContextElement>
}

@CodeBlocksDSL
suspend fun currentInstance(): ScriptInstance {
    return coroutineContext[ScriptContextElement.Key]?.instance
        ?: error("Block executed outside of ScriptInstance context!")
}

@CodeBlocksDSL
suspend fun currentFile(): ScriptFile = currentInstance().ownerFile

@CodeBlocksDSL
suspend fun currentServer(): MinecraftServer = currentFile().system.owner

@CodeBlocksDSL
suspend fun getVariable(name: String): VariableContainer<*>? {
    val instance = currentInstance()
    return instance.localVariables[name]
}

@Deprecated("Global variables are removed. Use local variables only.")
@CodeBlocksDSL
suspend fun getVariable(name: String, isGlobal: Boolean): VariableContainer<*>? {
    if (isGlobal) return null
    return getVariable(name)
}

@CodeBlocksDSL
suspend fun setVariable(name: String, value: Any?) {
    val instance = currentInstance()
    val variable = instance.localVariables[name]
        ?: error("Variable with name $name not found!")
    variable.set(JavaHacks.forceCast(value))
}

@Deprecated("Global variables are removed. Use local variables only.")
@CodeBlocksDSL
suspend fun setVariable(name: String, isGlobal: Boolean, value: Any?) {
    if (isGlobal) error("Global variables are removed")
    setVariable(name, value)
}
