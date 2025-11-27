package ru.hollowhorizon.hollowengine.client.kool

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexSorting
import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.BackendScope
import kotlinx.coroutines.launch
import org.joml.Matrix4f
import org.lwjgl.opengl.GL33
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalContracts::class)
inline fun UiScope.GlCanvas(
    scopeName: String? = null,
    noinline glCanvas: DrawContext.() -> Unit,
    block: GlCanvasScope.() -> Unit = {},
) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val image = uiNode.createChild(scopeName, GlCanvasNode::class, GlCanvasNode.factory)
    image.modifier.drawer = glCanvas
    image.block()
}

interface GlCanvasScope : ImageScope {
    override val modifier: GlCanvasModifier
}

open class GlCanvasModifier(surface: UiSurface) : ImageModifier(surface) {
    var drawer: DrawContext.() -> Unit by property(DrawContext.DEFAULT)
}

data class DrawContext(
    val mouseX: Float,
    val mouseY: Float,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
) {
    inline val x get() = x1
    inline val y get() = y1
    inline val width get() = x2 - x1
    inline val height get() = y2 - y1

    companion object {
        val DEFAULT: DrawContext.() -> Unit = {}
    }
}

open class GlCanvasNode(parent: UiNode?, surface: UiSurface) : UiNode(parent, surface), GlCanvasScope {
    override val modifier = GlCanvasModifier(surface)

    val imageWidth = mutableStateOf(1f)
    val imageHeight = mutableStateOf(1f)

    private val imgUvTpLt: Vec2f get() = modifier.imageProvider?.uvTopLeft ?: Vec2f.ZERO
    private val imgUvTpRt: Vec2f get() = modifier.imageProvider?.uvTopRight ?: Vec2f.ZERO
    private val imgUvBtLt: Vec2f get() = modifier.imageProvider?.uvBottomLeft ?: Vec2f.ZERO
    private val imgUvBtRt: Vec2f get() = modifier.imageProvider?.uvBottomRight ?: Vec2f.ZERO
    private var imageAr = 1f

    override fun measureContentSize(ctx: KoolContext) {
        imageAr = 1f
        if (modifier.imageSize != ImageSize.Stretch) {
            imageAr = modifier.imageProvider?.getImageAspectRatio() ?: 1f
            updateImageSize()
            if (modifier.imageProvider?.isDynamicSize == true) {
                surface.onEachFrame { updateImageSize() }
            }
        }

        val modWidth = modifier.width
        val modHeight = modifier.height
        val measuredWidth: Float
        val measuredHeight: Float

        val padH = paddingStartPx + paddingEndPx
        val padV = paddingTopPx + paddingBottomPx

        when {
            modWidth is Dp && modHeight is Dp -> {
                measuredWidth = modWidth.px
                measuredHeight = modHeight.px
            }

            modWidth is Dp && modHeight !is Dp -> {
                // fixed width, measured height depends on width and chosen image size mode
                measuredWidth = modWidth.px
                measuredHeight = imageHeight(measuredWidth - padH, imageAr) + padV
            }

            modWidth !is Dp && modHeight is Dp -> {
                // fixed height, measured width depends on height and chosen image size mode
                measuredHeight = modHeight.px
                measuredWidth = imageWidth(measuredHeight - padV, imageAr) + padH
            }

            else -> {
                // dynamic (fit / grow) width and height
                val scale = (modifier.imageSize as? ImageSize.FixedScale)?.scale ?: 1f
                measuredWidth = imageWidth.use() * scale + padH
                measuredHeight = imageHeight.use() * scale + padV
            }
        }
        setContentSize(measuredWidth, measuredHeight)

        surface.onEachFrame {
            BackendScope.launch {
                drawGlCanvas(
                    leftPx, topPx,
                    rightPx, bottomPx,
                    modifier.zLayer
                )
            }
        }
    }

    private fun FlatImageProvider.resizeImage() = apply {
        val u0 = (leftPx) / WINDOW_BUFFER.width
        val v0 = 1f - (topPx) / WINDOW_BUFFER.height

        val u1 = (rightPx) / WINDOW_BUFFER.width
        val v1 = 1f - (bottomPx) / WINDOW_BUFFER.height
        uvTopLeft.set(u0, v0)
        uvTopRight.set(u1, v0)
        uvBottomLeft.set(u0, v1)
        uvBottomRight.set(u1, v1)
    }

    private fun updateImageSize() {
        imageWidth.set(max(1f, modifier.imageProvider?.getImageWidth() ?: 1f))
        imageHeight.set(max(1f, modifier.imageProvider?.getImageHeight() ?: 1f))
    }

    private fun imageWidth(measuredHeightPx: Float, imageAr: Float): Float {
        // make sure we use() image dimensions, so we get updated when they change
        imageWidth.use()
        val imgH = imageHeight.use()
        return when (val sz = modifier.imageSize) {
            ImageSize.Stretch -> measuredHeightPx
            ImageSize.FitContent -> measuredHeightPx * imageAr
            ImageSize.ZoomContent -> measuredHeightPx * imageAr
            is ImageSize.FixedScale -> imgH * sz.scale
        }
    }

    private fun imageHeight(measuredWidthPx: Float, imageAr: Float): Float {
        // make sure we use() image dimensions, so we get updated when they change
        imageHeight.use()
        val imgW = imageWidth.use()
        return when (val sz = modifier.imageSize) {
            ImageSize.Stretch -> measuredWidthPx
            ImageSize.FitContent -> measuredWidthPx / imageAr
            ImageSize.ZoomContent -> measuredWidthPx / imageAr
            is ImageSize.FixedScale -> imgW * sz.scale
        }
    }

    override fun render(ctx: KoolContext) {
        super.render(ctx)
        modifier.imageProvider(FlatImageProvider(WINDOW_BUFFER, true).resizeImage()).imageSize(ImageSize.Stretch)
        modifier.imageProvider?.getTexture(innerWidthPx, innerHeightPx)?.let {
            val imgMesh = surface.getMeshLayer(modifier.zLayer + modifier.imageZ).addImage(it)
            imgMesh.builder.clear()
            imgMesh.builder.configured(modifier.tint) {
                rect {
                    isCenteredOrigin = false

                    texCoordLowerLeft.set(imgUvTpLt)
                    texCoordLowerRight.set(imgUvTpRt)
                    texCoordUpperLeft.set(imgUvBtLt)
                    texCoordUpperRight.set(imgUvBtRt)

                    val imgW = imageWidth.value
                    val imgH = imageHeight.value
                    val cx = widthPx * 0.5f
                    val cy = heightPx * 0.5f

                    when (val sz = modifier.imageSize) {
                        ImageSize.FitContent -> {
                            val s = min(innerWidthPx / imgW, innerHeightPx / imgH)
                            origin.set(cx - imgW * s * 0.5f, cy - imgH * s * 0.5f, 0f)
                            size.set(imgW * s, imgH * s)
                        }

                        ImageSize.ZoomContent -> {
                            val s = max(innerWidthPx / imgW, innerHeightPx / imgH)
                            origin.set(cx - imgW * s * 0.5f, cy - imgH * s * 0.5f, 0f)
                            size.set(imgW * s, imgH * s)
                        }

                        ImageSize.Stretch -> {
                            origin.set(paddingStartPx, paddingTopPx, 0f)
                            size.set(innerWidthPx, innerHeightPx)
                        }

                        is ImageSize.FixedScale -> {
                            val s = sz.scale
                            origin.set(cx - imageWidth.value * s * 0.5f, cy - imageHeight.value * s * 0.5f, 0f)
                            size.set(imageWidth.value * s, imageHeight.value * s)
                        }
                    }
                }
            }
            imgMesh.applyShader(it, modifier.customShader)
        }
    }

    private fun drawGlCanvas(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        zLayer: Int,
    ) {
        if (x1 >= x2 || y1 >= y2) return
        val oldBuffer = GL33.glGetInteger(GL33.GL_FRAMEBUFFER_BINDING)
        val buffer = guiFramebuffer
        buffer.bindWrite(false)

        RenderSystem.backupProjectionMatrix()
        RenderSystem.setProjectionMatrix(
            Matrix4f().setOrtho(
                0.0F, buffer.width.toFloat(), buffer.height.toFloat(), 0.0F, 0.0F, 5000.0F
            ), VertexSorting.ORTHOGRAPHIC_Z
        )
        val matrix4fstack = RenderSystem.getModelViewStack()
        matrix4fstack.pushPose()
        matrix4fstack.setIdentity()
        matrix4fstack.translate(0.0f, 0.0f, -3000.0f + zLayer)
        RenderSystem.applyModelViewMatrix()

        // Очищаем участок где будем рисовать
        GL33.glEnable(GL33.GL_SCISSOR_TEST)
        GL33.glScissor(
            x1.toInt(), buffer.height - y2.toInt(),
            (x2 - x1).toInt(), (y2 - y1).toInt(),
        )
        GL33.glClearColor(0f, 0f, 0f, 0f)
        GL33.glClear(GL33.GL_COLOR_BUFFER_BIT or GL33.GL_DEPTH_BUFFER_BIT)

        // Сама отрисовка
        RenderSystem.enableDepthTest()
        RenderSystem.depthFunc(GL33.GL_LEQUAL)
        val mouse = PointerInput.primaryPointer.pos
        modifier.drawer(DrawContext(mouse.x, mouse.y, x1, y1, x2, y2))
        RenderSystem.disableDepthTest()

        // Возвращаем параметры
        GL33.glDisable(GL33.GL_SCISSOR_TEST)
        RenderSystem.restoreProjectionMatrix()

        matrix4fstack.popPose()

        RenderSystem.applyModelViewMatrix()
        RenderSystem.disableCull()

        buffer.unbindWrite()
        GL33.glBindFramebuffer(GL33.GL_FRAMEBUFFER, oldBuffer)
    }

    companion object {
        val factory: (UiNode, UiSurface) -> GlCanvasNode = { parent, surface -> GlCanvasNode(parent, surface) }
    }
}