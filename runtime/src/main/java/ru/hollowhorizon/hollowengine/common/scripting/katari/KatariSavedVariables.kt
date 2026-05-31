package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.KatariState
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariStateSnapshot
import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder
import com.sunnychung.lib.multiplatform.kotlite.katari.StateSnapshotCodec
import com.sunnychung.lib.multiplatform.kotlite.katari.TaskState
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.model.CustomFunctionParameter
import com.sunnychung.lib.multiplatform.kotlite.model.NullValue
import com.sunnychung.lib.multiplatform.kotlite.model.RuntimeValue
import com.sunnychung.lib.multiplatform.kotlite.model.TypeParameter
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.VariableMap
import ru.hollowhorizon.hollowengine.common.coroutines.OwnerScope
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.KatariGeneratedBindingRuntime
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat

private const val SAVED_VALUE_TASK = "hollowengine:saved_variable"
private const val SAVED_VALUE_NAME = "value"

class KatariSavedVariables(
    private val server: MinecraftServer,
) {
    lateinit var snapshotCodec: StateSnapshotCodec

    fun serializeValue(value: RuntimeValue): CompoundTag {
        return serializeKatariSavedRuntimeValue(value, snapshotCodec)
    }

    suspend fun restoreValue(tag: CompoundTag): RuntimeValue {
        return restoreKatariSavedRuntimeValue(tag, snapshotCodec, KatariRestoreContext(server))
    }
}

internal fun serializeKatariSavedRuntimeValue(
    value: RuntimeValue,
    snapshotCodec: StateSnapshotCodec,
): CompoundTag {
    val snapshot = snapshotCodec.serialize(
        KatariState(
            programVersion = 0,
            tasks = listOf(TaskState(SAVED_VALUE_TASK, localVariables = mapOf(SAVED_VALUE_NAME to value))),
        )
    )
    return NBTFormat(snapshotCodec.serializersModule()).serialize(KatariStateSnapshot.serializer(), snapshot) as CompoundTag
}

internal suspend fun restoreKatariSavedRuntimeValue(
    tag: CompoundTag,
    snapshotCodec: StateSnapshotCodec,
    context: ValueRestoreContext,
): RuntimeValue {
    val snapshot = NBTFormat(snapshotCodec.serializersModule())
        .deserialize(KatariStateSnapshot.serializer(), tag)
    val state = snapshotCodec.restore(snapshot, context)
    return state.tasks.firstOrNull { it.id == SAVED_VALUE_TASK }
        ?.localVariables
        ?.get(SAVED_VALUE_NAME)
        ?: NullValue
}

fun NarrativeBindingsBuilder.registerSavedVariableBindings(savedVariables: KatariSavedVariables?) {
    registerSavedVariableAccessors(savedVariables, receiverType = "Server")
    registerSavedVariableAccessors(savedVariables, receiverType = "Entity")
}

@ScriptBinding("getOrCreate")
inline fun <T> MinecraftServer.scriptSavedGetOrCreate(key: String, defaultValue: () -> Any?): T =
    if (this.has(key)) this.get<T>(key) else {
        val value = defaultValue()
        this.set(key, value)
        this.get<T>(key)
    }

@ScriptBinding("getOrCreate")
inline fun <T> Entity.scriptSavedGetOrCreate(key: String, defaultValue: () -> Any?): T =
    if (this.has(key)) this.get<T>(key) else {
        val value = defaultValue()
        this.set(key, value)
        this.get<T>(key)
    }

@PublishedApi
internal fun MinecraftServer.has(key: String): Boolean {
    error("Katari saved has is available only inside Katari scripts")
}

@PublishedApi
internal fun <T> MinecraftServer.get(key: String): T {
    error("Katari saved get is available only inside Katari scripts")
}

@PublishedApi
internal fun <T> MinecraftServer.set(key: String, value: T) {
    error("Katari saved set is available only inside Katari scripts")
}

@PublishedApi
internal fun Entity.has(key: String): Boolean {
    error("Katari saved has is available only inside Katari scripts")
}

@PublishedApi
internal fun <T> Entity.get(key: String): T {
    error("Katari saved get is available only inside Katari scripts")
}

@PublishedApi
internal fun <T> Entity.set(key: String, value: T) {
    error("Katari saved set is available only inside Katari scripts")
}

private fun NarrativeBindingsBuilder.registerSavedVariableAccessors(
    savedVariables: KatariSavedVariables?,
    receiverType: String,
) {
    immediateFunction(
        name = "has",
        valueParameters = listOf(CustomFunctionParameter("key", "String")),
        receiverType = receiverType,
        returnType = "Boolean",
    ) { arguments, context ->
        val variables = savedVariables?.variables(arguments.first(), receiverType) ?: return@immediateFunction NullValue
        KatariGeneratedBindingRuntime.toRuntimeValue(
            variables.contains(KatariGeneratedBindingRuntime.asString(arguments.getOrNull(1), "key")),
            symbolTable = context.symbolTable,
        )
    }

    immediateFunction(
        name = "get",
        valueParameters = listOf(CustomFunctionParameter("key", "String")),
        receiverType = receiverType,
        returnType = "T",
        typeParameters = listOf(TypeParameter("T", null)),
    ) { arguments, _ ->
        val variables = savedVariables?.variables(arguments.first(), receiverType) ?: return@immediateFunction NullValue
        val key = KatariGeneratedBindingRuntime.asString(arguments.getOrNull(1), "key")
        val tag = variables.getTag(key) ?: error("$receiverType saved variable `$key` is not present")
        savedVariables.restoreValue(tag)
    }

    immediateFunction(
        name = "set",
        valueParameters = listOf(
            CustomFunctionParameter("key", "String"),
            CustomFunctionParameter("value", "T"),
        ),
        receiverType = receiverType,
        returnType = "Unit",
        typeParameters = listOf(TypeParameter("T", null)),
    ) { arguments, _ ->
        val variables = savedVariables?.variables(arguments.first(), receiverType) ?: return@immediateFunction NullValue
        val key = KatariGeneratedBindingRuntime.asString(arguments.getOrNull(1), "key")
        variables.setTag(key, savedVariables.serializeValue(arguments.getOrNull(2) ?: NullValue))
        savedVariables.markDirty(arguments.first(), receiverType)
        NullValue
    }

    immediateFunction(
        name = "remove",
        valueParameters = listOf(CustomFunctionParameter("key", "String")),
        receiverType = receiverType,
        returnType = "Unit",
    ) { arguments, _ ->
        val variables = savedVariables?.variables(arguments.first(), receiverType) ?: return@immediateFunction NullValue
        variables.remove(KatariGeneratedBindingRuntime.asString(arguments.getOrNull(1), "key"))
        savedVariables.markDirty(arguments.first(), receiverType)
        NullValue
    }
}

private suspend fun KatariSavedVariables.variables(receiver: RuntimeValue, receiverType: String): VariableMap {
    return when (receiverType) {
        "Server" -> KatariGeneratedBindingRuntime.awaitHost<MinecraftServer>(receiver, "Server", "receiver")
            .runtimeContext
            .scope
            .variables
        "Entity" -> {
            val entity = KatariGeneratedBindingRuntime.awaitHost<Entity>(receiver, "Entity", "receiver")
            val scope = entity.coroutineScope as? OwnerScope
                ?: error("Entity `${entity.uuid}` does not provide a persistent script scope")
            scope.variables
        }
        else -> error("Unsupported saved variable receiver `$receiverType`")
    }
}

private suspend fun KatariSavedVariables.markDirty(receiver: RuntimeValue, receiverType: String) {
    when (receiverType) {
        "Server" -> KatariGeneratedBindingRuntime.awaitHost<MinecraftServer>(receiver, "Server", "receiver")
            .runtimeContext
            .markDirty()
        "Entity" -> {
            val entity = KatariGeneratedBindingRuntime.awaitHost<Entity>(receiver, "Entity", "receiver")
            (entity.coroutineScope as? OwnerScope)?.markDirty()
        }
    }
}
