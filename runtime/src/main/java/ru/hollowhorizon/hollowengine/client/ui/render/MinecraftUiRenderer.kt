package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexSorting
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.client.renderer.LightTexture
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import ru.hollowhorizon.hollowengine.client.render.render
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.utils.popPose
import ru.hollowhorizon.hollowengine.client.utils.pushPose
import ru.hollowhorizon.hollowengine.client.utils.setIdentity
import ru.hollowhorizon.hollowengine.common.utils.literal
import java.util.*
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
            is DrawBackdropFilterCommand -> drawBackdropFilter(command)
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
        val scale = Minecraft.getInstance().window.guiScale.toFloat() * LayerSupersampling
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
        layerStack.addLast(
            LayerState(
                rect = command.rect,
                transform = command.transform,
                framebuffer = framebuffer,
                parentClips = parentClips,
                scale = scale,
                filter = command.filter,
                backfaceVisibility = command.backfaceVisibility,
            )
        )
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
            val transform = UiMatrix4.translation(-parentLayer.rect.x, -parentLayer.rect.y, 0f) * layer.transform
            if (!isBackfaceHidden(layer.rect.width, layer.rect.height, transform, layer.backfaceVisibility)) {
                UiTextureEffects.drawTexture(
                    layer.framebuffer.texture,
                    layer.rect.width,
                    layer.rect.height,
                    transform,
                    1f,
                    flipY = true,
                    filter = layer.filter,
                    textureWidth = layer.framebuffer.width.toFloat(),
                    textureHeight = layer.framebuffer.height.toFloat(),
                )
            }
        } else {
            Minecraft.getInstance().mainRenderTarget.bindWrite(true)
            val window = Minecraft.getInstance().window
            GL11.glViewport(0, 0, window.width, window.height)
            restoreMainProjection()
            if (!isBackfaceHidden(layer.rect.width, layer.rect.height, layer.transform, layer.backfaceVisibility)) {
                UiTextureEffects.drawTexture(
                    layer.framebuffer.texture,
                    layer.rect.width,
                    layer.rect.height,
                    layer.transform,
                    1f,
                    flipY = true,
                    filter = layer.filter,
                    textureWidth = layer.framebuffer.width.toFloat(),
                    textureHeight = layer.framebuffer.height.toFloat(),
                )
            }
        }
        RenderSystem.disableDepthTest()
    }

    private fun drawBackdropFilter(command: DrawBackdropFilterCommand) {
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        val target = currentTarget()
        val scratch = framebuffers.acquire(target.width, target.height, exclude = target.framebuffer)
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.framebufferId)
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scratch.framebuffer)
        GL30.glBlitFramebuffer(
            0,
            0,
            target.width,
            target.height,
            0,
            0,
            target.width,
            target.height,
            GL11.GL_COLOR_BUFFER_BIT,
            GL11.GL_NEAREST,
        )
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.framebufferId)
        GL11.glViewport(0, 0, target.width, target.height)
        if (target.logicalWidth > 0f && target.logicalHeight > 0f) {
            configureLayerProjection(target.logicalWidth, target.logicalHeight)
        }

        val local = localRect(command.rect)
        val u0 = (local.x * target.scale / target.width.toFloat()).coerceIn(0f, 1f)
        val u1 = ((local.x + local.width) * target.scale / target.width.toFloat()).coerceIn(0f, 1f)
        val v0 = (local.y * target.scale / target.height.toFloat()).coerceIn(0f, 1f)
        val v1 = ((local.y + local.height) * target.scale / target.height.toFloat()).coerceIn(0f, 1f)
        UiTextureEffects.drawTexturedRegion(
            texture = scratch.texture,
            width = command.rect.width,
            height = command.rect.height,
            transform = transform,
            opacity = command.opacity,
            u0 = u0,
            v0 = v0,
            u1 = u1,
            v1 = v1,
            flipY = true,
            filter = command.filter,
            textureWidth = target.width,
            textureHeight = target.height,
        )
    }

    private fun drawBox(command: DrawBoxCommand) {
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        command.shadows.filterNot { it.inset }.forEach { shadow ->
            drawShadow(command.rect.width, command.rect.height, command.border.radius, shadow, command.opacity, transform, command.filter)
        }
        when (val paint = command.paint) {
            UiResolvedPaint.None -> Unit
            is UiResolvedPaint.Color -> drawLocalPaint(command.rect.width, command.rect.height, command.border.radius, paint.color.withOpacity(command.opacity), transform, command.filter)
            is UiResolvedPaint.LinearGradient -> drawLocalGradient(
                command.rect.width,
                command.rect.height,
                command.border.radius,
                paint.angleDegrees,
                paint.stops,
                command.opacity,
                transform,
                command.filter,
            )
            is UiResolvedPaint.Image -> drawImage(command.rect.width, command.rect.height, paint.source, command.opacity, transform, filter = command.filter)
            is UiResolvedPaint.Shader -> drawLocalPaint(
                command.rect.width,
                command.rect.height,
                command.border.radius,
                UiColor(0.2f, 0.2f, 0.24f, command.opacity),
                transform,
                command.filter,
            )
        }
        if (command.border.width.left.resolve(command.rect.width) > 0f && command.border.color.alpha > 0f) {
            val borderWidth = command.border.width.left.resolve(command.rect.width).coerceAtLeast(1f)
            val borderColor = command.border.color.withOpacity(command.opacity).filtered(command.filter)
            drawLocalBorder(command.rect.width, command.rect.height, command.border.radius, borderWidth, borderColor, transform)
        }
    }

    private fun drawText(command: DrawTextCommand) {
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        val mc = Minecraft.getInstance()
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
                command.color.withOpacity(command.opacity).filtered(command.filter).argb(),
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
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        drawImage(command.rect.width, command.rect.height, command.source, command.opacity, transform, command.fit, command.filter)
    }

    private fun drawImage(
        width: Float,
        height: Float,
        source: String,
        opacity: Float,
        transform: UiMatrix4,
        fit: UiImageFit = UiImageFit.STRETCH,
        filter: UiFilterChain = UiFilterChain.Empty,
    ) {
        val location = ResourceLocation.tryParse(source) ?: return
        RenderSystem.setShaderTexture(0, location)
        UiTextureEffects.drawTexturedQuad(width, height, transform, opacity, flipY = false, fit = fit, texture = location, filter = filter)
    }

    private fun drawItem(command: DrawItemCommand) {
        if (layerStack.isNotEmpty()) return
        if (isBackfaceHidden(command.rect.width, command.rect.height, effective(command.transform), command.backfaceVisibility)) return
        val location = ResourceLocation.tryParse(command.item) ?: return
        val item = BuiltInRegistries.ITEM.getOptional(location).orElse(null) ?: return
        ItemStack(item).render(command.rect.x, command.rect.y, command.rect.width, command.rect.height)
    }

    private fun drawEntity(command: DrawEntityCommand) {
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
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
        drawLocalPaint(command.rect.width, command.rect.height, 0f, UiColor(0.09f, 0.12f, 0.16f, command.opacity), transform, command.filter)
        drawLocalBorder(command.rect.width, command.rect.height, 0f, UiColor(0.28f, 0.42f, 0.58f, command.opacity).filtered(command.filter), transform)
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
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        drawLocalPaint(command.rect.width, command.rect.height, 0f, UiColor(0.08f, 0.08f, 0.1f, command.opacity), transform, command.filter)
        drawLocalPaint(command.rect.width - 16f, 1f, 0f, UiColor(0.4f, 0.7f, 0.9f, command.opacity), transform * UiMatrix4.translation(8f, command.rect.height * 0.5f, 0f), command.filter)
    }

    private fun drawScrollbar(command: DrawScrollbarCommand) {
        val trackColor = UiColor(0f, 0f, 0f, 0.42f * command.opacity)
        val thumbColor = when (command.orientation) {
            ScrollbarOrientation.VERTICAL -> UiColor(0.78f, 0.84f, 0.94f, 0.9f * command.opacity)
            ScrollbarOrientation.HORIZONTAL -> UiColor(0.78f, 0.84f, 0.94f, 0.82f * command.opacity)
        }
        drawSolid(localRect(command.track), trackColor, UiMatrix4.identity(), radius = 3.5f)
        drawSolid(localRect(command.thumb), thumbColor, UiMatrix4.identity(), radius = 3.5f)
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

    private fun currentTarget(): RenderTargetState {
        val layer = layerStack.lastOrNull()
        if (layer != null) {
            return RenderTargetState(
                framebufferId = layer.framebuffer.framebuffer,
                framebuffer = layer.framebuffer,
                width = layer.framebuffer.width,
                height = layer.framebuffer.height,
                logicalWidth = layer.rect.width,
                logicalHeight = layer.rect.height,
                scale = layer.scale,
            )
        }
        val window = Minecraft.getInstance().window
        val target = Minecraft.getInstance().mainRenderTarget
        return RenderTargetState(
            framebufferId = target.frameBufferId,
            framebuffer = null,
            width = window.width,
            height = window.height,
            logicalWidth = window.guiScaledWidth.toFloat(),
            logicalHeight = window.guiScaledHeight.toFloat(),
            scale = window.width / window.guiScaledWidth.toFloat(),
        )
    }
}

private data class RenderTargetState(
    val framebufferId: Int, val framebuffer: UiFramebuffer?, val width: Int, val height: Int,
    val logicalWidth: Float, val logicalHeight: Float, val scale: Float,
)

private data class LayerState(
    val rect: UiRect,
    val transform: UiMatrix4,
    val framebuffer: UiFramebuffer,
    val parentClips: List<UiRect>,
    val scale: Float,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
)

private const val LayerSupersampling = 2f

private fun UiRect.intersect(other: UiRect): UiRect {
    val left = maxOf(x, other.x)
    val top = maxOf(y, other.y)
    val right = minOf(x + width, other.x + other.width)
    val bottom = minOf(y + height, other.y + other.height)
    return UiRect(left, top, maxOf(0f, right - left), maxOf(0f, bottom - top))
}
