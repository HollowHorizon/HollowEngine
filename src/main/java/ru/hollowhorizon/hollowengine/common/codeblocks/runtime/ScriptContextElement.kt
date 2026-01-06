package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

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
suspend fun getVariable(name: String, isGlobal: Boolean): VariableContainer<*>? {
    val file = currentFile()
    val instance = currentInstance()
    return if (isGlobal) file.system.globals[name] else instance.localVariables[name]
}

@CodeBlocksDSL
suspend fun setVariable(name: String, isGlobal: Boolean, value: Any?) {
    val file = currentFile()
    val instance = currentInstance()
    val variable = (if (isGlobal) file.system.globals[name] else instance.localVariables[name])
        ?: error("Variable with name $name not found!")
    variable.set(JavaHacks.forceCast(value))
}