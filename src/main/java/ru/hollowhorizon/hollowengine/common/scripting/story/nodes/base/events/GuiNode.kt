package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.events

import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hc.client.screens.ClosedGuiEvent
import ru.hollowhorizon.hc.common.ui.CURRENT_SERVER_GUI
import ru.hollowhorizon.hc.common.ui.OpenGuiPacket
import ru.hollowhorizon.hc.common.ui.Widget
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

class GuiNode(gui: Widget.() -> Unit) : Node() {
    val gui by lazy {
        Widget().apply(gui)
    }
    var isStarted = false
    var isEnded = false
    override fun tick(): Boolean {
        if (!isStarted) {
            CURRENT_SERVER_GUI = gui
            manager.team.onlineMembers.forEach {
                OpenGuiPacket(gui).send(PacketDistributor.PLAYER.with { it })
            }
            isStarted = true
            MinecraftForge.EVENT_BUS.register(this)
        }

        return !isEnded
    }

    @SubscribeEvent
    fun onEvent(event: ClosedGuiEvent) {
        isEnded = event.entity in manager.team.onlineMembers
    }

    override fun serializeNBT() = CompoundTag().apply {
        putBoolean("isEnded", isEnded)
        putBoolean("isStarted", isStarted)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        isEnded = nbt.getBoolean("isEnded")
        isStarted = nbt.getBoolean("isStarted")
    }

}

fun IContextBuilder.gui(gui: Widget.() -> Unit) = +GuiNode(gui)