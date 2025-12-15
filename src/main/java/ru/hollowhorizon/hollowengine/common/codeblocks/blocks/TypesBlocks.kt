package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:types/location")
class PositionBlock : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<Vec3>()

    val x by input<Number>()
    val y by input<Number>()
    val z by input<Number>()

    override suspend fun BlockContext.execute(): Any? {
        return Vec3(x().toDouble(), y().toDouble(), z().toDouble())
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("X") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(x)
        Text("Y") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(y)
        Text("Z") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(z)
        Image("hollowengine:textures/gui/icons/copy.svg") {
            modifier.size(sizes.largeGap * 1.5f, sizes.largeGap * 1.5f)
                .margin(sizes.smallGap)
                .align(AlignmentX.End, AlignmentY.Center)

            val isHovered by modifier.hoverable()
            val color by animateColorAsState(Color.WHITE.mulRgb(if (isHovered) 1f else 0.8f))
            modifier.tint(color)
                .onClick {
                    if (it.isLeftClick) {
                        pastePlayerCoords()
                    }
                }
        }
    }

    private fun BlockEditor.InputSlotScope.pastePlayerCoords() {
        val pos = Minecraft.getInstance().player?.position() ?: Vec3.ZERO
        inputs["x"] = NumberBlock(pos.x).also {
            it.parentBlock = this@PositionBlock; it.color = MdColor.AMBER; it.parentInputName = "x"
        }
        inputs["y"] = NumberBlock(pos.y).also {
            it.parentBlock = this@PositionBlock; it.color = MdColor.AMBER; it.parentInputName = "y"
        }
        inputs["z"] = NumberBlock(pos.z).also {
            it.parentBlock = this@PositionBlock; it.color = MdColor.AMBER; it.parentInputName = "z"
        }
        notifyChanged()
    }
}