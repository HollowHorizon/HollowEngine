package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.events.await
import ru.hollowhorizon.hollowengine.common.events.server.ServerChatEvent
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationPlayMode
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.getLevel

internal val KATARI_ENTITY = KatariParameterType("Entity")
internal val KATARI_PLAYER = KatariParameterType("Player")
internal val KATARI_CHAT_MESSAGE = KatariParameterType("ChatMessage")

internal fun hollowKatariFunctions(
    server: MinecraftServer,
    sourcePlayer: ServerPlayer?,
): List<KatariFunctionDefinition> {
    return katariCoreFunctions(server, sourcePlayer) +
            katariChatFunctions(server) +
            katariAnimatorFunctions(server) +
            katariInputFunctions(server) +
            katariUtilityFunctions(server, sourcePlayer)
}

private fun katariCoreFunctions(
    server: MinecraftServer,
    sourcePlayer: ServerPlayer?,
) = listOf(
    immediate("level", signature = valueSignature(KatariTypes.Text).returns(KatariParameterType("Level"))) { args ->
        KatariValue.HostObject("Level", server.getLevel(args.getOrNull(0)?.asText() ?: "minecraft:overworld"))
    },
    immediate(
        "player",
        signature = namedValueSignature(KATARI_PLAYER, KatariTypes.Text.param("name", hasDefault = true)),
    ) { args ->
        val nameOrUuid = args.getOrNull(0)?.asText()?.takeIf(String::isNotBlank) ?: sourcePlayer?.uuid?.toString()
        ?: error("player(name) expects player name or uuid when no source player is bound")
        val player = server.playerList.players.firstOrNull {
            it.gameProfile.name.equals(nameOrUuid, ignoreCase = true) || it.uuid.toString() == nameOrUuid
        } ?: error("Player `$nameOrUuid` is not online")
        KatariValue.HostObject("Player", player)
    },
)

private fun katariChatFunctions(server: MinecraftServer) = listOf(
    suspendable("waitChat", server, signature = valueSignature().returns(KATARI_CHAT_MESSAGE)) {
        val event = await<ServerChatEvent>()
        delay(50)
        val msg = KatariChatMessage(event.player, event.message.string)
        KatariValue.HostObject("ChatMessage", msg)
    },
)

private fun katariUtilityFunctions(
    server: MinecraftServer,
    sourcePlayer: ServerPlayer?,
) = listOf(
    immediate("runScript", signature = valueSignature(KatariTypes.Text)) { args ->
        val path = args.getOrNull(0)?.asText() ?: error("runScript(path) expects path")
        KatariValue.Text(server.runtimeContext.katari.run(path, sourcePlayer).getOrThrow())
    },
)

internal fun valueSignature(vararg types: KatariParameterType) = KatariCallableSignature(
    valueParameters = types.mapIndexed { index, type -> type.asValueParameter("arg$index") },
    returnType = KatariTypes.Unit,
)

internal fun memberSignature(receiver: KatariParameterType, vararg types: KatariParameterType) =
    KatariCallableSignature(
        dispatchReceiverType = receiver,
        valueParameters = types.mapIndexed { index, type -> type.asValueParameter("arg$index") },
        returnType = KatariTypes.Unit,
    )

internal fun namedValueSignature(
    returnType: KatariParameterType = KatariTypes.Unit,
    vararg parameters: KatariValueParameter,
) = KatariCallableSignature(valueParameters = parameters.toList(), returnType = returnType)

internal fun namedMemberSignature(
    receiver: KatariParameterType,
    returnType: KatariParameterType = KatariTypes.Unit,
    vararg parameters: KatariValueParameter,
) = KatariCallableSignature(
    dispatchReceiverType = receiver,
    valueParameters = parameters.toList(),
    returnType = returnType,
)

internal fun KatariParameterType.param(
    name: String,
    defaultValue: KatariValue? = null,
    hasDefault: Boolean = defaultValue != null,
) = asValueParameter(name, defaultValue, hasDefault)

internal fun KatariCallableSignature.returns(returnType: KatariParameterType) = copy(returnType = returnType)

internal fun immediate(
    id: String,
    signature: KatariCallableSignature = valueSignature(KatariTypes.Any.repeated()),
    block: suspend (List<KatariValue>) -> Any? = { KatariValue.Null },
) = ImmediateKatariFunctionDefinition(id = id, signature = signature) { arguments, _ ->
    when (val result = block(arguments)) {
        null, Unit -> KatariValue.Null
        is KatariValue -> result
        else -> error("Katari function `$id` returned unsupported value `$result`")
    }
}

internal fun suspendable(
    id: String,
    server: MinecraftServer,
    signature: KatariCallableSignature = valueSignature(KatariTypes.Any.repeated()),
    block: suspend CoroutineScope.(List<KatariValue>) -> Any?,
) = SuspendableKatariFunctionDefinition(
    id = id,
    signature = signature,
    onDispatch = { arguments, _, resume ->
        server.coroutineScope.launch {
            val result = runCatching { block(arguments) }
            val error = result.exceptionOrNull()
            if (error == null) {
                resume(KatariHostValueResponse(result.getOrNull().toKatariValue()))
            } else {
                resume(KatariHostErrorResponse(error.message ?: error::class.java.simpleName))
            }
        }
    },
    onResume = { _, response, _ ->
        if (response is KatariHostErrorResponse) error(response.message)
        (response as? KatariHostValueResponse)?.value ?: KatariValue.Null
    },
)

private data class KatariHostErrorResponse(val message: String) : FunctionResponse
private data class KatariHostValueResponse(val value: KatariValue) : FunctionResponse

internal fun Any?.toKatariValue(): KatariValue = when (this) {
    null, Unit -> KatariValue.Null
    is KatariValue -> this
    is Boolean -> KatariValue.Bool(this)
    is Int -> KatariValue.Int32(this)
    is Double -> KatariValue.Float64(this)
    is Float -> KatariValue.Float64(toDouble())
    is String -> KatariValue.Text(this)
    is Entity -> KatariValue.HostObject("Entity", this)
    is KatariChatMessage -> KatariValue.HostObject("ChatMessage", this)
    is KatariAnimatorBuilder -> toKatariHost()
    is KatariInputSnapshot -> toKatariHost()
    else -> error("Unsupported Katari host return value `$this`")
}

internal fun KatariValue?.asText(): String = when (this) {
    is KatariValue.Text -> value
    is KatariValue.Int32 -> value.toString()
    is KatariValue.Float64 -> value.toString()
    is KatariValue.Bool -> value.toString()
    KatariValue.Null, KatariValue.DefaultArgument, null -> ""
    else -> toString()
}

internal fun KatariValue.asDouble(): Double? = when (this) {
    is KatariValue.Int32 -> value.toDouble()
    is KatariValue.Float64 -> value
    else -> null
}

internal fun KatariValue.asInt(): Int? = when (this) {
    is KatariValue.Int32 -> value
    is KatariValue.Float64 -> value.toInt()
    else -> null
}

internal fun KatariValue.asBool(): Boolean? = when (this) {
    is KatariValue.Bool -> value
    else -> null
}

internal fun String.toAnimationPlayMode(): AnimationPlayMode = when (lowercase()) {
    "once" -> AnimationPlayMode.Once
    "loop", "looped" -> AnimationPlayMode.Loop
    "hold", "clamp", "clampforever", "hold_on_last_frame" -> AnimationPlayMode.ClampForever
    "pingpong", "ping-pong" -> AnimationPlayMode.PingPong
    else -> error("Unknown animation play mode `$this`")
}

internal inline fun <reified T> KatariValue?.asHost(typeId: String, name: String): T {
    val host = this as? KatariValue.HostObject ?: error("$name expects host value `$typeId`")
    if (typeId != "Any" && host.typeId != typeId) error("$name expects `$typeId`, got `${host.typeId}`")
    return host.value as? T ?: error("$name has unexpected host value `${host.value}`")
}

internal inline fun <reified T> List<KatariValue>.receiver(function: String): T =
    firstOrNull().asHost("Any", "$function receiver")
