package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionTypes

@Serializable
@SerialName("hollowengine:types/location")
class PositionBlock : CodeBlock(), ExpressionBlock {
    @Transient
    override val expressionType = ExpressionTypes.VEC3

    override suspend fun execute(context: BlockContext): Any {
        val x = inputs["x"]?.execute(context).toString().toDoubleOrNull() ?: 0.0
        val y = inputs["y"]?.execute(context).toString().toDoubleOrNull() ?: 0.0
        val z = inputs["z"]?.execute(context).toString().toDoubleOrNull() ?: 0.0
        return Vec3(x, y, z)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Column(Grow.Std) {
            Row(Grow.Std) {
                Text("XYZ") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
                InputSlot("x", ExpressionTypes.NUMBER)
                InputSlot("y", ExpressionTypes.NUMBER)
                InputSlot("z", ExpressionTypes.NUMBER)
            }
            Box(Grow.Std) {
                Image("hollowengine:textures/gui/icons/paste.png") {
                    modifier.size(sizes.largeGap, sizes.largeGap)
                        .margin(sizes.smallGap)
                        .align(AlignmentX.End, AlignmentY.Center)

                    val isHovered by modifier.hoverable()
                    val color by animateColorAsState(Color.WHITE.withAlpha(if (isHovered) 1f else 0.8f))
                    modifier.tint(color)
                        .onClick {
                            if (it.isLeftClick) {
                                pastePlayerCoords()
                            }
                        }
                }
            }
        }
    }

    private fun BlockEditor.InputSlotScope.pastePlayerCoords() {
        val pos = Minecraft.getInstance().player?.position() ?: Vec3.ZERO
        inputs["x"] = NumberBlock(pos.x).also { it.parentBlock = this@PositionBlock; it.parentInputName = "x" }
        inputs["y"] = NumberBlock(pos.x).also { it.parentBlock = this@PositionBlock; it.parentInputName = "y" }
        inputs["y"] = NumberBlock(pos.x).also { it.parentBlock = this@PositionBlock; it.parentInputName = "z" }
        notifyChanged()
    }
}