package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import kotlinx.coroutines.currentCoroutineContext
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksDSL
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

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
) {
    fun serialize(): CompoundTag = CompoundTag().apply {
        putString("name", name)
        putString("scope", scope.name)
        put("owner", serializeSignalOwner(owner))
        putString("source", sourceScriptPath)
        when (payload) {
            null -> Unit
            is String -> putString("payload_string", payload)
            is Int -> putInt("payload_int", payload)
            is Long -> putLong("payload_long", payload)
            is Double -> putDouble("payload_double", payload)
            is Float -> putFloat("payload_float", payload)
            is Boolean -> putBoolean("payload_bool", payload)
        }
    }

    companion object {
        fun deserialize(tag: CompoundTag): ScriptSignal {
            val payload: Any? = when {
                tag.contains("payload_string") -> tag.getString("payload_string")
                tag.contains("payload_int") -> tag.getInt("payload_int")
                tag.contains("payload_long") -> tag.getLong("payload_long")
                tag.contains("payload_double") -> tag.getDouble("payload_double")
                tag.contains("payload_float") -> tag.getFloat("payload_float")
                tag.contains("payload_bool") -> tag.getBoolean("payload_bool")
                else -> null
            }
            return ScriptSignal(
                name = tag.getString("name"),
                scope = runCatching { SignalScope.valueOf(tag.getString("scope")) }.getOrDefault(SignalScope.LOCAL),
                owner = deserializeSignalOwner(tag.getCompound("owner")),
                sourceScriptPath = tag.getString("source"),
                payload = payload,
            )
        }
    }
}

class ScriptSignalContextElement(
    val signal: ScriptSignal,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<ScriptSignalContextElement>
}

@CodeBlocksDSL
suspend fun currentScriptSignal(): ScriptSignal? = currentCoroutineContext()[ScriptSignalContextElement]?.signal

interface ScriptSignalHandler {
    val signalName: String
    val signalScope: SignalScope
}

private fun serializeSignalOwner(ownerKey: OwnerKey): CompoundTag = CompoundTag().apply {
    when (ownerKey) {
        OwnerKey.Global -> putString("type", "global")
        is OwnerKey.Entity -> {
            putString("type", "entity")
            putUUID("uuid", ownerKey.uuid)
        }
    }
}

private fun deserializeSignalOwner(tag: CompoundTag): OwnerKey {
    return when (tag.getString("type")) {
        "entity" -> OwnerKey.Entity(tag.getUUID("uuid"))
        else -> OwnerKey.Global
    }
}
