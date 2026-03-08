package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksDSL
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.createContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.VariableContainer
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import ru.hollowhorizon.hollowengine.common.coroutines.OwnerScope
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

class ScriptContextElement(val instance: ScriptInstance) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ScriptContextElement>
}

enum class VariableScope {
    LOCAL,
    GLOBAL,
    ENTITY,
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
suspend fun getVariable(name: String): VariableContainer? = currentInstance().localVariables[name]

@CodeBlocksDSL
suspend fun getVariable(name: String, scope: VariableScope): VariableContainer? {
    val instance = currentInstance()
    return when (scope) {
        VariableScope.LOCAL -> instance.localVariables[name]
        VariableScope.GLOBAL -> currentServer().runtimeContext.scope.variables[name]
        VariableScope.ENTITY -> (instance.ownerFile.system.ownerScope(instance.ownerKey) as? EntityScope)?.variables?.get(name)
    }
}

@CodeBlocksDSL
fun getVariable(name: String, entity: Entity): VariableContainer? {
    return (entity.coroutineScope as? OwnerScope)?.variables?.get(name)
}

@Deprecated("Use VariableScope instead of boolean flags")
@CodeBlocksDSL
suspend fun getVariable(name: String, isGlobal: Boolean): VariableContainer? {
    return getVariable(name, if (isGlobal) VariableScope.GLOBAL else VariableScope.LOCAL)
}

@CodeBlocksDSL
suspend fun setVariable(name: String, value: Any?) {
    setVariable(name, VariableScope.LOCAL, value)
}

@CodeBlocksDSL
suspend fun setVariable(name: String, scope: VariableScope, value: Any?, fallbackType: ExpressionType? = null) {
    val instance = currentInstance()
    val variables = when (scope) {
        VariableScope.LOCAL -> instance.localVariables
        VariableScope.GLOBAL -> currentServer().runtimeContext.scope.variables
        VariableScope.ENTITY -> (instance.ownerFile.system.ownerScope(instance.ownerKey) as? EntityScope)?.variables
            ?: error("Entity variables are available only for entity-scoped script executions")
    }
    val variable = variables.getOrPut(name) { createContainer(fallbackType ?: error("Variable type for '$name' is unknown")) }
    variable.set(JavaHacks.forceCast(value))
    instance.ownerFile.system.markDirty()
    if (scope == VariableScope.GLOBAL) currentServer().runtimeContext.markDirty()
}

@CodeBlocksDSL
fun setVariable(name: String, entity: Entity, value: Any?, fallbackType: ExpressionType? = null) {
    val scope = entity.coroutineScope as? OwnerScope ?: error("Entity does not provide an OwnerScope")
    val variable = scope.variables.getOrPut(name) { createContainer(fallbackType ?: error("Variable type for '$name' is unknown")) }
    variable.set(JavaHacks.forceCast(value))
    scope.markDirty()
}

@Deprecated("Use VariableScope instead of boolean flags")
@CodeBlocksDSL
suspend fun setVariable(name: String, isGlobal: Boolean, value: Any?) {
    setVariable(name, if (isGlobal) VariableScope.GLOBAL else VariableScope.LOCAL, value)
}
