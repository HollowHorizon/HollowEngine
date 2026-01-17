package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import com.mojang.blaze3d.vertex.PoseStack
import de.fabmax.kool.modules.ui2.*
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

    val zoom = mutableStateOf(1f)
    val scrollState = ScrollState()
    val yaw = mutableStateOf(0f)
    val pitch = mutableStateOf(0f)

    context(scope: UiScope)
    operator fun invoke() = with(scope) {
        if (animationEnabled.use()) surface.triggerUpdate() // Анимация должна обновлять виджет
        val zoomState = zoom.use()
        val scale by animateSpringFloatAsState(zoomState)
        val yaw = yaw.use()
        val pitch = pitch.use()

        Model(attachment, scrollState, "Model-Renderer") {
            modifier.size(Grow.Std, Grow.Std)
                .margin(Dimensions.PaddingMedium)
                .scale(scale)
                .yaw(yaw).pitch(pitch)
                .onDrag {
                    if (it.pointer.isLeftButtonDown) {
                        this@ModelController.yaw.set((yaw + it.pointer.delta.x / 10) % 360)
                        this@ModelController.pitch.set((pitch + it.pointer.delta.y / 10).coerceIn(-90f, 90f))
                    } else if (it.pointer.isRightButtonDown) {
                        scrollState.scrollDpX(it.pointer.delta.x)
                        scrollState.scrollDpY(it.pointer.delta.y)
                    }
                }
                .onWheelY {
                    zoom.set((zoomState * if (it.pointer.scroll.y > 0) 1.1f else 0.9f).coerceIn(0.01f, 5f))
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
}

fun ModelModifier.scale(factor: Float) = apply { this.scale = factor }
fun ModelModifier.yaw(yaw: Float) = apply { this.yaw = yaw }
fun ModelModifier.pitch(yaw: Float) = apply { this.pitch = yaw }

interface ModelScope : ImageScope {
    override val modifier: ModelModifier
}

class ModelNode(parent: UiNode?, surface: UiSurface) : GlCanvasNode(parent, surface), ModelScope {
    override val modifier: ModelModifier = ModelModifier(surface)

    lateinit var scrollState: ScrollState

    companion object {
        val factory: (UiNode, UiSurface) -> ModelNode = { parent, surface -> ModelNode(parent, surface) }
    }
}

@OptIn(ExperimentalContracts::class)
inline fun UiScope.Model(
    attachment: ModelAttachment,
    scrollState: ScrollState,
    scopeName: String? = null, block: ModelScope.() -> Unit,
) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val modelNode = uiNode.createChild(scopeName, ModelNode::class, ModelNode.factory)
    modelNode.scrollState = scrollState
    modelNode.modifier.drawer = { modifier ->
        val modelConfig = modifier as ModelModifier
        GL33.glDepthFunc(GL33.GL_LEQUAL)
        val stack = PoseStack()
        val xOffset = x + width / 2 + modelNode.scrollState.xScrollDp.value * UiScale.measuredScale
        val yOffset = y + height + modelNode.scrollState.yScrollDp.value * UiScale.measuredScale
        stack.translate(xOffset, yOffset, 0f)
        val newScale = min(width, height) * modelConfig.scale
        stack.mulPoseMatrix(Matrix4f().scaling(newScale, -newScale, newScale))
        stack.mulPose(Quaternionf().rotateX(modelConfig.pitch * Mth.DEG_TO_RAD))
        stack.mulPose(Quaternionf().rotateY(modelConfig.yaw * Mth.DEG_TO_RAD))

        attachment.pipeline.render(
            RenderContext(
                stack, Minecraft.getInstance().renderBuffers().bufferSource(),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
            )
        )
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch()
    }
    modelNode.block()
}