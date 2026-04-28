package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.*
import com.sunnychung.lib.multiplatform.kotlite.stdlib.AllStdLibModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.RelativeMovement
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc.NpcAnimationRuntime
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
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.execute
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.getLevel
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects.playSound
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.move
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.npc
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.rotation
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.utils.colored
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.onClickCommand
import ru.hollowhorizon.hollowengine.common.utils.onHoverText
import java.util.UUID

class HollowKatariHost(
    private val server: MinecraftServer,
    private val runId: String,
    private val sourcePlayer: ServerPlayer?,
    private val onDirty: () -> Unit,
) : NarrativeHost {
    private val pendingChoices = linkedMapOf<String, (String) -> Unit>()
    private val pendingInputs = linkedMapOf<String, (String) -> Unit>()

    override fun narrate(text: String, resume: () -> Unit) {
        send(text.literal)
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
        send(question.literal.colored(ChatFormatting.YELLOW))
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
    sourcePlayerId: String? = sourcePlayer?.uuid?.toString(),
): Pair<KatariBindings, HollowKatariHost> {
    val host = HollowKatariHost(server, runId, sourcePlayer, onDirty)
    val bindings = NarrativeBindings {
        install(AllStdLibModules { message -> server.playerList.players.forEach { it.sendSystemMessage(message.literal) } })
        registerBuiltinFunctions(host)
        registerHostTypes(server)
        registerContextGlobals(server, sourcePlayer, sourcePlayerId)
        register(hollowKatariFunctions(server, sourcePlayer))
    }
    return bindings to host
}

private fun NarrativeBindingsBuilder.registerHostTypes(
    server: MinecraftServer,
) {
    val entityType = KatariEntityRef::class.toKatari("EntityRef")
    val npcType = KatariNpcRef::class.toKatari("NpcRef", superTypes = listOf(entityType))
    val playerType = KatariPlayerRef::class.toKatari("PlayerRef", superTypes = listOf(entityType))
    val positionType = KatariPositionRef::class.toKatari("Position")
    val chatMessageType = KatariChatMessage::class.toKatari("ChatMessage")
    val animatorType = KatariAnimatorBuilder::class.toKatari("AnimatorController")
    val inputType = KatariInputSnapshot::class.toKatari("InputEvent")
    val serverType = MinecraftServer::class.toKatari("Server")
    val levelType = net.minecraft.server.level.ServerLevel::class.toKatari("Level")

    registerHostType(
        entityType,
        KatariEntityRefSnapshot::class,
        KatariEntityRefSnapshot.serializer(),
        serialize = { it.snapshot() },
        deserialize = { snapshot, context -> snapshot.restore(context) },
    )
    registerHostType(
        npcType,
        KatariEntityRefSnapshot::class,
        KatariEntityRefSnapshot.serializer(),
        serialize = { it.snapshot() },
        deserialize = { snapshot, context -> snapshot.restore(context) as KatariNpcRef },
    )
    registerHostType(
        playerType,
        KatariEntityRefSnapshot::class,
        KatariEntityRefSnapshot.serializer(),
        serialize = { it.snapshot() },
        deserialize = { snapshot, context -> snapshot.restore(context) as KatariPlayerRef },
    )
    registerHostType(
        positionType,
        KatariPositionSnapshot::class,
        KatariPositionSnapshot.serializer(),
        serialize = { it.snapshot() },
        deserialize = { snapshot, _ -> snapshot.restore() },
    )
    registerHostType(chatMessageType)
    registerHostType(
        animatorType,
        KatariAnimatorBuilderSnapshot::class,
        KatariAnimatorBuilderSnapshot.serializer(),
        serialize = { it.snapshot() },
        deserialize = { snapshot, _ -> snapshot.restore() },
    )
    registerHostType(
        inputType,
        KatariInputSnapshot::class,
        KatariInputSnapshot.serializer(),
        serialize = { it },
        deserialize = { snapshot, _ -> snapshot },
    )
    registerHostType(serverType)
    registerHostType(levelType)
}

private fun NarrativeBindingsBuilder.registerContextGlobals(
    server: MinecraftServer,
    sourcePlayer: ServerPlayer?,
    sourcePlayerId: String?,
) {
    val playerRef = sourcePlayer?.toKatariRef() ?: sourcePlayerId?.let {
        KatariPlayerRef(
            uuid = java.util.UUID.fromString(it),
            dimension = server.overworld().dimension().location(),
            lastPosition = sourcePlayer?.position() ?: Vec3.ZERO,
        )
    }
    playerRef?.let { global("player", KatariValue.HostObject("PlayerRef", it)) }
    globalProperty("server", getter = { KatariValue.HostObject("Server", server) })
    globalProperty("level", getter = {
        KatariValue.HostObject("Level", sourcePlayer?.level() ?: server.overworld())
    })
}
