package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.*
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeHostValue
import com.sunnychung.lib.multiplatform.kotlite.stdlib.AllStdLibModules
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.utils.colored
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.onClickCommand
import ru.hollowhorizon.hollowengine.common.utils.onHoverText
import java.util.*

class HollowKatariHost(
    private val server: MinecraftServer,
    private val runId: String,
    private val sourcePlayer: ServerPlayer?,
    private val onDirty: () -> Unit,
) : NarrativeHost {
    private val pendingChoices = linkedMapOf<String, (String) -> Unit>()
    private val pendingInputs = linkedMapOf<String, (String) -> Unit>()

    override fun narrate(text: String, resume: () -> Unit) {
        send(text.literal.colored(ChatFormatting.GRAY))
        onDirty()
        resume()
    }

    override fun choose(options: List<ChoiceOptionSnapshot>, resume: (String) -> Unit) {
        pendingChoices[runId] = {
            server.coroutineScope.launch {
                delay(50)
                resume(it)
            }
        }
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
        pendingInputs[runId] = {
            server.coroutineScope.launch {
                delay(50)
                resume(it)
            }
        }
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
        registerHostTypes()
        registerGeneratedKatariBindings(server)
        registerContextGlobals(server, sourcePlayer, sourcePlayerId)
    }
    return bindings to host
}

fun NarrativeBindingsBuilder.registerHostTypes() {
    registerHostType(KatariChatMessage::class, "ChatMessage")
    registerHostType(MinecraftServer::class, "Server")
    registerHostType(ServerLevel::class, "Level")
}

fun NarrativeBindingsBuilder.registerContextGlobals(
    server: MinecraftServer,
    sourcePlayer: ServerPlayer?,
    sourcePlayerId: String?,
) {
    val playerRef: Player? = sourcePlayer ?: sourcePlayerId?.let { server.playerList.getPlayer(UUID.fromString(it)) }
    playerRef?.let { global("player", NarrativeHostValue("Player", it, symbolTable)) }
    global("server", NarrativeHostValue("Server", server, symbolTable))
    global("level", NarrativeHostValue("Level", sourcePlayer?.level() ?: server.overworld(), symbolTable))
}
