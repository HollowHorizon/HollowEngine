package ru.hollowhorizon.hollowengine.client.gui.overlay

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.ClearColorDontCare
import de.fabmax.kool.pipeline.ClearDepthDontCare
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions
import ru.hollowhorizon.hollowengine.common.utils.currentServer

object CompilationStatus {
    private val messages = HashMap<String, Status>()

    private val surface: UiSurface

    val overlay = Scene("Compilation Status").apply {
        setupUiScene()
        clearColor = ClearColorDontCare
        clearDepth = ClearDepthDontCare

        val sizes = Sizes.medium

        surface = addPanelSurface(IdeTheme.colors, IdeTheme.sizes) {
            modifier.align(AlignmentX.End, AlignmentY.Bottom)
                .border(RectBorder(Color.WHITE, sizes.borderWidth))
                .background(RectBackground(Color("00000066")))
                .size(FitContent, FitContent)
                .padding(sizes.gap)

            Column {
                messages.forEach { (file, status) ->
                    Text("hollowengine.hud.compilation.${status.text}".lang(file)) {
                        modifier.textAlign(AlignmentX.End, AlignmentY.Center)
                    }
                }
            }
        }
        surface.inputMode = UiSurface.InputCaptureMode.CaptureDisabled

        onUpdate {
            isVisible = messages.isNotEmpty()
        }
    }

    fun updateStatus(file: String, status: Status) {
        messages[file] = status
        surface.triggerUpdate()
    }

    fun clearStatus(file: String) {
        messages.remove(file)
        surface.triggerUpdate()
    }

    enum class Status(val text: String) {
        PARSE("parsing"),
        COMPILATION("compiling"),
        OBFUSCATION("obfuscating"),
        EXECUTE("executing"),
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class UpdateStatusPacket(val file: String, private val status: CompilationStatus.Status?) :
    HollowPacket {
    override fun handle(player: Player) {
        if (status != null) CompilationStatus.updateStatus(file, status)
        else CompilationStatus.clearStatus(file)
    }

    fun sendToOperators() {
        try {
            currentServer // TODO: Make check is server loaded
        } catch (e: Exception) {
            return
        }

        val players = currentServer.playerList.players.filter { it.hasPermissions(PlayerPermissions.GAMEMASTER) }
        send(*players.toTypedArray())
    }
}
