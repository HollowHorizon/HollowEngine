package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.types

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.DefaultText
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.NumberBlock
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

    override suspend fun execute(): Any? {
        return Vec3(x().toDouble(), y().toDouble(), z().toDouble())
    }

    override fun InputSlotScope.composeContent() {
        Text("X") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(x)
        Text("Y") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(y)
        Text("Z") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(z)
        Image("hollowengine:textures/gui/icons/copy.svg") {
            modifier.size(Dimensions.PaddingHuge.scaled(), Dimensions.PaddingHuge.scaled())
                .margin(Dimensions.PaddingNormal.scaled())
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

    private fun InputSlotScope.pastePlayerCoords() {
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

@Serializable
@SerialName("hollowengine:types/block_pos")
class BlockPosBlock : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<BlockPos>()

    val x by input<Number>()
    val y by input<Number>()
    val z by input<Number>()

    override suspend fun execute(): BlockPos {
        return BlockPos(x().toInt(), y().toInt(), z().toInt())
    }

    override fun InputSlotScope.composeContent() {
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

    private fun InputSlotScope.pastePlayerCoords() {
        val pos = Minecraft.getInstance().player?.position() ?: Vec3.ZERO
        inputs["x"] = NumberBlock(pos.x).also {
            it.parentBlock = this@BlockPosBlock; it.color = MdColor.AMBER; it.parentInputName = "x"
        }
        inputs["y"] = NumberBlock(pos.y).also {
            it.parentBlock = this@BlockPosBlock; it.color = MdColor.AMBER; it.parentInputName = "y"
        }
        inputs["z"] = NumberBlock(pos.z).also {
            it.parentBlock = this@BlockPosBlock; it.color = MdColor.AMBER; it.parentInputName = "z"
        }
        notifyChanged()
    }
}

@Serializable
@SerialName("hollowengine:types/world/overworld")
class GetOverworldBlock: ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<ResourceKey<Level>>()

    override suspend fun execute(): ResourceKey<Level> = Level.OVERWORLD

    override fun InputSlotScope.composeContent() {
        DefaultText("Обычный Мир")
    }
}

@Serializable
@SerialName("hollowengine:types/world/nether")
class GetNetherBlock: ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<ResourceKey<Level>>()

    override suspend fun execute(): ResourceKey<Level> = Level.NETHER

    override fun InputSlotScope.composeContent() {
        DefaultText("Незер")
    }
}

@Serializable
@SerialName("hollowengine:types/world/the_end")
class GetTheEndBlock: ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<ResourceKey<Level>>()

    override suspend fun execute(): ResourceKey<Level> = Level.END

    override fun InputSlotScope.composeContent() {
        DefaultText("Энд")
    }
}