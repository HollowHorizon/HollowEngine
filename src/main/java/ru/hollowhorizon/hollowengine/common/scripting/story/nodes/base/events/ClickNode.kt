package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.events

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.client.screen.overlays.DrawMousePacket
import ru.hollowhorizon.hollowengine.common.network.MouseButton
import ru.hollowhorizon.hollowengine.common.network.MouseButtonWaitPacket
import ru.hollowhorizon.hollowengine.common.network.ServerMouseClickedEvent
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.ForgeEventNode

class ClickNode(var clickType: MouseButton, val drawIcon: Boolean = false) :
    ForgeEventNode<ServerMouseClickedEvent>(ServerMouseClickedEvent::class.java, { true }) {
    override val action = { event: ServerMouseClickedEvent ->
        manager.team.isMember(event.entity.uuid) && clickType == event.button
    }

    override fun tick(): Boolean {
        if (drawIcon) {
            if (!isStarted) {
                MouseButtonWaitPacket(clickType).send(*manager.team.onlineMembers.toTypedArray())
                DrawMousePacket(true).send(*manager.team.onlineMembers.toTypedArray())
            }
            if (isEnded) DrawMousePacket(false).send(*manager.team.onlineMembers.toTypedArray())
        }
        return super.tick()
    }

    override fun serializeNBT(): CompoundTag {
        return super.serializeNBT().apply {
            putInt("clickType", clickType.ordinal)
        }
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        super.deserializeNBT(nbt)
        clickType = MouseButton.entries[nbt.getInt("clickType")]
    }
}

fun IContextBuilder.waitClick() =
    +ClickNode(MouseButton.RIGHT, true)