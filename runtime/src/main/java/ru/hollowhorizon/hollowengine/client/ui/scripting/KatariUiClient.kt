package ru.hollowhorizon.hollowengine.client.ui.scripting

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.render.MinecraftUiRenderer
import ru.hollowhorizon.hollowengine.client.ui.screen.HollowUiScreen
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlBuilder
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlOptions
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlTree
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.GuiOverlay
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderOverlayEvent
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariUiEventPacket
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForCompoundNBT


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
    private val variables: @Serializable(ForCompoundNBT::class) CompoundTag = CompoundTag(),
) : HollowPacket {
    override fun handle(player: Player) {
        Minecraft.getInstance().execute {
            when (mode) {
                KatariUiDisplayMode.SCREEN -> Minecraft.getInstance().setScreen(KatariUiScreen(id, root, variables))
                KatariUiDisplayMode.OVERLAY -> KatariUiOverlays.show(id, root, variables)
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

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class CloseKatariUiScreenPacket(private val id: String) : HollowPacket {
    override fun handle(player: Player) {
        Minecraft.getInstance().execute {
            val current = Minecraft.getInstance().screen
            if (current is KatariUiScreen && current.id == id) {
                Minecraft.getInstance().setScreen(null)
            }
        }
    }
}

class KatariUiScreen(
    val id: String,
    private val root: UiXmlTree,
    private val variables: CompoundTag = CompoundTag(),
) : HollowUiScreen("Katari UI", CompiledHss(emptyList())) {
    private val sink = UiEventSink { payload ->
        HollowEngine.LOGGER.info("[UI:$id]:\n ${payload.toPrettyString()}")
        KatariUiEventPacket(id, payload).send()
    }

    override fun buildUi(): UiNode {
        return UiXmlBuilder(UiXmlOptions(eventSink = sink)).build(root)
    }

    override fun bindings(): UiBindingContext = UiBindingContext(variables)

    override fun eventSink(): UiEventSink = sink
}

private fun CompoundTag.toPrettyString(): String {
    val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    val jsonElement: JsonElement = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, this)
    return gson.toJson(jsonElement)
}

@ClientOnly
object KatariUiOverlays {
    private val overlays = linkedMapOf<String, KatariUiOverlay>()

    fun show(id: String, root: UiXmlTree, variables: CompoundTag = CompoundTag()) {
        overlays.remove(id)?.close()
        overlays[id] = KatariUiOverlay(id, root, variables)
    }

    fun hide(id: String) {
        overlays.remove(id)?.close()
    }

    @SubscribeEvent
    fun render(event: RenderOverlayEvent.Post) {
        if (event.overlay != GuiOverlay.VIGNETTE) return
        overlays.values.forEach { it.render() }
    }
}

private class KatariUiOverlay(
    private val id: String,
    private val root: UiXmlTree,
    private val variables: CompoundTag,
) {
    private val runtime = HollowUiRuntime()
    private val renderer = MinecraftUiRenderer()
    private val sink = UiEventSink { payload -> KatariUiEventPacket(id, payload).send() }
    private val node = UiXmlBuilder(UiXmlOptions(eventSink = sink)).build(root).also {
        UiClientScriptRunner.prepare(it.modifiers.filterIsInstance<UiClientScriptModifier>().flatMap { modifier -> modifier.scripts }, it, sink, variables)
    }

    fun render() {
        val window = Minecraft.getInstance().window
        UiNodeKeys.assign(node)
        val frame = runtime.frame(
            node,
            window.guiScaledWidth.toFloat(),
            window.guiScaledHeight.toFloat(),
            UiBindingContext(variables),
        )
        renderer.render(frame.commands)
    }

    fun close() {
        renderer.close()
    }
}
