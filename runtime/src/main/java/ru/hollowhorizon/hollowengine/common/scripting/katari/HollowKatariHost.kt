package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.ChoiceOptionSnapshot
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariBindings
import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindings
import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder
import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeHost
import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeNoOpHost
import com.sunnychung.lib.multiplatform.kotlite.model.GlobalProperty
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeHostValue
import com.sunnychung.lib.multiplatform.kotlite.model.NullValue
import com.sunnychung.lib.multiplatform.kotlite.model.SourcePosition
import com.sunnychung.lib.multiplatform.kotlite.stdlib.AllStdLibModules
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.utils.colored
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.onClickCommand
import ru.hollowhorizon.hollowengine.common.utils.onHoverText
import java.util.UUID

val KatariEditorContextGlobalTypes = linkedMapOf(
    "player" to "Player",
    "server" to "Server",
    "overworld" to "Level",
)

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
    val savedVariables = KatariSavedVariables(server)
    val bindings = NarrativeBindings {
        install(AllStdLibModules { message -> server.playerList.players.forEach { it.sendSystemMessage(message.literal) } })
        registerBuiltinFunctions(host)
        registerSavedVariableBindings(savedVariables)
        registerGeneratedKatariBindings(server)
        registerContextGlobals(server, sourcePlayer, sourcePlayerId)
    }
    savedVariables.snapshotCodec = bindings.snapshotCodec
    return bindings to host
}

fun NarrativeBindingsBuilder.registerContextGlobals(
    server: MinecraftServer,
    sourcePlayer: ServerPlayer?,
    sourcePlayerId: String?,
) {
    val playerRef: Player? = sourcePlayer ?: sourcePlayerId?.let { server.playerList.getPlayer(UUID.fromString(it)) }
    playerRef?.let { global("player", NarrativeHostValue("Player", it, symbolTable), persistent = true) }
    global("server", NarrativeHostValue("Server", server, symbolTable))
    global("overworld", NarrativeHostValue("Level", server.overworld(), symbolTable))
}

fun createHollowKatariEditorBindings(): KatariBindings {
    return NarrativeBindings {
        install(AllStdLibModules())
        registerBuiltinFunctions(NarrativeNoOpHost)
        registerSavedVariableBindings(null)
        registerGeneratedKatariBindings()
        registerContextGlobalTypes()
    }
}

fun NarrativeBindingsBuilder.registerContextGlobalTypes() {
    KatariEditorContextGlobalTypes.forEach { (name, type) -> registerGlobalType(name, type) }
}

private fun NarrativeBindingsBuilder.registerGlobalType(name: String, type: String) {
    registerKotliteGlobalProperty(
        GlobalProperty(
            position = SourcePosition.BUILTIN,
            declaredName = name,
            type = type,
            isMutable = false,
            getter = { NullValue },
        )
    )
}
