package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.GL11
import ru.hollowhorizon.hollowengine.client.ui.*
import kotlin.math.ceil

data class UiRenderTarget(
    val framebufferId: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val logicalWidth: Float,
    val logicalHeight: Float,
    val scale: Float,
)

internal data class RenderTargetState(
    val framebufferId: Int,
    val framebuffer: UiFramebuffer?,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val logicalWidth: Float,
    val logicalHeight: Float,
    val scale: Float,
)

internal data class LayerState(
    val rect: UiRect,
    val radius: Float,
    val transform: UiMatrix4,
    val framebuffer: UiLayerFramebuffer,
    val parentClips: List<UiRect>,
    val scale: Float,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
    val padding: Float,
)

internal const val LayerSupersampling = 1f
internal const val LayerTextureSubdivisions = 12

internal fun layerPadding(command: BeginLayerCommand): Float {
    val blur = command.filter.blurRadius()
    val guard = 12f
    return ceil(guard + blur * 3f).coerceAtLeast(guard)
}

internal fun configureUiBlend() {
    RenderSystem.enableBlend()
    RenderSystem.blendFuncSeparate(
        GlStateManager.SourceFactor.SRC_ALPHA,
        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
        GlStateManager.SourceFactor.ONE,
        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
    )
}

internal fun disableScissor() {
    GL11.glDisable(GL11.GL_SCISSOR_TEST)
}

internal fun UiRect.intersect(other: UiRect): UiRect {
    val left = maxOf(x, other.x)
    val top = maxOf(y, other.y)
    val right = minOf(x + width, other.x + other.width)
    val bottom = minOf(y + height, other.y + other.height)
    return UiRect(left, top, maxOf(0f, right - left), maxOf(0f, bottom - top))
}
