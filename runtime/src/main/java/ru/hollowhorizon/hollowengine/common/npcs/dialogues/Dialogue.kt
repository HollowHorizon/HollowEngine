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
class DialogueUpdateEvent(
    val tag: @Serializable(ForCompoundNBT::class) CompoundTag
) : HollowPacket, Event {

    override fun handle(player: Player) {
        EventBus.post(this)
    }
}

interface DialogChoice {
    val content: String

    fun UiScope.buildIcon(scale: Float, progress: Float)

    companion object {
        private const val DEFAULT_ICON_PATH = "hollowengine:textures/gui/dialogues/simple.png"

        fun simple(text: String, iconPath: String = DEFAULT_ICON_PATH): DialogChoice =
            ChoiceIcon(text, iconPath)

        fun item(text: String, itemStack: ItemStack): DialogChoice =
            ChoiceItem(text, itemStack)
    }
}

@Serializable
@Polymorphic(DialogChoice::class)
class ChoiceIcon(
    override val content: String,
    val iconPath: String
) : DialogChoice {

    companion object {
        private const val ICON_SIZE_DP = 18f
        private const val ICON_MARGIN_TOP_DP = 4f
        private const val ICON_MARGIN_START_DP = 2f
        private val FULL_WHITE = Color(1f, 1f, 1f, 1f)
    }

    override fun UiScope.buildIcon(scale: Float, progress: Float) {
        val tintColor = FULL_WHITE.withAlpha(Interpolation.QUAD_IN(progress))

        Image(iconPath) {
            modifier
                .size(ICON_SIZE_DP.dp * scale, ICON_SIZE_DP.dp * scale)
                .margin(
                    top = ICON_MARGIN_TOP_DP.dp * scale,
                    start = ICON_MARGIN_START_DP.dp * scale
                )
                .tint(tintColor)
        }
    }
}

@Serializable
@Polymorphic(DialogChoice::class)
class ChoiceItem(
    override val content: String,
    val itemStack: @Serializable(ForItemStack::class) ItemStack
) : DialogChoice {

    companion object {
        private const val ICON_SIZE_DP = 18f
        private const val ICON_MARGIN_TOP_DP = 4f
        private const val ICON_MARGIN_START_DP = 2f
        private val FULL_WHITE = Color(1f, 1f, 1f, 1f)
    }

    override fun UiScope.buildIcon(scale: Float, progress: Float) {
        val tintColor = FULL_WHITE.withAlpha(Interpolation.QUAD_IN(progress))

        Item(itemStack) {
            modifier
                .size(ICON_SIZE_DP.dp * scale, ICON_SIZE_DP.dp * scale)
                .margin(
                    top = ICON_MARGIN_TOP_DP.dp * scale,
                    start = ICON_MARGIN_START_DP.dp * scale
                )
                .tint(tintColor)
        }
    }
}

private fun Color.withAlpha(alpha: Float): Color =
    Color(r, g, b, this.a * alpha)