package ru.hollowhorizon.hollowengine.client.render

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderDispatcher
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3d
import org.joml.Vector3f
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.kool.EntityModifier
import java.io.File
import kotlin.math.min

object OpenGLUtils {

    fun drawLine(
        bufferbuilder: BufferBuilder, matrix: Matrix4f,
        from: Vector3d, to: Vector3d,
        r: Float, g: Float, b: Float, a: Float,
    ) {
        bufferbuilder.vertex(matrix, from.x.toFloat(), from.y.toFloat() - 0.1f, from.z.toFloat())
            .color(r, g, b, a)
        bufferbuilder.vertex(matrix, to.x.toFloat(), to.y.toFloat() - 0.1f, to.z.toFloat()).color(r, g, b, a)
    }

    fun saveTexture(width: Int, height: Int, texture: Int) {
        NativeImage(width, height, Minecraft.ON_OSX).apply {
            RenderSystem.setShaderTexture(0, texture)
            downloadTexture(0, false)
        }.writeToFile(File("hollowengine/framebuffer_debug.png"))
    }

    fun renderGrid(stack: PoseStack, color: Color, size: Int = 10, step: Float = 1f) {
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.builder

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        RenderSystem.lineWidth(1.0f)

        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR)

        val matrix = stack.last().pose()

        for (i in -size..size) {
            val pos = i * step

            val (r, g, b, a) = if (i == 0) color.withAlpha(0.75f) else color.withAlpha(0.5f)

            buffer.vertex(matrix, -size * step, 0f, pos)
                .color(r, g, b, a).endVertex()
            buffer.vertex(matrix, size * step, 0f, pos)
                .color(r, g, b, a).endVertex()

            buffer.vertex(matrix, pos, 0f, -size * step)
                .color(r, g, b, a).endVertex()
            buffer.vertex(matrix, pos, 0f, size * step)
                .color(r, g, b, a).endVertex()
        }

        tessellator.end()
        RenderSystem.disableBlend()
    }

    fun renderBoundingBox(stack: PoseStack, min: Vec3f, max: Vec3f, color: Color) {
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.builder

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        RenderSystem.lineWidth(1.0f)

        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR)

        val matrix = stack.last().pose()
        val (r, g, b, a) = color

        fun line(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float) {
            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex()
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a).endVertex()
        }

        line(min.x, min.y, min.z, max.x, min.y, min.z)
        line(min.x, min.y, min.z, min.x, max.y, min.z)
        line(min.x, min.y, min.z, min.x, min.y, max.z)

        line(max.x, max.y, max.z, min.x, max.y, max.z)
        line(max.x, max.y, max.z, max.x, min.y, max.z)
        line(max.x, max.y, max.z, max.x, max.y, min.z)

        line(min.x, max.y, min.z, max.x, max.y, min.z)
        line(min.x, max.y, min.z, min.x, max.y, max.z)

        line(max.x, min.y, min.z, max.x, max.y, min.z)
        line(max.x, min.y, min.z, max.x, min.y, max.z)

        line(min.x, min.y, max.z, max.x, min.y, max.z)
        line(min.x, min.y, max.z, min.x, max.y, max.z)

        tessellator.end()
        RenderSystem.disableBlend()
    }
}

operator fun Color.component1() = r
operator fun Color.component2() = g
operator fun Color.component3() = b
operator fun Color.component4() = a

private val CUSTOM_IMGUI_LIGHT_0: Vector3f = Vector3f(-0.3f, 1f, 1f).normalize()
private val CUSTOM_IMGUI_LIGHT_1: Vector3f = Vector3f(0.3f, -1f, -1f).normalize()

fun LivingEntity.render(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    modifier: EntityModifier,
) {

    val stack = PoseStack()
    val xOffset = x + width / 2 + modifier.offset.x
    val yOffset = y + height + modifier.offset.y
    stack.translate(xOffset, yOffset, 0f)
    val newScale = min(width / bbWidth, height / bbHeight) * 0.95f * modifier.scale
    stack.mulPoseMatrix(Matrix4f().scaling(newScale, -newScale, newScale))

    RenderSystem.setShaderLights(
        CUSTOM_IMGUI_LIGHT_0,
        CUSTOM_IMGUI_LIGHT_1
    )
    val mc = Minecraft.getInstance()
    val renderDispatcher = mc.entityRenderDispatcher

    stack.mulPose(Quaternionf().rotateX(modifier.pitch * Mth.DEG_TO_RAD))

    val renderBuffers = mc.renderBuffers()
    use(renderDispatcher, modifier) {
        RenderSystem.runAsFancy {
            renderDispatcher.render(
                this, 0.0, 0.0, 0.0,
                0.0f, 1.0f, stack,
                renderBuffers.bufferSource(), 15728880
            )
        }

        RenderSystem.disableDepthTest()
        renderBuffers.bufferSource().endBatch()
        RenderSystem.enableDepthTest()
    }

    Lighting.setupFor3DItems()
}

fun ItemStack.render(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    scale: Float = 1f,
    rotation: Float = 0f,
    stack: PoseStack = PoseStack(),
) {
    val xOffset = x + width / 2
    val yOffset = y + height / 2
    stack.translate(xOffset, yOffset, 0f)

    stack.mulPoseMatrix(Matrix4f().scaling(1f, -1f, 1f))

    val newScale = min(width, height) * 0.95f * scale
    stack.scale(newScale, newScale, newScale)
    stack.mulPose(Quaternionf().rotateZ(rotation * Mth.DEG_TO_RAD))


    val src = Minecraft.getInstance().renderBuffers().bufferSource()
    val model = Minecraft.getInstance().itemRenderer.getModel(this, Minecraft.getInstance().level, null, 0)

    val flat = !model.usesBlockLight()

    if (flat) Lighting.setupForFlatItems()
    Minecraft.getInstance().itemRenderer.render(
        this,
        ItemDisplayContext.GUI,
        false,
        stack, src, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model
    )

    src.endBatch()
    if (flat) Lighting.setupFor3DItems()
}

fun renderItemDecorations(stack: ItemStack, poseStack: PoseStack, x: Int, y: Int, width: Float, height: Float) {
    if (stack.isBarVisible) {
        val i = stack.barWidth / 16f
        val j = stack.barColor
        val k = (x + width * 0.125f).toInt()
        val l = (y + height * 0.8125f).toInt()
        fill(
            poseStack,
            RenderType.guiOverlay(),
            k,
            l,
            (k + width * 0.8125f).toInt(),
            (l + height * 0.125f).toInt(),
            0,
            -16777216
        )
        fill(
            poseStack, RenderType.guiOverlay(), k, l, (k + i * width).toInt(),
            (l + height * 0.0625f).toInt(), 10,
            j or -16777216
        )
    }
    Minecraft.getInstance().player?.cooldowns?.getCooldownPercent(
        stack.item, TickHandler.partialTick
    )?.let { f ->
        if (f > 0) {
            val k = y + width * (Mth.floor(16.0f * (1.0f - f)) / 16f)
            val l = k + height * Mth.ceil(16.0f * f) / 16f
            fill(poseStack, RenderType.guiOverlay(), x, k.toInt(), (x + width).toInt(), l.toInt(), 0, Int.MAX_VALUE)
        }
    }

}

fun fill(stack: PoseStack, renderType: RenderType, minX: Int, minY: Int, maxX: Int, maxY: Int, z: Int, color: Int) {
    var minX = minX
    var minY = minY
    var maxX = maxX
    var maxY = maxY
    var i: Int
    val matrix4f: Matrix4f = stack.last().pose()
    if (minX < maxX) {
        i = minX
        minX = maxX
        maxX = i
    }
    if (minY < maxY) {
        i = minY
        minY = maxY
        maxY = i
    }
    val src = Minecraft.getInstance().renderBuffers().bufferSource()
    val vertexConsumer: VertexConsumer = src.getBuffer(renderType)

    vertexConsumer.vertex(matrix4f, minX.toFloat(), minY.toFloat(), z.toFloat()).color(color)
    vertexConsumer.vertex(matrix4f, minX.toFloat(), maxY.toFloat(), z.toFloat()).color(color)
    vertexConsumer.vertex(matrix4f, maxX.toFloat(), maxY.toFloat(), z.toFloat()).color(color)
    vertexConsumer.vertex(matrix4f, maxX.toFloat(), minY.toFloat(), z.toFloat()).color(color)
}

fun fill(
    stack: PoseStack,
    vertexConsumer: VertexConsumer,
    minX: Int,
    minY: Int,
    maxX: Int,
    maxY: Int,
    z: Int,
    color: Int,
) {
    var minX = minX
    var minY = minY
    var maxX = maxX
    var maxY = maxY
    var i: Int
    val matrix4f: Matrix4f = stack.last().pose()
    if (minX < maxX) {
        i = minX
        minX = maxX
        maxX = i
    }
    if (minY < maxY) {
        i = minY
        minY = maxY
        maxY = i
    }
    vertexConsumer.vertex(matrix4f, minX.toFloat(), minY.toFloat(), z.toFloat()).color(color)
    vertexConsumer.vertex(matrix4f, minX.toFloat(), maxY.toFloat(), z.toFloat()).color(color)
    vertexConsumer.vertex(matrix4f, maxX.toFloat(), maxY.toFloat(), z.toFloat()).color(color)
    vertexConsumer.vertex(matrix4f, maxX.toFloat(), minY.toFloat(), z.toFloat()).color(color)
}

inline fun LivingEntity.use(dispatcher: EntityRenderDispatcher, modifier: EntityModifier, block: () -> Unit) {
    val yBodyRotOld = yBodyRot
    val yBodyRotOldO = yBodyRotO
    val yRotOld = yRot
    val yRotOldO = yRotO
    val xRotOld = xRot
    val xRotOldO = xRotO
    val yHeadRotOld: Float = yHeadRot
    val yHeadRotOldO: Float = yHeadRotO

    yBodyRot = modifier.yaw
    yBodyRotO = yBodyRot
    yRot = modifier.yaw * modifier.headRotationModifierY
    yRotO = yRot
    xRot = modifier.pitch * modifier.headRotationModifierX
    xRotO = xRot
    yHeadRot = yRot
    yHeadRotO = yHeadRot

    val oldNameStatus = isCustomNameVisible
    isCustomNameVisible = modifier.isCustomNameVisible
    dispatcher.setRenderShadow(modifier.isRenderShadow)

    block()

    if (!modifier.isRenderShadow) dispatcher.setRenderShadow(true)
    isCustomNameVisible = oldNameStatus

    yBodyRot = yBodyRotOld
    yBodyRotO = yBodyRotOldO
    yRot = yRotOld
    yRotO = yRotOldO
    xRot = xRotOld
    xRotO = xRotOldO
    yHeadRot = yHeadRotOld
    yHeadRotO = yHeadRotOldO
}