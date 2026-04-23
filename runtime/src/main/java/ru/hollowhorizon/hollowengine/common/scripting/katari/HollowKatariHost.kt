package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.ChoiceOptionSnapshot
import com.sunnychung.lib.multiplatform.kotlite.katari.FunctionResponse
import com.sunnychung.lib.multiplatform.kotlite.katari.FunctionResult
import com.sunnychung.lib.multiplatform.kotlite.katari.ImmediateKatariFunctionDefinition
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariFunctionDefinition
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariValue
import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeHost
import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindings
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariBindings
import com.sunnychung.lib.multiplatform.kotlite.katari.SuspendableKatariFunctionDefinition
import com.sunnychung.lib.multiplatform.kotlite.katari.toKatari
import com.sunnychung.lib.multiplatform.kotlite.stdlib.AllStdLibModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.npcs.navigation.rotate
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.move
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.npc
import ru.hollowhorizon.hollowengine.common.utils.colored
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.onClickCommand
import ru.hollowhorizon.hollowengine.common.utils.onHoverText

class HollowKatariHost(
    private val server: MinecraftServer,
    private val runId: String,
    private val sourcePlayer: ServerPlayer?,
    private val onDirty: () -> Unit,
) : NarrativeHost {
    private val pendingChoices = linkedMapOf<String, (String) -> Unit>()
    private val pendingInputs = linkedMapOf<String, (String) -> Unit>()

    override fun narrate(text: String, resume: () -> Unit) {
        send("[Katari] $text".literal)
        onDirty()
        resume()
    }

    override fun choose(options: List<ChoiceOptionSnapshot>, resume: (String) -> Unit) {
        pendingChoices[runId] = resume
        send("Choose:".literal.colored(ChatFormatting.GOLD))
        options.filter { it.enabled }.forEachIndexed { index, option ->
            val command = "/hollowengine katari choose $runId ${option.id}"
            send(
                "  ${index + 1}. ${option.text}".literal
                    .colored(ChatFormatting.AQUA)
                    .onClickCommand(command)
                    .onHoverText("Select `${option.text}`")
            )
        }
        onDirty()
    }

    override fun readLine(question: String, resume: (String) -> Unit) {
        pendingInputs[runId] = resume
        send("[Katari] $question".literal.colored(ChatFormatting.YELLOW))
        send("Type the answer in chat.".literal.colored(ChatFormatting.GRAY))
        onDirty()
    }

    fun select(optionId: String): Boolean {
        val resume = pendingChoices.remove(runId) ?: return false
        onDirty()
        resume(optionId)
        return true
    }

    fun submitInput(text: String): Boolean {
        val resume = pendingInputs.remove(runId) ?: return false
        onDirty()
        resume(text)
        return true
    }

    private fun send(component: Component) {
        val player = sourcePlayer
        if (player != null && player.isAlive) {
            player.sendSystemMessage(component)
        } else {
            server.playerList.players.forEach { it.sendSystemMessage(component) }
        }
    }
}

fun createHollowKatariBindings(
    server: MinecraftServer,
    runId: String,
    sourcePlayer: ServerPlayer?,
    onDirty: () -> Unit,
): Pair<KatariBindings, HollowKatariHost> {
    val host = HollowKatariHost(server, runId, sourcePlayer, onDirty)
    val bindings = NarrativeBindings {
        install(AllStdLibModules { message -> server.playerList.players.forEach { it.sendSystemMessage(message.literal) } })
        registerBuiltinFunctions(host)
        registerHostTypes(server)
        register(hollowKatariFunctions(server))
    }
    return bindings to host
}

private fun com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder.registerHostTypes(
    server: MinecraftServer,
) {
    registerHostType(
        KatariEntityRef::class.toKatari("EntityRef"),
        KatariEntityRefSnapshot::class,
        KatariEntityRefSnapshot.serializer(),
        serialize = { it.snapshot() },
        deserialize = { snapshot, context -> snapshot.restore(context) },
    )
    registerHostType(
        KatariPositionRef::class.toKatari("Position"),
        KatariPositionSnapshot::class,
        KatariPositionSnapshot.serializer(),
        serialize = { it.snapshot() },
        deserialize = { snapshot, _ -> snapshot.restore() },
    )
    server.playerList.players.firstOrNull()?.let { global("player", it.toKatariRef()) }
}

private fun hollowKatariFunctions(server: MinecraftServer): List<KatariFunctionDefinition> {
    return listOf(
        immediate("say") { args ->
            val text = args.singleOrNull()?.asText() ?: error("say(text) expects one argument")
            server.playerList.players.forEach { it.sendSystemMessage(text.literal) }
        },
        immediate("pos") { args ->
            val x = args.getOrNull(0).asDoubleArgument("x")
            val y = args.getOrNull(1).asDoubleArgument("y")
            val z = args.getOrNull(2).asDoubleArgument("z")
            KatariValue.HostObject("Position", Vec3(x, y, z).toPositionRef())
        },
        suspendable("wait", server) { args ->
            val ticks = args.getOrNull(0)?.asInt() ?: error("wait(ticks) expects tick count")
            delay(ticks.coerceAtLeast(0) * 50L)
        },
        immediate("npc") { args ->
            val pos = args.getOrNull(0).asHost<KatariPositionRef>("Position", "npc pos")
            val name = args.getOrNull(1)?.asText() ?: "NPC"
            val model = args.getOrNull(2)?.asText() ?: "hollowengine:models/entity/player_model.gltf"
            val world = args.getOrNull(3)?.asText() ?: pos.dimension?.toString() ?: "minecraft:overworld"
            KatariValue.HostObject("EntityRef", npc(pos.value, name = name, model = model, world = world).toKatariRef())
        },
        suspendable("moveTo", server) { args ->
            val entity = args.receiver<KatariEntityRef>("moveTo")
            val target = args.getOrNull(1).asHost<Any>("Any", "target")
            val speed = args.getOrNull(2)?.asDouble() ?: 1.0
            val distance = args.getOrNull(3)?.asDouble() ?: 1.5
            val resolved = entity.resolve(server)
            val targetEntity = target.toEntityOrNull(server)
            if (resolved is NpcEntity && targetEntity != null) resolved.move(targetEntity, distance, speed)
            else if (resolved is NpcEntity) resolved.move(target.toTargetPosition(server), distance, speed)
            else error("moveTo is only supported for NPC references")
        },
        suspendable("lookAt", server) { args ->
            val entity = args.receiver<KatariEntityRef>("lookAt")
            val target = args.getOrNull(1).asHost<Any>("Any", "target")
            val duration = args.getOrNull(2)?.asInt() ?: 1500
            val resolved = entity.resolve(server) as? LivingEntity ?: error("lookAt receiver must be a living entity")
            val targetEntity = target.toEntityOrNull(server)
            if (targetEntity != null) resolved.rotate(targetEntity, duration.toLong())
            else resolved.rotate({ target.toTargetPosition(server) }, duration.toLong())
        },
        immediate("remove") { args -> args.receiver<KatariEntityRef>("remove").resolve(server).discard() },
        immediate("despawn") { args -> args.receiver<KatariEntityRef>("despawn").resolve(server).discard() },
        immediate("setHealth") { args ->
            val entity = args.receiver<KatariEntityRef>("setHealth").resolve(server) as? LivingEntity
                ?: error("setHealth receiver must be a living entity")
            entity.health = args.getOrNull(1)?.asDouble()?.toFloat() ?: error("setHealth(value) expects health")
        },
        immediate("heal") { args ->
            val entity = args.receiver<KatariEntityRef>("heal").resolve(server) as? LivingEntity
                ?: error("heal receiver must be a living entity")
            entity.heal(args.getOrNull(1)?.asDouble()?.toFloat() ?: 1f)
        },
        immediate("setCustomName") { args ->
            val entity = args.receiver<KatariEntityRef>("setCustomName").resolve(server)
            entity.customName = (args.getOrNull(1)?.asText() ?: "").literal
        },
        immediate("isAlive") { args -> KatariValue.Bool(args.receiver<KatariEntityRef>("isAlive").resolve(server).isAlive) },
        immediate("position") { args ->
            KatariValue.HostObject("Position", args.receiver<KatariEntityRef>("position").resolve(server).position().toPositionRef())
        },
        immediate("dimension") { args -> KatariValue.Text(args.receiver<KatariEntityRef>("dimension").resolve(server).level().dimension().location().toString()) },
        immediate("stopNavigation") { args -> (args.receiver<KatariEntityRef>("stopNavigation").resolve(server) as? Mob)?.navigation?.stop() },
    )
}

private fun immediate(
    id: String,
    block: suspend (List<KatariValue>) -> Any? = { KatariValue.Null },
) = ImmediateKatariFunctionDefinition(id) { arguments, _ ->
    when (val result = block(arguments)) {
        null, Unit -> KatariValue.Null
        is KatariValue -> result
        else -> error("Katari function `$id` returned unsupported value `$result`")
    }
}

private fun suspendable(
    id: String,
    server: MinecraftServer,
    block: suspend CoroutineScope.(List<KatariValue>) -> Unit,
) = SuspendableKatariFunctionDefinition(
    id = id,
    onDispatch = { arguments, _, resume ->
        server.coroutineScope.launch {
            val result = runCatching { block(arguments) }
            val error = result.exceptionOrNull()
            if (error == null) {
                resume(FunctionResponse.Ack)
            } else {
                resume(KatariHostErrorResponse(error.message ?: error::class.java.simpleName))
            }
        }
    },
    onResume = { _, response, _ ->
        if (response is KatariHostErrorResponse) error(response.message)
        require(response == FunctionResponse.Ack) { "`$id` expects acknowledgement" }
        KatariValue.Null
    },
)

private data class KatariHostErrorResponse(val message: String) : FunctionResponse

private fun KatariValue?.asText(): String = when (this) {
    is KatariValue.Text -> value
    is KatariValue.Int32 -> value.toString()
    is KatariValue.Float64 -> value.toString()
    is KatariValue.Bool -> value.toString()
    KatariValue.Null, null -> ""
    else -> toString()
}

private fun KatariValue?.asDoubleArgument(name: String): Double =
    this?.asDouble() ?: error("Missing numeric argument `$name`")

private fun KatariValue.asDouble(): Double? = when (this) {
    is KatariValue.Int32 -> value.toDouble()
    is KatariValue.Float64 -> value
    else -> null
}

private fun KatariValue.asInt(): Int? = when (this) {
    is KatariValue.Int32 -> value
    is KatariValue.Float64 -> value.toInt()
    else -> null
}

private inline fun <reified T> KatariValue?.asHost(typeId: String, name: String): T {
    val host = this as? KatariValue.HostObject ?: error("$name expects host value `$typeId`")
    if (typeId != "Any" && host.typeId != typeId) error("$name expects `$typeId`, got `${host.typeId}`")
    return host.value as? T ?: error("$name has unexpected host value `${host.value}`")
}

private inline fun <reified T> List<KatariValue>.receiver(function: String): T =
    firstOrNull().asHost("Any", "$function receiver")
