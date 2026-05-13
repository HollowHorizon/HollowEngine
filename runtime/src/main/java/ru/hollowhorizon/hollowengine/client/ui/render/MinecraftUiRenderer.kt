package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexSorting
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import org.joml.Matrix4f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import ru.hollowhorizon.hollowengine.client.render.render
import ru.hollowhorizon.hollowengine.client.ui.BeginLayerCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawBoxCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawCanvasCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawEntityCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawImageCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawItemCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawScrollbarCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawTextCommand
import ru.hollowhorizon.hollowengine.client.ui.EndLayerCommand
import ru.hollowhorizon.hollowengine.client.ui.PopClipCommand
import ru.hollowhorizon.hollowengine.client.ui.PushClipCommand
import ru.hollowhorizon.hollowengine.client.ui.ScrollbarOrientation
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiImageFit
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiRect
import ru.hollowhorizon.hollowengine.client.ui.UiRenderCommand
import ru.hollowhorizon.hollowengine.client.ui.UiResolvedPaint
import ru.hollowhorizon.hollowengine.client.utils.popPose
import ru.hollowhorizon.hollowengine.client.utils.pushPose
import ru.hollowhorizon.hollowengine.client.utils.setIdentity
import ru.hollowhorizon.hollowengine.common.utils.literal
import java.util.ArrayDeque
import kotlin.math.min
import kotlin.math.sqrt

class MinecraftUiRenderer {
    private val framebuffers = UiFramebufferPool()
    private val layerStack = ArrayDeque<LayerState>()
    private val clipStack = ArrayDeque<UiRect>()
    private var layerProjectionActive = false

    fun render(commands: List<UiRenderCommand>) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableDepthTest()
        GL11.glDepthMask(false)
        commands.forEach(::render)
        disableScissor()
        while (layerStack.isNotEmpty()) finishLayer()
        GL11.glDepthMask(true)
    }

    fun close() {
        while (layerStack.isNotEmpty()) finishLayer()
        framebuffers.close()
    }

    private fun render(command: UiRenderCommand) {
        when (command) {
            is BeginLayerCommand -> beginLayer(command)
            is EndLayerCommand -> finishLayer()
            is PushClipCommand -> pushClip(command.rect)
            is PopClipCommand -> popClip()
            is DrawBoxCommand -> drawBox(command)
            is DrawTextCommand -> drawText(command)
            is DrawImageCommand -> drawImage(command)
            is DrawItemCommand -> drawItem(command)
            is DrawEntityCommand -> drawEntity(command)
            is DrawCanvasCommand -> drawCanvasPlaceholder(command)
            is DrawScrollbarCommand -> drawScrollbar(command)
        }
    }

    private fun beginLayer(command: BeginLayerCommand) {
        val scale = Minecraft.getInstance().window.guiScale.toFloat()
        val width = (command.rect.width * scale).toInt().coerceAtLeast(1)
        val height = (command.rect.height * scale).toInt().coerceAtLeast(1)
        val framebuffer = framebuffers.acquire(width, height)
        val parentClips = clipStack.toList()
        if (!layerProjectionActive) {
            RenderSystem.backupProjectionMatrix()
            RenderSystem.getModelViewStack().pushPose()
            layerProjectionActive = true
        }
        framebuffer.bind()
        GL11.glViewport(0, 0, width, height)
        configureLayerProjection(command.rect.width, command.rect.height)
        GL11.glClearColor(0f, 0f, 0f, 0f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)
        RenderSystem.disableDepthTest()
        layerStack.addLast(LayerState(command.rect, command.transform, framebuffer, parentClips, scale))
        clipStack.clear()
        disableScissor()
    }

    private fun finishLayer() {
        val layer = layerStack.removeLast()
        val parentLayer = layerStack.lastOrNull()
        restoreClips(layer.parentClips)
        if (parentLayer != null) {
            parentLayer.framebuffer.bind()
            GL11.glViewport(0, 0, parentLayer.framebuffer.width, parentLayer.framebuffer.height)
            configureLayerProjection(parentLayer.rect.width, parentLayer.rect.height)
            drawTexture(
                layer.framebuffer.texture,
                layer.rect.width,
                layer.rect.height,
                UiMatrix4.translation(-parentLayer.rect.x, -parentLayer.rect.y, 0f) * layer.transform,
                1f,
                flipY = true,
            )
        } else {
            Minecraft.getInstance().mainRenderTarget.bindWrite(true)
            val window = Minecraft.getInstance().window
            GL11.glViewport(0, 0, window.width, window.height)
            restoreMainProjection()
            drawTexture(layer.framebuffer.texture, layer.rect.width, layer.rect.height, layer.transform, 1f, flipY = true)
        }
        RenderSystem.disableDepthTest()
    }

    private fun drawBox(command: DrawBoxCommand) {
        val transform = effective(command.transform)
        when (val paint = command.paint) {
            UiResolvedPaint.None -> Unit
            is UiResolvedPaint.Color -> drawLocalSolid(command.rect.width, command.rect.height, paint.color.withOpacity(command.opacity), transform)
            is UiResolvedPaint.Image -> drawImage(command.rect.width, command.rect.height, paint.source, command.opacity, transform)
            is UiResolvedPaint.Shader -> drawLocalSolid(command.rect.width, command.rect.height, UiColor(0.2f, 0.2f, 0.24f, command.opacity), transform)
        }
        if (command.border.width.left.resolve(command.rect.width) > 0f && command.border.color.alpha > 0f) {
            drawLocalBorder(command.rect.width, command.rect.height, command.border.color.withOpacity(command.opacity), transform)
        }
    }

    private fun drawText(command: DrawTextCommand) {
        val mc = Minecraft.getInstance()
        val transform = effective(command.transform)
        val origin = transform.transform(0f, 0f)
        val xAxis = transform.transform(1f, 0f)
        val yAxis = transform.transform(0f, 1f)
        val scaleX = sqrt((xAxis.x - origin.x) * (xAxis.x - origin.x) + (xAxis.y - origin.y) * (xAxis.y - origin.y))
        val scaleY = sqrt((yAxis.x - origin.x) * (yAxis.x - origin.x) + (yAxis.y - origin.y) * (yAxis.y - origin.y))
        val lines = mc.font.split(command.text.literal, command.rect.width.toInt().coerceAtLeast(1))
        val maxLines = (command.rect.height / mc.font.lineHeight).toInt().coerceAtLeast(1)
        lines.take(maxLines).forEachIndexed { index, line ->
            val pose = PoseStack()
            pose.translate(origin.x.toDouble(), (origin.y + index * mc.font.lineHeight * scaleY).toDouble(), origin.z.toDouble())
            pose.scale(scaleX, scaleY, 1f)
            mc.font.drawInBatch(
                line,
                0f,
                0f,
                command.color.withOpacity(command.opacity).argb(),
                false,
                pose.last().pose(),
                mc.renderBuffers().bufferSource(),
                DisplayMode.SEE_THROUGH,
                0,
                15728880,
            )
        }
        mc.renderBuffers().bufferSource().endBatch()
    }

    private fun drawImage(command: DrawImageCommand) {
        drawImage(command.rect.width, command.rect.height, command.source, command.opacity, effective(command.transform), command.fit)
    }

    private fun drawImage(
        width: Float,
        height: Float,
        source: String,
        opacity: Float,
        transform: UiMatrix4,
        fit: UiImageFit = UiImageFit.STRETCH,
    ) {
        val location = ResourceLocation.tryParse(source) ?: return
        RenderSystem.setShaderTexture(0, location)
        drawTexturedQuad(width, height, transform, opacity, flipY = false, fit = fit, texture = location)
    }

    private fun drawItem(command: DrawItemCommand) {
        if (layerStack.isNotEmpty()) return
        val location = ResourceLocation.tryParse(command.item) ?: return
        val item = BuiltInRegistries.ITEM.getOptional(location).orElse(null) ?: return
        ItemStack(item).render(command.rect.x, command.rect.y, command.rect.width, command.rect.height)
    }

    private fun drawEntity(command: DrawEntityCommand) {
        if (layerStack.isEmpty()) {
            val entity = when (command.entity) {
                "player" -> Minecraft.getInstance().player
                else -> null
            }
            if (entity != null) {
                renderEntity(entity, command.rect)
                return
            }
        }
        drawEntityPlaceholder(command)
    }

    private fun drawEntityPlaceholder(command: DrawEntityCommand) {
        val transform = effective(command.transform)
        drawLocalSolid(command.rect.width, command.rect.height, UiColor(0.09f, 0.12f, 0.16f, command.opacity), transform)
        drawLocalBorder(command.rect.width, command.rect.height, UiColor(0.28f, 0.42f, 0.58f, command.opacity), transform)
    }

    private fun renderEntity(entity: LivingEntity, rect: UiRect) {
        val stack = PoseStack()
        val xOffset = rect.x + rect.width / 2f
        val yOffset = rect.y + rect.height
        stack.translate(xOffset.toDouble(), yOffset.toDouble(), 0.0)
        val scale = min(rect.width / entity.bbWidth, rect.height / entity.bbHeight) * 0.92f
        stack.mulPose(Axis.ZP.rotationDegrees(180f))
        stack.scale(scale, scale, scale)
        stack.mulPose(Quaternionf().rotateY(25f * Mth.DEG_TO_RAD))

        val light0 = Vector3f(-0.3f, 1f, 1f).normalize()
        val light1 = Vector3f(0.3f, -1f, -1f).normalize()
        RenderSystem.setShaderLights(light0, light1)

        val mc = Minecraft.getInstance()
        val dispatcher = mc.entityRenderDispatcher
        val buffers = mc.renderBuffers().bufferSource()
        dispatcher.setRenderShadow(false)
        RenderSystem.runAsFancy {
            dispatcher.render(entity, 0.0, 0.0, 0.0, 0f, 1f, stack, buffers, LightTexture.FULL_BRIGHT)
        }
        buffers.endBatch()
        dispatcher.setRenderShadow(true)
        Lighting.setupFor3DItems()
    }

    private fun drawCanvasPlaceholder(command: DrawCanvasCommand) {
        val transform = effective(command.transform)
        drawLocalSolid(command.rect.width, command.rect.height, UiColor(0.08f, 0.08f, 0.1f, command.opacity), transform)
        drawLocalSolid(command.rect.width - 16f, 1f, UiColor(0.4f, 0.7f, 0.9f, command.opacity), transform * UiMatrix4.translation(8f, command.rect.height * 0.5f, 0f))
    }

    private fun drawScrollbar(command: DrawScrollbarCommand) {
        val trackColor = UiColor(0f, 0f, 0f, 0.42f * command.opacity)
        val thumbColor = when (command.orientation) {
            ScrollbarOrientation.VERTICAL -> UiColor(0.78f, 0.84f, 0.94f, 0.9f * command.opacity)
            ScrollbarOrientation.HORIZONTAL -> UiColor(0.78f, 0.84f, 0.94f, 0.82f * command.opacity)
        }
        drawSolid(localRect(command.track), trackColor, UiMatrix4.identity())
        drawSolid(localRect(command.thumb), thumbColor, UiMatrix4.identity())
    }

    private fun drawLocalBorder(width: Float, height: Float, color: UiColor, transform: UiMatrix4) {
        drawLocalSolid(width, 1f, color, transform)
        drawLocalSolid(width, 1f, color, transform * UiMatrix4.translation(0f, height - 1f, 0f))
        drawLocalSolid(1f, height, color, transform)
        drawLocalSolid(1f, height, color, transform * UiMatrix4.translation(width - 1f, 0f, 0f))
    }

    private fun drawSolid(rect: UiRect, color: UiColor, transform: UiMatrix4) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        val corners = rect.corners(transform)
        buffer.addVertex(corners[0].x, corners[0].y, corners[0].z).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(corners[1].x, corners[1].y, corners[1].z).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(corners[2].x, corners[2].y, corners[2].z).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(corners[3].x, corners[3].y, corners[3].z).setColor(color.red, color.green, color.blue, color.alpha)
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }

    private fun drawLocalSolid(width: Float, height: Float, color: UiColor, transform: UiMatrix4) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        val corners = localCorners(width, height, transform)
        buffer.addVertex(corners[0].x, corners[0].y, corners[0].z).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(corners[1].x, corners[1].y, corners[1].z).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(corners[2].x, corners[2].y, corners[2].z).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(corners[3].x, corners[3].y, corners[3].z).setColor(color.red, color.green, color.blue, color.alpha)
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }

    private fun drawTexture(texture: Int, width: Float, height: Float, transform: UiMatrix4, opacity: Float, flipY: Boolean) {
        GlStateManager._bindTexture(texture)
        RenderSystem.setShaderTexture(0, texture)
        drawTexturedQuad(width, height, transform, opacity, flipY)
    }

    private fun drawTexturedQuad(
        width: Float,
        height: Float,
        transform: UiMatrix4,
        opacity: Float,
        flipY: Boolean,
        fit: UiImageFit = UiImageFit.STRETCH,
        texture: ResourceLocation? = null,
    ) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        val placement = imagePlacement(width, height, fit, texture)
        val corners = localCorners(placement.width, placement.height, transform * UiMatrix4.translation(placement.x, placement.y, 0f))
        val top = if (flipY) placement.v1 else placement.v0
        val bottom = if (flipY) placement.v0 else placement.v1
        buffer.addVertex(corners[0].x, corners[0].y, corners[0].z).setUv(placement.u0, top).setColor(1f, 1f, 1f, opacity)
        buffer.addVertex(corners[1].x, corners[1].y, corners[1].z).setUv(placement.u0, bottom).setColor(1f, 1f, 1f, opacity)
        buffer.addVertex(corners[2].x, corners[2].y, corners[2].z).setUv(placement.u1, bottom).setColor(1f, 1f, 1f, opacity)
        buffer.addVertex(corners[3].x, corners[3].y, corners[3].z).setUv(placement.u1, top).setColor(1f, 1f, 1f, opacity)
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }

    private fun pushClip(rect: UiRect) {
        val local = localRect(rect)
        clipStack.addLast(clipStack.lastOrNull()?.intersect(local) ?: local)
        applyScissor(clipStack.last())
    }

    private fun popClip() {
        if (clipStack.isNotEmpty()) clipStack.removeLast()
        clipStack.lastOrNull()?.let(::applyScissor) ?: disableScissor()
    }

    private fun applyScissor(rect: UiRect) {
        val layer = layerStack.lastOrNull()
        if (layer != null) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
            GL11.glScissor(
                (rect.x * layer.scale).toInt(),
                (layer.framebuffer.height - (rect.y + rect.height) * layer.scale).toInt(),
                (rect.width * layer.scale).toInt().coerceAtLeast(0),
                (rect.height * layer.scale).toInt().coerceAtLeast(0),
            )
            return
        }
        val window = Minecraft.getInstance().window
        val scaleX = window.width / window.guiScaledWidth.toFloat()
        val scaleY = window.height / window.guiScaledHeight.toFloat()
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(
            (rect.x * scaleX).toInt(),
            (window.height - (rect.y + rect.height) * scaleY).toInt(),
            (rect.width * scaleX).toInt().coerceAtLeast(0),
            (rect.height * scaleY).toInt().coerceAtLeast(0),
        )
    }

    private fun disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
    }

    private fun effective(transform: UiMatrix4): UiMatrix4 {
        val layer = layerStack.lastOrNull() ?: return transform
        return (layer.transform.inverse() ?: UiMatrix4.translation(-layer.rect.x, -layer.rect.y, 0f)) * transform
    }

    private fun localRect(rect: UiRect): UiRect {
        val layer = layerStack.lastOrNull() ?: return rect
        return rect.copy(x = rect.x - layer.rect.x, y = rect.y - layer.rect.y)
    }

    private fun restoreClips(clips: List<UiRect>) {
        clipStack.clear()
        clipStack.addAll(clips)
        clipStack.lastOrNull()?.let(::applyScissor) ?: disableScissor()
    }

    private fun configureLayerProjection(width: Float, height: Float) {
        RenderSystem.setProjectionMatrix(
            Matrix4f().setOrtho(0f, width, height, 0f, -1000f, 1000f),
            VertexSorting.ORTHOGRAPHIC_Z,
        )
        val stack = RenderSystem.getModelViewStack()
        stack.setIdentity()
        RenderSystem.applyModelViewMatrix()
    }

    private fun restoreMainProjection() {
        if (!layerProjectionActive || layerStack.isNotEmpty()) return
        RenderSystem.restoreProjectionMatrix()
        val stack = RenderSystem.getModelViewStack()
        stack.popPose()
        RenderSystem.applyModelViewMatrix()
        layerProjectionActive = false
    }
}

private data class LayerState(
    val rect: UiRect,
    val transform: UiMatrix4,
    val framebuffer: UiFramebuffer,
    val parentClips: List<UiRect>,
    val scale: Float,
)

private data class ImagePlacement(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val u0: Float = 0f,
    val v0: Float = 0f,
    val u1: Float = 1f,
    val v1: Float = 1f,
)

private fun imagePlacement(width: Float, height: Float, fit: UiImageFit, texture: ResourceLocation?): ImagePlacement {
    val size = texture?.let(::textureSize) ?: return ImagePlacement(0f, 0f, width, height)
    val sourceAspect = size.first / size.second
    val targetAspect = width / height
    return when (fit) {
        UiImageFit.STRETCH -> ImagePlacement(0f, 0f, width, height)
        UiImageFit.NONE -> ImagePlacement((width - size.first) * 0.5f, (height - size.second) * 0.5f, size.first, size.second)
        UiImageFit.CONTAIN -> {
            val drawWidth: Float
            val drawHeight: Float
            if (sourceAspect > targetAspect) {
                drawWidth = width
                drawHeight = width / sourceAspect
            } else {
                drawHeight = height
                drawWidth = height * sourceAspect
            }
            ImagePlacement((width - drawWidth) * 0.5f, (height - drawHeight) * 0.5f, drawWidth, drawHeight)
        }
        UiImageFit.COVER -> {
            val cropX: Float
            val cropY: Float
            if (sourceAspect > targetAspect) {
                cropX = (1f - targetAspect / sourceAspect) * 0.5f
                cropY = 0f
            } else {
                cropX = 0f
                cropY = (1f - sourceAspect / targetAspect) * 0.5f
            }
            ImagePlacement(0f, 0f, width, height, cropX, cropY, 1f - cropX, 1f - cropY)
        }
    }
}

private fun textureSize(location: ResourceLocation): Pair<Float, Float>? {
    val texture = Minecraft.getInstance().textureManager.getTexture(location)
    val id = texture.id
    GlStateManager._bindTexture(id)
    val width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)
    val height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT)
    if (width <= 0 || height <= 0) return null
    return width.toFloat() to height.toFloat()
}

private class UiFramebufferPool {
    private val framebuffers = mutableListOf<UiFramebuffer>()

    fun acquire(width: Int, height: Int): UiFramebuffer {
        val framebuffer = framebuffers.firstOrNull { it.width == width && it.height == height } ?: UiFramebuffer(width, height).also {
            framebuffers += it
        }
        return framebuffer
    }

    fun close() {
        framebuffers.forEach(UiFramebuffer::close)
        framebuffers.clear()
    }
}

private class UiFramebuffer(
    val width: Int,
    val height: Int,
) {
    val framebuffer: Int = GL30.glGenFramebuffers()
    val texture: Int = GL11.glGenTextures()
    private val depth: Int = GL30.glGenRenderbuffers()

    init {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L)
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0)
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depth)
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT24, width, height)
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depth)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer)
    }

    fun close() {
        GL30.glDeleteFramebuffers(framebuffer)
        GL11.glDeleteTextures(texture)
        GL30.glDeleteRenderbuffers(depth)
    }
}

private fun UiRect.corners(transform: UiMatrix4) = arrayOf(
    transform.transform(x, y),
    transform.transform(x, y + height),
    transform.transform(x + width, y + height),
    transform.transform(x + width, y),
)

private fun localCorners(width: Float, height: Float, transform: UiMatrix4) = arrayOf(
    transform.transform(0f, 0f),
    transform.transform(0f, height),
    transform.transform(width, height),
    transform.transform(width, 0f),
)

private fun UiRect.intersect(other: UiRect): UiRect {
    val left = maxOf(x, other.x)
    val top = maxOf(y, other.y)
    val right = minOf(x + width, other.x + other.width)
    val bottom = minOf(y + height, other.y + other.height)
    return UiRect(left, top, maxOf(0f, right - left), maxOf(0f, bottom - top))
}

private fun UiColor.withOpacity(opacity: Float) = copy(alpha = alpha * opacity)

private fun UiColor.argb(): Int {
    val a = (alpha * 255f).toInt().coerceIn(0, 255)
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return a shl 24 or (r shl 16) or (g shl 8) or b
}
