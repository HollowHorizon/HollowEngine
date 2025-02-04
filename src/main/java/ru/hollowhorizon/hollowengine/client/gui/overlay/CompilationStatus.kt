package ru.hollowhorizon.hollowengine.client.gui.overlay

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.kool.KoolManager.MONOCRAFT_DATA
import ru.hollowhorizon.hc.client.kool.ScreenScene
import ru.hollowhorizon.hc.client.utils.currentServer
import ru.hollowhorizon.hc.common.coroutines.isServerLoaded
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions

object CompilationStatus {
    private val messages = HashMap<String, Status>()

    private val surface: UiSurface

    val overlay = ScreenScene("Compilation Status").apply {
        setupUiScene()

        val sizes = Sizes.medium

        surface = addPanelSurface(sizes = sizes.copy(normalText = MsdfFont(MONOCRAFT_DATA, 30f))) {
            modifier.align(AlignmentX.End, AlignmentY.Bottom)
                .border(RectBorder(Color.WHITE, sizes.borderWidth))
                .background(RectBackground(Color("00000066")))
                .padding(sizes.gap)

            Column {
                messages.forEach { (file, status) ->
                    Text("hollowengine.hud.compilation.${status.text}".lang(file)) {
                        modifier.textAlign(AlignmentX.End, AlignmentY.Center)
                    }
                }
            }
        }

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

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class UpdateStatusPacket(val file: String, private val status: CompilationStatus.Status?) : HollowPacketV3<UpdateStatusPacket> {
    override fun handle(player: Player) {
        if (status != null) CompilationStatus.updateStatus(file, status)
        else CompilationStatus.clearStatus(file)
    }

    fun sendToOperators() {
        if(!isServerLoaded) return

        val players = currentServer.playerList.players.filter { it.hasPermissions(PlayerPermissions.GAMEMASTER) }
        send(*players.toTypedArray())
    }
}