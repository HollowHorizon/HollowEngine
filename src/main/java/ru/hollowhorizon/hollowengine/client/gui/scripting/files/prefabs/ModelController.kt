package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import com.mojang.blaze3d.vertex.PoseStack
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.animateSpringFloatAsState
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.kool.GlCanvasModifier
import ru.hollowhorizon.hollowengine.client.kool.GlCanvasNode
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.client.render.OpenGLUtils
import ru.hollowhorizon.hollowengine.client.utils.exists
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.min

class ModelController {
    val model = mutableStateOf("hollowengine:models/entity/player_model.gltf")
        .onChange { old, new ->
            if (ResourceLocation.isValidResourceLocation(new) && new.rl.exists()) {
                attachment = ModelAttachment(new)
                animations = attachment.animations.map { it }
            }
        }
    var attachment: ModelAttachment = ModelAttachment(model.value)
    var animations = attachment.animations.map { it }
    var animationId = mutableStateOf(0)
    val animationEnabled = mutableStateOf(false)

    var scale: Float = 1f
        private set

    private val zoom = mutableStateOf(1f)
    val offsetX = mutableStateOf(0f)
    val offsetY = mutableStateOf(0f)
    val yaw = mutableStateOf(0f)
    val pitch = mutableStateOf(0f)
    val isGridVisible = mutableStateOf(false)

    context(scope: UiScope)
    operator fun invoke() = with(scope) {
        surface.triggerUpdate() // Анимация должна обновлять виджет
        val zoomState = zoom.use()
        val smoothedZoom by animateSpringFloatAsState(zoomState)
        scale = smoothedZoom

        val yaw = yaw.use()
        val pitch = pitch.use()

        Model(attachment, "Model-Renderer") {
            modifier.size(Grow.Std, Grow.Std)
                .margin(Dimensions.PaddingMedium)
                .showGrid(isGridVisible.use())
                .scale(scale)
                .yaw(yaw).pitch(pitch)
                .offsetX(offsetX.use()).offsetY(offsetY.use())
                .onDrag {
                    if (it.pointer.isLeftButtonDown) {
                        this@ModelController.yaw.set((yaw + it.pointer.delta.x / 10) % 360)
                        this@ModelController.pitch.set((pitch + it.pointer.delta.y / 10).coerceIn(-90f, 90f))
                    } else if (it.pointer.isRightButtonDown) {
                        offsetX.set(offsetX.value + it.pointer.delta.x / scale)
                        offsetY.set(offsetY.value + it.pointer.delta.y / scale)
                    }
                }
                .onWheelY {
                    val newZoom = (zoomState * if (it.pointer.scroll.y > 0) 1.1f else 0.9f).coerceIn(0.1f, 10f)
                    zoom.set(newZoom)
                }
        }

        Column {
            modifier.margin(Dimensions.PaddingMedium)
                .padding(Dimensions.PaddingMedium)
                .align(AlignmentX.Start, AlignmentY.Bottom)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundAccent,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall * 0.5f
                    )
                )
                .zLayer(1000)

            Text("Предпросмотр анимации") {
                modifier.font(remember { MsdfFont(ColorTheme.Fonts.MONOCRAFT, 13f) })
                    .textColor(ColorTheme.UI.WhiteReplacement)
                    .margin(Dimensions.PaddingMedium)
            }

            Row(Grow.Std) {
                Row(Grow.Std) {
                    modifier.background(RoundRectBackground(ColorTheme.UI.BackgroundDarker, Dimensions.PaddingMedium))
                        .border(
                            RoundRectBorder(
                                ColorTheme.UI.BackgroundAccent,
                                Dimensions.PaddingMedium,
                                Dimensions.PaddingSmall * 0.5f
                            )
                        )
                        .alignY(AlignmentY.Center)

                    Text(animations.getOrNull(animationId.use())?.name ?: "Пусто") {
                        modifier.font(remember { MsdfFont(ColorTheme.Fonts.MONOCRAFT, 14f) })
                            .textColor(ColorTheme.UI.WhiteReplacement)
                            .margin(Dimensions.PaddingMedium)
                            .width(Grow.Std)
                    }
                    Arrow(ArrowScope.ROTATION_DOWN) {
                        modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                            .margin(Dimensions.PaddingMedium)
                            .colors(
                                ColorTheme.UI.BackgroundAccent,
                                ColorTheme.UI.WhiteReplacement
                            ).alignY(AlignmentY.Center)
                            .onClick {
                                animationId.set((animationId.use() + 1) % animations.size)
                            }
                    }
                }
                Box {
                    modifier.padding(Dimensions.PaddingMedium)
                        .margin(start = Dimensions.PaddingMedium)
                        .background(RoundRectBackground(ColorTheme.UI.BackgroundDarker, Dimensions.PaddingMedium))
                        .border(
                            RoundRectBorder(
                                ColorTheme.UI.BackgroundAccent,
                                Dimensions.PaddingMedium,
                                Dimensions.PaddingSmall * 0.5f
                            )
                        )
                        .alignY(AlignmentY.Center)
                        .onClick {
                            animationEnabled.set(!animationEnabled.use())
                            animations.getOrNull(animationId.use())?.apply {
                                enabled = animationEnabled.use()
                                wrapMode = WrapMode.Loop
                            }
                        }

                    Image(if (animationEnabled.use()) icons.PAUSE else icons.START) {
                        modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    }
                }
            }
        }

        EditorButtons()

        val lineColor by animateColorAsState(
            if (isGridVisible.use()) ColorTheme.UI.BackgroundElements.withAlpha(0.65f)
            else ColorTheme.UI.BackgroundElements.withAlpha(0f)
        )
        modifier.background(
            GridBackground(
                Dimensions.PaddingExtraLarge,
                scale,
                offsetX.use(),
                offsetY.use(),
                Dimensions.PaddingSmall * 0.5f,
                lineColor
            )
        )
    }

    fun UiScope.EditorButtons() {
        Row {
            modifier.align(AlignmentX.Start, AlignmentY.Top)
                .zLayer(1000)

            Toggle(icons.AUTOCOMPLETE_CLASS, remember(false))
            Toggle(icons.LAYERS, remember(false))
            Toggle(icons.RECIPES, isGridVisible)
            Toggle(icons.RELOAD, remember(false))
        }
    }

    fun UiScope.Toggle(icon: ResourceLocation, selected: MutableStateValue<Boolean>) {
        Box {
            modifier.margin(Dimensions.PaddingMedium)
                .padding(Dimensions.PaddingMedium)

            modifier.onClick {
                selected.set(!selected.value)
            }

            val borderColor by animateColorAsState(
                if (selected.use()) ColorTheme.Accents.Main
                else ColorTheme.UI.BackgroundAccent
            )
            val backgroundColor by animateColorAsState(
                if (selected.use()) ColorTheme.UI.BackgroundElements.mix(
                    ColorTheme.Accents.Main, 0.5f
                ) else ColorTheme.UI.BackgroundElements
            )

            modifier.background(RoundRectBackground(backgroundColor, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        borderColor,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall * 0.5f
                    )
                )

            Image(icon) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .tint(if (selected.use()) Color.WHITE else ColorTheme.UI.WhiteReplacement)
            }
        }
    }

    context(scope: UiScope)
    fun Info() = with(scope) {
        Column {
            Text("Полигонов: ") { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
            Text("Анимаций: ") { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
            Text("Шейп-кеев: ") { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
        }
        Column {
            Text("${attachment.triangles}") { modifier.textColor(ColorTheme.UI.BackgroundAccent) }
            Text("${attachment.animations.size}") { modifier.textColor(ColorTheme.UI.BackgroundAccent) }
            Text("${attachment.shapekeys}") { modifier.textColor(ColorTheme.UI.BackgroundAccent) }
        }

    }
}

open class ModelModifier(surface: UiSurface) : GlCanvasModifier(surface) {
    var scale by property(1f)
    var yaw by property(0f)
    var pitch by property(0f)
    var offsetX by property(0f)
    var offsetY by property(0f)
    var showGrid by property(false)
}

fun ModelModifier.scale(factor: Float) = apply { this.scale = factor }
fun ModelModifier.yaw(yaw: Float) = apply { this.yaw = yaw }
fun ModelModifier.pitch(yaw: Float) = apply { this.pitch = yaw }
fun ModelModifier.offsetX(offsetX: Float) = apply { this.offsetX = offsetX }
fun ModelModifier.offsetY(offsetY: Float) = apply { this.offsetY = offsetY }
fun ModelModifier.showGrid(showGrid: Boolean) = apply { this.showGrid = showGrid }

interface ModelScope : ImageScope {
    override val modifier: ModelModifier
}

class ModelNode(parent: UiNode?, surface: UiSurface) : GlCanvasNode(parent, surface), ModelScope {
    override val modifier: ModelModifier = ModelModifier(surface)

    companion object {
        val factory: (UiNode, UiSurface) -> ModelNode = { parent, surface -> ModelNode(parent, surface) }
    }
}

@OptIn(ExperimentalContracts::class)
inline fun UiScope.Model(
    attachment: ModelAttachment,
    scopeName: String? = null, block: ModelScope.() -> Unit,
) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val modelNode = uiNode.createChild(scopeName, ModelNode::class, ModelNode.factory)
    modelNode.modifier.drawer = { modifier ->
        val modelConfig = modifier as ModelModifier
        GL33.glDepthFunc(GL33.GL_LEQUAL)
        val stack = PoseStack()

        val centerX = x + width / 2f
        val centerY = y + height / 2f

        val xPos = centerX + (modelConfig.offsetX * modelConfig.scale)
        val yPos = centerY + (modelConfig.offsetY * modelConfig.scale)

        stack.translate(xPos, yPos, 0f)

        val baseSize = min(width, height)
        val newScale = baseSize * modelConfig.scale

        stack.mulPoseMatrix(Matrix4f().scaling(newScale, -newScale, newScale))
        stack.mulPose(Quaternionf().rotateX(modelConfig.pitch * Mth.DEG_TO_RAD))
        stack.mulPose(Quaternionf().rotateY(modelConfig.yaw * Mth.DEG_TO_RAD))

        if (modifier.showGrid && false) {

            OpenGLUtils.renderGrid(stack, ColorTheme.UI.WhiteReplacement)
        }

        val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
        attachment.pipeline.render(
            RenderContext(
                stack, bufferSource,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
            )
        )
        bufferSource.endBatch()
    }
    modelNode.block()
}