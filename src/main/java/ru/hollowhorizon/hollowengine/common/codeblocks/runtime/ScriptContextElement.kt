package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksDSL
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.deserializeVariableValue
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.serializeVariableValue
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import ru.hollowhorizon.hollowengine.common.coroutines.OwnerScope
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
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
suspend fun getVariable(name: String, expectedType: ExpressionType): Any? {
    return readTypedVariable(currentInstance().localVariables, name, expectedType)
}

@CodeBlocksDSL
suspend fun getVariable(name: String, scope: VariableScope, expectedType: ExpressionType): Any? {
    val instance = currentInstance()
    return when (scope) {
        VariableScope.LOCAL -> readTypedVariable(instance.localVariables, name, expectedType)
        VariableScope.GLOBAL -> readTypedVariable(currentServer().runtimeContext.scope.variables, name, expectedType)
        VariableScope.ENTITY -> {
            val entityScope = instance.ownerFile.system.ownerScope(instance.ownerKey) as? EntityScope
                ?: error("Entity variables are available only for entity-scoped script executions")
            entityScope.variables.getTag(name)
        }
    }
}

@CodeBlocksDSL
fun getVariable(name: String, entity: Entity): CompoundTag? {
    return (entity.coroutineScope as? OwnerScope)?.variables?.getTag(name)
}

@Deprecated("Use VariableScope instead of boolean flags")
@CodeBlocksDSL
suspend fun getVariable(name: String, isGlobal: Boolean, expectedType: ExpressionType): Any? {
    return getVariable(name, if (isGlobal) VariableScope.GLOBAL else VariableScope.LOCAL, expectedType)
}

@CodeBlocksDSL
suspend fun setVariable(name: String, value: Any?) {
    writeTypedVariable(currentInstance().localVariables, name, value)
    currentInstance().ownerFile.system.markDirty()
}

@CodeBlocksDSL
suspend fun setVariable(name: String, scope: VariableScope, value: Any?, fallbackType: ExpressionType? = null) {
    val instance = currentInstance()
    when (scope) {
        VariableScope.LOCAL -> writeTypedVariable(instance.localVariables, name, value)
        VariableScope.GLOBAL -> {
            writeTypedVariable(currentServer().runtimeContext.scope.variables, name, value)
            currentServer().runtimeContext.markDirty()
        }
        VariableScope.ENTITY -> {
            val entityScope = instance.ownerFile.system.ownerScope(instance.ownerKey) as? EntityScope
                ?: error("Entity variables are available only for entity-scoped script executions")
            val tag = value as? CompoundTag ?: error("Entity variable '$name' expects CompoundTag, got ${value?.javaClass?.name}")
            entityScope.variables.setTag(name, tag)
            entityScope.markDirty()
        }
    }
    instance.ownerFile.system.markDirty()
}

@CodeBlocksDSL
fun setVariable(name: String, entity: Entity, value: CompoundTag) {
    val scope = entity.coroutineScope as? OwnerScope ?: error("Entity does not provide an OwnerScope")
    scope.variables.setTag(name, value)
    scope.markDirty()
}

@Deprecated("Use VariableScope instead of boolean flags")
@CodeBlocksDSL
suspend fun setVariable(name: String, isGlobal: Boolean, value: Any?) {
    setVariable(name, if (isGlobal) VariableScope.GLOBAL else VariableScope.LOCAL, value)
}

private suspend fun readTypedVariable(variables: VariableMap, name: String, expectedType: ExpressionType): Any? {
    return deserializeVariableValue(variables.getRawTag(name), expectedType, currentServer())
}

private fun writeTypedVariable(variables: VariableMap, name: String, value: Any?) {
    variables.setRawTag(name, serializeVariableValue(value))
}
