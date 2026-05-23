package ru.hollowhorizon.hollowengine.client.ui.scripting

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.ui.HollowUiRuntime
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.UiNodeKeys
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.client.ui.screen.HollowUiScreen
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlBuilder
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlTree
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.GuiOverlay
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderOverlayEvent
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler

@Serializable
enum class KatariUiDisplayMode {
    SCREEN,
    OVERLAY,
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class ShowKatariUiPacket(
    private val id: String,
    private val root: UiXmlTree,
    private val mode: KatariUiDisplayMode,
) : HollowPacket {
    override fun handle(player: Player) {
        Minecraft.getInstance().execute {
            when (mode) {
                KatariUiDisplayMode.SCREEN -> Minecraft.getInstance().setScreen(KatariUiScreen(root))
                KatariUiDisplayMode.OVERLAY -> KatariUiOverlays.show(id, root)
            }
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class HideKatariUiOverlayPacket(private val id: String) : HollowPacket {
    override fun handle(player: Player) {
        Minecraft.getInstance().execute {
            KatariUiOverlays.hide(id)
        }
    }
}

class KatariUiScreen(private val root: UiXmlTree) : HollowUiScreen("Katari UI", CompiledHss(emptyList())) {
    override fun buildUi(): UiNode {
        return UiXmlBuilder().build(root)
    }
}

@ClientOnly
object KatariUiOverlays {
    private val overlays = linkedMapOf<String, KatariUiOverlay>()

    fun show(id: String, root: UiXmlTree) {
        overlays.remove(id)?.close()
        overlays[id] = KatariUiOverlay(root)
    }

    fun hide(id: String) {
        overlays.remove(id)?.close()
    }

    @SubscribeEvent
    fun render(event: RenderOverlayEvent.Post) {
        if (event.overlay != GuiOverlay.CHAT_PANEL) return
        overlays.values.forEach { it.render() }
    }
}

private class KatariUiOverlay(private val root: UiXmlTree) {
    private val runtime = HollowUiRuntime()
    private val renderer = MinecraftUiRenderer()

    fun render() {
        val window = Minecraft.getInstance().window
        val node = UiXmlBuilder().build(root)
        UiNodeKeys.assign(node)
        val frame = runtime.frame(node, window.guiScaledWidth.toFloat(), window.guiScaledHeight.toFloat())
        renderer.render(frame.commands)
    }

    fun close() {
        renderer.close()
    }
}
