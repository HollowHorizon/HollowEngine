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
class CloseKatariUiOverlayPacket(
    private val id: String,
    private val root: UiXmlTree,
) : HollowPacket {
    override fun handle(player: Player) {
        Minecraft.getInstance().execute {
            KatariUiOverlays.close(id, root)
        }
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class UpdateKatariUiOverlayPacket(
    private val id: String,
    private val root: UiXmlTree,
) : HollowPacket {
    override fun handle(player: Player) {
        Minecraft.getInstance().execute {
            KatariUiOverlays.update(id, root)
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
                current.startClosingAnimation()
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
        overlays[id]?.show(root, variables) ?: run {
            overlays[id] = KatariUiOverlay(id, root, variables)
        }
    }

    fun update(id: String, root: UiXmlTree) {
        overlays[id]?.update(root)
    }

    fun close(id: String, root: UiXmlTree) {
        overlays[id]?.close(root)
    }

    @SubscribeEvent
    fun render(event: RenderOverlayEvent.Post) {
        if (event.overlay != GuiOverlay.CHAT_PANEL) return
        val nowMillis = System.currentTimeMillis()
        overlays.entries.toList().forEach { (id, overlay) ->
            if (overlay.render(nowMillis)) {
                overlays.remove(id)?.dispose()
            }
        }
    }
}

private class KatariUiOverlay(
    private val id: String,
    private var root: UiXmlTree,
    private var variables: CompoundTag,
) {
    private val runtime = HollowUiRuntime()
    private val renderer = MinecraftUiRenderer()
    private val sink = UiEventSink { payload -> KatariUiEventPacket(id, payload).send() }
    private var node = buildNode(root, variables)
    private var closing = false
    private var closingStartedAt: Long? = null
    private var closeBaseFrame: HollowUiFrame? = null
    private var lastFrame: HollowUiFrame? = null

    fun show(root: UiXmlTree, variables: CompoundTag) {
        this.root = root
        this.variables = variables
        node = buildNode(root, variables)
        closing = false
        closingStartedAt = null
        closeBaseFrame = null
    }

    fun update(root: UiXmlTree) {
        this.root = root
        node = buildNode(root, variables)
    }

    fun close(root: UiXmlTree) {
        this.root = root
        node = buildNode(root, variables)
        closing = true
        closingStartedAt = null
        closeBaseFrame = lastFrame
    }

    fun render(nowMillis: Long): Boolean {
        val window = Minecraft.getInstance().window
        node.setClosingState(closing)
        UiNodeKeys.assign(node)
        val frame = runtime.frame(
            node,
            window.guiScaledWidth.toFloat(),
            window.guiScaledHeight.toFloat(),
            UiBindingContext(variables),
            nowMillis,
        )
        renderer.render(frame.commands)
        lastFrame = frame
        if (!closing) return false
        val closeStartedAt = closingStartedAt ?: nowMillis.also { closingStartedAt = it }
        return nowMillis - closeStartedAt >= frame.motionDurationMillis(closeBaseFrame)
    }

    fun dispose() {
        renderer.close()
    }

    private fun buildNode(root: UiXmlTree, variables: CompoundTag): BoxNode {
        return UiXmlBuilder(UiXmlOptions(eventSink = sink)).build(root).also {
            val scripts = it.modifiers.filterIsInstance<UiClientScriptModifier>().flatMap { modifier -> modifier.scripts }
            UiClientScriptRunner.prepare(scripts, it, sink, variables)
        }
    }
}
