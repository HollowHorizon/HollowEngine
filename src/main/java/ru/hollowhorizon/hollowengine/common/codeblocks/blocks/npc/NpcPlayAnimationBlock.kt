package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf

@Serializable
@SerialName("hollowengine:npcs/play_animation")
class NpcPlayAnimationBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    val npc by input<NpcEntity>()
    val animation by input<String>()
    var wrapModeInt = 0

    override suspend fun execute() {
        val npc = npc()
        val anim = animation()
        if (anim.isBlank()) return
        val wrapMode = when (wrapModeInt) {
            1 -> WrapMode.Loop
            2 -> WrapMode.ClampForever
            3 -> WrapMode.PingPong
            else -> WrapMode.Once
        }
        NpcAnimationTransitionPacket(npc.id, from = null, to = anim, wrapMode = wrapMode).sendTrackingEntityAndSelf(npc)
    }

    override fun InputSlotScope.composeContent() {
        Text("Запустить анимацию") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
        Text("=") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(animation)
        Text("режим") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        ComboBox {
            modifier.width(FitContent).items(listOf("Один раз", "Цикл", "Последний кадр", "Пинг-понг"))
                .font(font)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.15f), Dimensions.PaddingSmall.scaled()))
                .zLayer(modifier.zLayer + 10)
                .margin(Dimensions.PaddingSmall.scaled()).padding(Dimensions.PaddingSmall.scaled())
                .alignY(AlignmentY.Center)
            modifier.selectedIndex(wrapModeInt)
            modifier.onItemSelected { wrapModeInt = it; notifyChanged() }
        }
    }
}
