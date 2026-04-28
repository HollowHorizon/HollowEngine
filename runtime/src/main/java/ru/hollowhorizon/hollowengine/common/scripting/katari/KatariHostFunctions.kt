package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.ChatFormatting
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.RelativeMovement
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc.NpcAnimationRuntime
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.events.await
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerInteractEvent
import ru.hollowhorizon.hollowengine.common.events.server.ServerChatEvent
import ru.hollowhorizon.hollowengine.common.geary.api.set
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationPlayMode
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode
import ru.hollowhorizon.hollowengine.common.npcs.navigation.rotate
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects.playSound
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.execute
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.getLevel
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.move
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.npc
import ru.hollowhorizon.hollowengine.common.utils.colored
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.rl

internal val KATARI_ENTITY = KatariParameterType("EntityRef")
internal val KATARI_NPC = KatariParameterType("NpcRef")
internal val KATARI_PLAYER = KatariParameterType("PlayerRef")
internal val KATARI_POSITION = KatariParameterType("Position")
internal val KATARI_CHAT_MESSAGE = KatariParameterType("ChatMessage")
private const val DAY_TICKS = 24000L

internal fun hollowKatariFunctions(
    server: MinecraftServer,
    sourcePlayer: ServerPlayer?,
): List<KatariFunctionDefinition> {
    return katariCoreFunctions(server, sourcePlayer) +
            katariEntityFunctions(server) +
            katariTriggerFunctions(server) +
            katariAnimatorFunctions(server) +
            katariInputFunctions(server) +
            katariUtilityFunctions(server, sourcePlayer)
}

private fun katariCoreFunctions(
    server: MinecraftServer,
    sourcePlayer: ServerPlayer?,
) = listOf(
    immediate("say", signature = valueSignature(KatariTypes.Text)) { args ->
        val text = args.singleOrNull()?.asText() ?: error("say(text) expects one argument")
        server.playerList.players.forEach { it.sendSystemMessage(text.literal) }
    },
    immediate(
        "pos",
        signature = namedValueSignature(
            KATARI_POSITION,
            KatariTypes.Double.param("x"),
            KatariTypes.Double.param("y"),
            KatariTypes.Double.param("z"),
        ),
    ) { args ->
        val x = args.getOrNull(0).asDoubleArgument("x")
        val y = args.getOrNull(1).asDoubleArgument("y")
        val z = args.getOrNull(2).asDoubleArgument("z")
        KatariValue.HostObject("Position", Vec3(x, y, z).toPositionRef())
    },
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
        player.toKatariHost()
    },
    suspendable("wait", server, signature = valueSignature(KatariTypes.Int)) { args ->
        val ticks = args.getOrNull(0)?.asInt() ?: error("wait(ticks) expects tick count")
        delay(ticks.coerceAtLeast(0) * 50L)
    },
    suspendable("waitTime", server, signature = valueSignature(KatariTypes.Int)) { args ->
        val targetTime = args.getOrNull(0)?.asInt() ?: error("waitTime(timeOfDay) expects game time")
        val normalizedTarget = targetTime.toLong().floorMod(DAY_TICKS)
        while (server.overworld().dayTime.floorMod(DAY_TICKS) != normalizedTarget) delay(50)
    },
    suspendable("waitDay", server, signature = valueSignature()) {
        while (!server.overworld().isDay) delay(50)
    },
    suspendable("waitNight", server, signature = valueSignature()) {
        while (server.overworld().isDay) delay(50)
    },
    immediate(
        "npc",
        signature = namedValueSignature(
            KATARI_NPC,
            KATARI_POSITION.param("pos"),
            KatariTypes.Text.param("name", KatariValue.Text("NPC")),
            KatariTypes.Text.param("model", KatariValue.Text("hollowengine:models/entity/player_model.gltf")),
            KatariTypes.Text.param("world", hasDefault = true),
        ),
    ) { args ->
        val pos = args.getOrNull(0).asHost<KatariPositionRef>("Position", "npc pos")
        val name = args.getOrNull(1)?.asText() ?: "NPC"
        val model = args.getOrNull(2)?.asText() ?: "hollowengine:models/entity/player_model.gltf"
        val world = args.getOrNull(3)?.asText() ?: pos.dimension?.toString() ?: "minecraft:overworld"
        npc(pos.value, name = name, model = model, world = world).toKatariHost()
    },
)

private fun katariEntityFunctions(server: MinecraftServer) = listOf(
    immediate("setHitboxMode", signature = memberSignature(KATARI_ENTITY, KatariTypes.Text)) { args ->
        val npc = args.receiver<KatariEntityRef>("setHitboxMode").resolve(server) as? NpcEntity
            ?: error("setHitboxMode receiver must be an NPC")
        npc.hitboxMode = args.getOrNull(1)?.asText()?.toHitboxMode()
            ?: error("setHitboxMode(mode) expects mode")
    },
    suspendable(
        "moveTo", server, signature = namedMemberSignature(
            KATARI_ENTITY,
            KatariTypes.Unit,
            KatariTypes.Any.param("target"),
            KatariTypes.Double.param("speed", KatariValue.Float64(1.0)),
            KatariTypes.Double.param("distance", KatariValue.Float64(0.05))
        )
    ) { args ->
        val entity = args.receiver<KatariEntityRef>("moveTo")
        val target = args.getOrNull(1).asHost<Any>("Any", "target")
        val speed = args.getOrNull(2)?.asDouble() ?: 1.0
        val distance = args.getOrNull(3)?.asDouble() ?: 0.05
        val resolved = entity.resolve(server)
        val targetEntity = target.toEntityOrNull(server)
        when (resolved) {
            is NpcEntity if targetEntity != null -> resolved.move(targetEntity, distance, speed)
            is NpcEntity -> resolved.move(target.toTargetPosition(server), distance, speed)
            else -> error("moveTo is only supported for NPC references")
        }
    },
    suspendable(
        "lookAt",
        server,
        signature = namedMemberSignature(
            KATARI_ENTITY,
            KatariTypes.Unit,
            KatariTypes.Any.param("target"),
            KatariTypes.Int.param("duration", KatariValue.Int32(50)),
        )
    ) { args ->
        val entity = args.receiver<KatariEntityRef>("lookAt")
        val target = args.getOrNull(1).asHost<Any>("Any", "target")
        val duration = args.getOrNull(2)?.asInt() ?: 1500
        val resolved = entity.resolve(server) as? LivingEntity ?: error("lookAt receiver must be a living entity")
        val targetEntity = target.toEntityOrNull(server)
        if (targetEntity != null) resolved.rotate(targetEntity, duration.toLong())
        else resolved.rotate({ target.toTargetPosition(server) }, duration.toLong())
    },
    immediate("teleport", signature = memberSignature(KATARI_ENTITY, KATARI_POSITION)) { args ->
        val entity = args.receiver<KatariEntityRef>("teleport").resolve(server)
        val position = args.getOrNull(1).asHost<KatariPositionRef>("Position", "teleport position")
        val level = position.dimension?.let { server.getLevel(it.toString()) } ?: entity.level() as ServerLevel
        entity.teleportTo(
            level,
            position.value.x,
            position.value.y,
            position.value.z,
            emptySet<RelativeMovement>(),
            entity.yRot,
            entity.xRot
        )
    },
    immediate("teleportTo", signature = memberSignature(KATARI_ENTITY, KATARI_ENTITY)) { args ->
        val entity = args.receiver<KatariEntityRef>("teleportTo").resolve(server)
        val target = args.getOrNull(1).asHost<KatariEntityRef>("EntityRef", "teleport target").resolve(server)
        entity.teleportTo(
            target.level() as ServerLevel,
            target.x,
            target.y,
            target.z,
            emptySet<RelativeMovement>(),
            target.yRot,
            target.xRot
        )
    },
    immediate("remove", signature = memberSignature(KATARI_ENTITY)) { args ->
        args.receiver<KatariEntityRef>("remove").resolve(server).discard()
    },
    immediate("despawn", signature = memberSignature(KATARI_ENTITY)) { args ->
        args.receiver<KatariEntityRef>("despawn").resolve(server).discard()
    },
    immediate("swing", signature = memberSignature(KATARI_ENTITY)) { args ->
        val entity = args.receiver<KatariEntityRef>("swing").resolve(server) as? LivingEntity
            ?: error("swing receiver must be a living entity")
        entity.swing(InteractionHand.MAIN_HAND)
    },
    immediate("setHealth", signature = memberSignature(KATARI_ENTITY, KatariTypes.Double)) { args ->
        val entity = args.receiver<KatariEntityRef>("setHealth").resolve(server) as? LivingEntity
            ?: error("setHealth receiver must be a living entity")
        entity.health = args.getOrNull(1)?.asDouble()?.toFloat() ?: error("setHealth(value) expects health")
    },
    immediate("heal", signature = memberSignature(KATARI_ENTITY, KatariTypes.Double)) { args ->
        val entity = args.receiver<KatariEntityRef>("heal").resolve(server) as? LivingEntity
            ?: error("heal receiver must be a living entity")
        entity.heal(args.getOrNull(1)?.asDouble()?.toFloat() ?: 1f)
    },
    immediate("setModel", signature = memberSignature(KATARI_ENTITY, KatariTypes.Text, KatariTypes.Text)) { args ->
        val entity = args.receiver<KatariEntityRef>("setModel").resolve(server)
        val model = args.getOrNull(1)?.asText() ?: error("setModel(model, controller) expects model")
        val controller = args.getOrNull(2)?.asText() ?: "player_model.animation-controller.kts"
        entity.set(Model(model = model, controllerScript = controller))
    },
    immediate(
        "setTransform",
        signature = memberSignature(
            KATARI_ENTITY,
            KatariTypes.Double,
            KatariTypes.Double,
            KatariTypes.Double,
            KatariTypes.Double
        )
    ) { args ->
        val entity = args.receiver<KatariEntityRef>("setTransform").resolve(server)
        entity.set(
            TransformComponent.legacy(
                x = args.getOrNull(1).asDoubleArgument("x").toFloat(),
                y = args.getOrNull(2).asDoubleArgument("y").toFloat(),
                z = args.getOrNull(3).asDoubleArgument("z").toFloat(),
                scale = args.getOrNull(4).asDoubleArgument("scale").toFloat(),
            )
        )
    },
    immediate(
        "playAnimation",
        signature = namedMemberSignature(
            KATARI_ENTITY,
            KatariTypes.Unit,
            KatariTypes.Text.param("animation"),
            KatariTypes.Text.param("playMode", KatariValue.Text("once")),
            KatariTypes.Double.param("fadeIn", KatariValue.Float64(0.33)),
            KatariTypes.Double.param("fadeOut", KatariValue.Float64(0.33))
        )
    ) { args ->
        val entity = args.receiver<KatariEntityRef>("playAnimation").resolve(server)
        NpcAnimationRuntime.apply(
            entity = entity,
            from = null,
            to = args.getOrNull(1)?.asText() ?: error("playAnimation(animation) expects animation"),
            playMode = args.getOrNull(2)?.asText()?.toAnimationPlayMode() ?: AnimationPlayMode.Once,
            duration = 0f,
            fadeIn = (args.getOrNull(3)?.asDouble() ?: 0.33).toFloat(),
            fadeOut = (args.getOrNull(4)?.asDouble() ?: 0.33).toFloat(),
        )
    },
    immediate(
        "stopAnimation",
        signature = namedMemberSignature(
            KATARI_ENTITY, KatariTypes.Unit, KatariTypes.Text.param("animation"),
            KatariTypes.Double.param(
                "fadeOut",
                KatariValue.Float64(0.33)
            )
        )
    ) { args ->
        val entity = args.receiver<KatariEntityRef>("stopAnimation").resolve(server)
        NpcAnimationRuntime.apply(
            entity = entity,
            from = args.getOrNull(1)?.asText() ?: error("stopAnimation(animation) expects animation"),
            to = null,
            playMode = AnimationPlayMode.Once,
            duration = (args.getOrNull(2)?.asDouble() ?: 0.33).toFloat(),
        )
    },
    immediate("attack", signature = memberSignature(KATARI_ENTITY, KATARI_ENTITY.nullable())) { args ->
        val mob = args.receiver<KatariEntityRef>("attack").resolve(server) as? Mob
            ?: error("attack receiver must be a mob")
        mob.target = args.getOrNull(1)
            ?.takeUnless { it == KatariValue.Null }
            ?.asHost<KatariEntityRef>("EntityRef", "attack target")
            ?.resolve(server) as? LivingEntity
    },
    immediate("say", signature = memberSignature(KATARI_ENTITY, KatariTypes.Text)) { args ->
        val entity = args.receiver<KatariEntityRef>("say").resolve(server)
        val text = args.getOrNull(1)?.asText() ?: ""
        val name = entity.customName?.string?.takeIf(String::isNotBlank) ?: entity.name.string
        server.playerList.players.forEach {
            it.sendSystemMessage("[${name}]: $text".literal.colored(ChatFormatting.LIGHT_PURPLE))
        }
    },
    immediate("getAttribute", signature = memberSignature(KATARI_ENTITY, KatariTypes.Text)) { args ->
        val entity = args.receiver<KatariEntityRef>("getAttribute").resolve(server) as? LivingEntity
            ?: error("getAttribute receiver must be a living entity")
        val attribute = args.getOrNull(1)?.asText()?.attribute()
        KatariValue.Float64(entity.attributes.getInstance(attribute)?.baseValue ?: 0.0)
    },
    immediate(
        "setAttribute",
        signature = memberSignature(KATARI_ENTITY, KatariTypes.Text, KatariTypes.Double)
    ) { args ->
        val entity = args.receiver<KatariEntityRef>("setAttribute").resolve(server) as? LivingEntity
            ?: error("setAttribute receiver must be a living entity")
        val attribute = args.getOrNull(1)?.asText()?.attribute()
        entity.attributes.getInstance(attribute)?.baseValue = args.getOrNull(2).asDoubleArgument("value")
    },
    immediate("give", signature = memberSignature(KATARI_PLAYER, KatariTypes.Text, KatariTypes.Int)) { args ->
        val player = args.receiver<KatariPlayerRef>("give").resolvePlayer(server)
        val itemId = args.getOrNull(1)?.asText() ?: error("give(item, count) expects item")
        val count = args.getOrNull(2)?.asInt() ?: 1
        player.inventory.add(ItemStack(BuiltInRegistries.ITEM.get(itemId.rl), count))
    },
    immediate("stopNavigation", signature = memberSignature(KATARI_ENTITY)) { args ->
        (args.receiver<KatariEntityRef>("stopNavigation").resolve(server) as? Mob)?.navigation?.stop()
    },
)

private fun katariTriggerFunctions(server: MinecraftServer) = listOf(
    suspendable("waitNpcInteract", server, signature = memberSignature(KATARI_ENTITY)) { args ->
        val npc = args.receiver<KatariEntityRef>("waitNpcInteract").resolve(server)
        val event = await<PlayerInteractEvent.EntityInteract> { it.target.uuid == npc.uuid }
        event.player.toKatariHost()
    },
    suspendable("waitChat", server, signature = valueSignature().returns(KATARI_CHAT_MESSAGE)) {
        val event = await<ServerChatEvent>()
        KatariChatMessage(event.player.toKatariRef(), event.message.string).toKatariHost()
    },
    suspendable(
        "waitZone",
        server,
        signature = namedValueSignature(
            KATARI_PLAYER, KATARI_PLAYER.param("player"), KATARI_POSITION.param("position"), KatariTypes.Double.param(
                "radius",
                KatariValue.Float64(1.0)
            ), KatariTypes.Boolean.param("leave", KatariValue.Bool(false))
        )
    ) { args ->
        val player = args.getOrNull(0).asHost<KatariPlayerRef>("PlayerRef", "waitZone player").resolvePlayer(server)
        val pos = args.getOrNull(1).asHost<KatariPositionRef>("Position", "waitZone position").value
        val radius = args.getOrNull(2)?.asDouble() ?: 1.0
        val leave = (args.getOrNull(3) as? KatariValue.Bool)?.value ?: false
        while ((player.position().distanceTo(pos) <= radius) == leave) delay(50)
        player.toKatariHost()
    },
)

private fun katariUtilityFunctions(
    server: MinecraftServer,
    sourcePlayer: ServerPlayer?,
) = listOf(
    immediate(
        "playSound",
        signature = namedValueSignature(
            KatariTypes.Unit,
            KatariTypes.Text.param("sound"),
            KATARI_POSITION.param("position"),
            KatariTypes.Double.param("volume", KatariValue.Float64(1.0)),
            KatariTypes.Double.param("pitch", KatariValue.Float64(1.0))
        )
    ) { args ->
        val location = args.getOrNull(0)?.asText() ?: error("playSound(sound, pos) expects sound")
        val pos = args.getOrNull(1).asHost<KatariPositionRef>("Position", "sound position")
        val volume = (args.getOrNull(2)?.asDouble() ?: 1.0).toFloat()
        val pitch = (args.getOrNull(3)?.asDouble() ?: 1.0).toFloat()
        server.getLevel(pos.dimension?.toString() ?: "minecraft:overworld")
            .playSound(location, volume, pitch, pos.value, null, false)
    },
    immediate(
        "playSound",
        signature = namedValueSignature(
            KatariTypes.Unit,
            KATARI_PLAYER.param("player"),
            KatariTypes.Text.param("sound"),
            KatariTypes.Double.param("volume", KatariValue.Float64(1.0)),
            KatariTypes.Double.param("pitch", KatariValue.Float64(1.0))
        )
    ) { args ->
        val player = args.receiver<KatariPlayerRef>("playSound").resolvePlayer(server)
        player.playSound(
            args.getOrNull(1)?.asText() ?: error("playSound(sound) expects sound"),
            (args.getOrNull(2)?.asDouble() ?: 1.0).toFloat(),
            (args.getOrNull(3)?.asDouble() ?: 1.0).toFloat(),
        )
    },
    immediate("command", signature = valueSignature(KatariTypes.Text)) { args ->
        KatariValue.Int32(execute(args.getOrNull(0)?.asText() ?: error("command(text) expects command")))
    },
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
    is Entity -> toKatariHost()
    is KatariChatMessage -> toKatariHost()
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

internal fun KatariValue?.asDoubleArgument(name: String): Double =
    this?.asDouble() ?: error("Missing numeric argument `$name`")

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

private fun Long.floorMod(divisor: Long): Long = ((this % divisor) + divisor) % divisor

private fun String.toHitboxMode(): HitboxMode = when (lowercase()) {
    "pulling", "push", "pushing", "standard" -> HitboxMode.PULLING
    "empty", "none", "passable" -> HitboxMode.EMPTY
    "blocking", "block" -> HitboxMode.BLOCKING
    else -> error("Unknown hitbox mode `$this`")
}

internal fun String.toAnimationPlayMode(): AnimationPlayMode = when (lowercase()) {
    "once" -> AnimationPlayMode.Once
    "loop", "looped" -> AnimationPlayMode.Loop
    "hold", "clamp", "clampforever", "hold_on_last_frame" -> AnimationPlayMode.ClampForever
    "pingpong", "ping-pong" -> AnimationPlayMode.PingPong
    else -> error("Unknown animation play mode `$this`")
}

private fun String.attribute(): Holder<Attribute> =
    BuiltInRegistries.ATTRIBUTE.getHolder(rl).orElseThrow { IllegalArgumentException("Unknown attribute `$this`") }

internal inline fun <reified T> KatariValue?.asHost(typeId: String, name: String): T {
    val host = this as? KatariValue.HostObject ?: error("$name expects host value `$typeId`")
    if (typeId != "Any" && host.typeId != typeId) error("$name expects `$typeId`, got `${host.typeId}`")
    return host.value as? T ?: error("$name has unexpected host value `${host.value}`")
}

internal inline fun <reified T> List<KatariValue>.receiver(function: String): T =
    firstOrNull().asHost("Any", "$function receiver")
