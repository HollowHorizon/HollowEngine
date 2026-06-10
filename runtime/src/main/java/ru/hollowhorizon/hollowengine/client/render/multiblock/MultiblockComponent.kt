package ru.hollowhorizon.hollowengine.client.render.multiblock

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.modules.ui2.remember
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ItemBlockRenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import org.joml.Quaternionf
import org.joml.Vector3f
import ru.hollowhorizon.hollowengine.client.kool.GlCanvas
import ru.hollowhorizon.hollowengine.common.multiblock.Multiblock
import ru.hollowhorizon.hollowengine.common.utils.RANDOM
import kotlin.math.max


fun UiScope.Multiblock(
    multiblock: Multiblock,
    scopeName: String? = null,
    layer: Int = 1000,
) = GlCanvas(scopeName = scopeName, {
    val source = Minecraft.getInstance().renderBuffers().bufferSource()
    val blockRenderer = Minecraft.getInstance().blockRenderer
    val modelRenderer = blockRenderer.modelRenderer

    val scale by remember { mutableStateOf(0.9f) }
    val rotX by remember { mutableStateOf(0f) }
    val rotY by remember { mutableStateOf(0f) }
    val offsetX by remember { mutableStateOf(0f) }
    val offsetY by remember { mutableStateOf(0f) }

    val stack = PoseStack()

    val mbX = multiblock.xSize
    val mbY = multiblock.ySize
    val mbZ = multiblock.zSize

    val size = max(mbX, mbY)

    val xSize = width * scale / size
    val ySize = height * scale / size

    val pivotPoint = Vector3f(mbX / 2f, 0f, mbZ / 2f)

    stack.translate(
        x + width / 2.0 - xSize * size / 2 + offsetX,
        y + height / 2.0 + ySize * size / 2 + offsetY,
        0.0
    )
    stack.scale(xSize, -ySize, xSize)

    stack.translate(pivotPoint.x, pivotPoint.y, pivotPoint.z)

    val rotation = Quaternionf()
        .rotateX(rotY * Mth.DEG_TO_RAD)
        .rotateY(rotX * Mth.DEG_TO_RAD)

    stack.mulPose(rotation)
    stack.translate(-pivotPoint.x, -pivotPoint.y, -pivotPoint.z)

    for (y in 0..<mbY) {
        if (y > layer) break
        for (z in 0..<mbZ) {
            for (x in 0..<mbX) {
                val id = x + z * mbZ + y * mbZ * mbX
                val block = multiblock.blocks[id].default()
                stack.pushPose()
                stack.translate(x.toDouble(), y.toDouble(), z.toDouble())

                val buffer: VertexConsumer = source.getBuffer(ItemBlockRenderTypes.getChunkRenderType(block))
                val pos = BlockPos(x, y, z)
                modelRenderer.tesselateWithoutAO(
                    multiblock, blockRenderer.getBlockModel(block), block, pos,
                    stack, buffer, false, RANDOM, block.getSeed(pos), OverlayTexture.NO_OVERLAY
                )

                stack.popPose()
            }
        }
    }
    source.endBatch()

})