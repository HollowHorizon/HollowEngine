package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.opengl.GL11
import ru.hollowhorizon.hollowengine.client.ui.BeginLayerCommand
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.shape.Shape
import ru.hollowhorizon.hollowengine.client.ui.style.UiBackfaceVisibility
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterChain
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

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
    val node: UiNode,
    val rect: UiRect,
    val radius: Float,
    val clipShape: Shape?,
    val transform: UiMatrix4,
    val framebuffer: UiLayerFramebuffer,
    val parentClips: List<UiRect>,
    val scale: Float,
    val filter: UiFilterChain,
    val backfaceVisibility: UiBackfaceVisibility,
    val padding: Float,
    val opacity: Float,
)

internal data class ScissorBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

internal const val GaussianMaxRadiusAtFullResolution = 8f

internal fun gaussianSampleScale(radius: Float): Float =
    max(1f, radius.coerceAtLeast(0f) / GaussianMaxRadiusAtFullResolution)

internal fun gaussianDownsampleFactor(radius: Float): Int =
    ceil(gaussianSampleScale(radius)).toInt()

internal fun gaussianSamplePadding(radius: Float): Int =
    ceil(max(radius * 0.5f, 1f) * 3f).toInt()

internal fun backdropSampleBounds(
    rect: UiRect,
    targetWidth: Int,
    targetHeight: Int,
    scaleX: Float,
    scaleY: Float,
    padding: Int,
): ScissorBounds? {
    if (targetWidth <= 0 || targetHeight <= 0 || scaleX <= 0f || scaleY <= 0f ||
        rect.width <= 0f || rect.height <= 0f
    ) {
        return null
    }
    val safePadding = padding.coerceAtLeast(0)
    val left = (floor(rect.x * scaleX).toInt() - safePadding).coerceIn(0, targetWidth)
    val top = (floor(rect.y * scaleY).toInt() - safePadding).coerceIn(0, targetHeight)
    val right = (ceil((rect.x + rect.width) * scaleX).toInt() + safePadding).coerceIn(0, targetWidth)
    val bottom = (ceil((rect.y + rect.height) * scaleY).toInt() + safePadding).coerceIn(0, targetHeight)
    if (right <= left || bottom <= top) return null
    return ScissorBounds(left, top, right - left, bottom - top)
}

internal const val LayerSupersampling = 1f
internal fun layerPadding(command: BeginLayerCommand): Float = layerPadding(command.filter)

internal fun layerPadding(filter: UiFilterChain): Float {
    val blur = filter.blurRadius()
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

internal fun <T> withCullStatePreserved(block: () -> T): T {
    val cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE)
    return try {
        block()
    } finally {
        if (cullEnabled) {
            RenderSystem.enableCull()
        } else {
            RenderSystem.disableCull()
        }
    }
}

internal fun UiRect.intersect(other: UiRect): UiRect {
    val left = maxOf(x, other.x)
    val top = maxOf(y, other.y)
    val right = minOf(x + width, other.x + other.width)
    val bottom = minOf(y + height, other.y + other.height)
    return UiRect(left, top, maxOf(0f, right - left), maxOf(0f, bottom - top))
}
