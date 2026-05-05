package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder
import com.sunnychung.lib.multiplatform.kotlite.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.events.factory.await
import ru.hollowhorizon.hollowengine.common.events.server.ServerChatEvent
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationPlayMode

internal fun NarrativeBindingsBuilder.hollowKatariFunctions(
    server: MinecraftServer,
    sourcePlayer: ServerPlayer?,
) {
    katariChatFunctions(server)
    katariAnimatorFunctions(server)
    katariInputFunctions(server)
}

private fun NarrativeBindingsBuilder.katariChatFunctions(server: MinecraftServer) {
    suspendableFunction("waitChat", listOf(), returnType = "ChatMessage", onDispatch = { args, ctx, resume ->
        server.coroutineScope.launch {
            val result = runCatching {
                val event = ServerChatEvent.await()
                delay(50)
                val msg = KatariChatMessage(event.player, event.message.string)
                NarrativeHostValue("ChatMessage", msg, ctx.symbolTable)
            }
            val error = result.exceptionOrNull()
            if (error == null) {
                resume(KatariHostValueResponse(result.getOrNull().toRuntimeValue(ctx.symbolTable)))
            } else {
                resume(KatariHostErrorResponse(error.message ?: error::class.java.simpleName))
            }
        }
    }, onResume = { args, response, ctx ->
        if (response is KatariHostErrorResponse) error(response.message)
        (response as? KatariHostValueResponse)?.value ?: NullValue
    })
}

private data class KatariHostErrorResponse(val message: String) : FunctionResponse
private data class KatariHostValueResponse(val value: RuntimeValue) : FunctionResponse

internal fun Any?.toRuntimeValue(symbolTable: SymbolTable): RuntimeValue = when (this) {
    null, Unit -> NullValue
    is RuntimeValue -> this
    is Boolean -> BooleanValue(this, symbolTable)
    is Int -> IntValue(this, symbolTable)
    is Double -> DoubleValue(this, symbolTable)
    is Float -> FloatValue(toFloat(), symbolTable)
    is String -> StringValue(this, symbolTable)
    is Entity -> NarrativeHostValue("Entity", this, symbolTable)
    is KatariChatMessage -> NarrativeHostValue("ChatMessage", this, symbolTable)
    is KatariAnimatorBuilder -> toKatariHost()
    is KatariInputSnapshot -> toKatariHost()
    else -> error("Unsupported Katari host return value `$this`")
}

internal fun RuntimeValue?.asText(): String = when (this) {
    is StringValue -> value
    is IntValue -> value.toString()
    is DoubleValue -> value.toString()
    is BooleanValue -> value.toString()
    NullValue, DefaultArgumentMarker, null -> ""
    else -> toString()
}

internal fun RuntimeValue.asDouble(): Double? = when (this) {
    is IntValue -> value.toDouble()
    is DoubleValue -> value
    else -> null
}

internal fun RuntimeValue.asInt(): Int? = when (this) {
    is IntValue -> value
    is DoubleValue -> value.toInt()
    else -> null
}

internal fun RuntimeValue.asBool(): Boolean? = when (this) {
    is BooleanValue -> value
    else -> null
}

internal fun String.toAnimationPlayMode(): AnimationPlayMode = when (lowercase()) {
    "once" -> AnimationPlayMode.Once
    "loop", "looped" -> AnimationPlayMode.Loop
    "hold", "clamp", "clampforever", "hold_on_last_frame" -> AnimationPlayMode.ClampForever
    "pingpong", "ping-pong" -> AnimationPlayMode.PingPong
    else -> error("Unknown animation play mode `$this`")
}

internal inline fun <reified T> RuntimeValue?.asHost(typeId: String, name: String): T {
    val host = this as? NarrativeHostValue ?: error("$name expects host value `$typeId`")
    if (typeId != "Any" && host.typeId != typeId) error("$name expects `$typeId`, got `${host.typeId}`")
    return host.value as? T ?: error("$name has unexpected host value `${host.value}`")
}

internal inline fun <reified T> List<RuntimeValue>.receiver(function: String): T =
    firstOrNull().asHost("Any", "$function receiver")
