package ru.hollowhorizon.hollowengine.common.npcs.dialogues

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.margin
import de.fabmax.kool.modules.ui2.size
import de.fabmax.kool.modules.ui2.tint
import de.fabmax.kool.util.Color
import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.api.utils.Polymorphic
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.utils.math.Interpolation
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForItemStack

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
class DialogueUpdateEvent(val tag: @Serializable(ForCompoundNBT::class) CompoundTag) : HollowPacket, Event {
    override fun handle(player: Player) {
        EventBus.post(this)
    }
}

interface DialogChoice {
    val content: String
    fun UiScope.icon(scale: Float, progress: Float)

    companion object {
        fun simple(text: String, icon: String = "hollowengine:textures/gui/dialogues/simple.png") =
            ChoiceIcon(text, icon)

        fun item(text: String, item: ItemStack) = ChoiceItem(text, item)
    }
}

@Serializable
@Polymorphic(DialogChoice::class)
class ChoiceIcon(override val content: String, val icon: String) : DialogChoice {
    override fun UiScope.icon(scale: Float, progress: Float) {
        Image(icon) {
            modifier.size(18.dp * scale, 18.dp * scale)
                .margin(top = 4.dp * scale, start = 2.dp * scale)
                .tint(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress)))
        }
    }
}

@Serializable
@Polymorphic(DialogChoice::class)
class ChoiceItem(override val content: String, val item: @Serializable(ForItemStack::class) ItemStack) : DialogChoice {
    override fun UiScope.icon(scale: Float, progress: Float) {
        Item(item) {
            modifier.size(18.dp * scale, 18.dp * scale)
                .margin(top = 4.dp * scale, start = 2.dp * scale)
                .tint(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress)))
        }
    }
}