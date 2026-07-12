package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.Time
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import org.joml.Quaternionf
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.GlCanvasModifier
import ru.hollowhorizon.hollowengine.client.kool.GlCanvasNode
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.client.models.internal.v2.walk
import ru.hollowhorizon.hollowengine.client.render.CUSTOM_IMGUI_LIGHT_0
import ru.hollowhorizon.hollowengine.client.render.CUSTOM_IMGUI_LIGHT_1
import ru.hollowhorizon.hollowengine.client.render.OpenGLUtils
import ru.hollowhorizon.hollowengine.client.utils.exists
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.utils.isValidRL
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.min

class ModelController {
    private var modelSwapJob: Job? = null

    val hasModel = mutableStateOf(false)
    val model = mutableStateOf("")
        .onChange { _, new ->
            if (new.isValidRL() && new.rl.exists() && HollowModelManager.supports(new.rl)) {
                val flow = HollowModelManager.getOrCreate(new.rl)
                modelSwapJob?.cancel()
                if (flow.value !== AnimatedModel.EMPTY) {
                    applyAttachment(ModelAttachment(flow, null))
                } else {
                    modelSwapJob = Minecraft.getInstance().coroutineScope.launch {
                        flow.filter { it !== AnimatedModel.EMPTY }.first()
                        applyAttachment(ModelAttachment(flow, null))
                    }
                }
            } else {
                hasModel.set(false)
                animations = emptyList()
                animationId.set(0)
            }
        }
    var attachment: ModelAttachment = ModelAttachment("hollowengine:models/entity/player_model.gltf")
    var animations = attachment.animations.map { it }
    var animationId = mutableStateOf(0)

    var scale: Float = 1f
        private set

    private val zoom = mutableStateOf(1f)
    val offsetX = mutableStateOf(0f)
    val offsetY = mutableStateOf(0f)
    val yaw = mutableStateOf(0f)
    val pitch = mutableStateOf(0f)
    val isBoundingBoxVisible = mutableStateOf(false)
    val isWireframeVisible = mutableStateOf(false)
    val isGridVisible = mutableStateOf(true)
    val isAutoRotateEnabled = mutableStateOf(false)

    fun clearModel() {
        model.set("")
    }

    private fun applyAttachment(newAttachment: ModelAttachment) {
        attachment = newAttachment
        animations = attachment.animations.map { it }
        animationId.set(0)
        hasModel.set(true)
    }

    context(scope: UiScope)
    operator fun invoke() = with(scope) {
        surface.triggerUpdate()
        val zoomState = zoom.use()
        val smoothedZoom = zoomState
        scale = smoothedZoom

        if (isAutoRotateEnabled.use()) {
            this@ModelController.yaw.set((this@ModelController.yaw.value + 20f * Time.deltaT) % 360f)
        }

        val yaw = yaw.use()
        val pitch = pitch.use()

        if (hasModel.use()) {
            Model(attachment, "Model-Renderer") {
                modifier.size(Grow.Std, Grow.Std)
                    .margin(Dimensions.PaddingMedium)
                    .showGrid(isGridVisible.use())
                    .showBoundingBox(isBoundingBoxVisible.use())
                    .showWireframe(isWireframeVisible.use())
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
        } else {
            Text("No model component") {
                modifier.align(AlignmentX.Center, AlignmentY.Center).textColor(ColorTheme.UI.WhiteReplacement)
            }
        }

        val animationPopup = remember { ItemPopupMenu<Unit>("animation-popup") }

        AnimationControlBar(animationPopup)

        animationPopup()

        EditorButtons()
        EditorInfo()

        val lineColor by animateColorAsState(
            if (isGridVisible.use()) ColorTheme.UI.BackgroundElements.withAlpha(0.65f)
            else ColorTheme.UI.BackgroundElements.withAlpha(0f)
        )
        modifier.background(
            GridBackground(
                Dimensions.PaddingExtraLarge * 5f,
                scale,
                offsetX.use(),
                offsetY.use(),
                Dimensions.PaddingSmall * 0.5f,
                lineColor
            )
        )
    }

    private fun UiScope.AnimationControlBar(popup: ItemPopupMenu<Unit>) {
        Column {
            val animation = animations.getOrNull(animationId.use())

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

            Text("hollowengine.gui.model_controller.animation_control".lang) {
                modifier.font(remember { MsdfFont(ColorTheme.Fonts.MONOCRAFT, 13f) })
                    .textColor(ColorTheme.UI.WhiteReplacement)
                    .margin(bottom = Dimensions.PaddingSmall)
            }

            Row(Grow.Std) {
                Box(Grow.Std, Grow.Std) {
                    val isHovered = remember { mutableStateOf(false) }
                    modifier
                        .background(
                            RoundRectBackground(
                                animateColorAsState(
                                    if (isHovered.use()) ColorTheme.UI.BackgroundDarker.mix(
                                        Color.WHITE,
                                        0.1f
                                    ) else ColorTheme.UI.BackgroundDarker
                                ).use(),
                                Dimensions.PaddingMedium
                            )
                        )
                        .border(
                            RoundRectBorder(
                                animateColorAsState(if (isHovered.use()) ColorTheme.Accents.Main else ColorTheme.UI.BackgroundAccent).use(),
                                Dimensions.PaddingMedium,
                                Dimensions.PaddingSmall * 0.5f
                            )
                        )
                        .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingSmall)
                        .alignY(AlignmentY.Center)
                        .onEnter { isHovered.set(true) }
                        .onExit { isHovered.set(false) }
                        .onClick {
                            popup.show(
                                Vec2f(it.screenPosition),
                                buildAnimationMenu(popup),
                                Unit
                            )
                        }

                    Row(Grow.Std) {
                        modifier.alignY(AlignmentY.Center)

                        Text(
                            animations.getOrNull(animationId.use())?.name
                                ?: "hollowengine.gui.model_controller.no_animations".lang
                        ) {
                            modifier.font(remember { MsdfFont(ColorTheme.Fonts.MONOCRAFT, 14f) })
                                .textColor(ColorTheme.UI.WhiteReplacement)
                                .alignY(AlignmentY.Center)
                                .width(Grow.Std)
                        }

                        Arrow(ArrowScope.ROTATION_DOWN) {
                            modifier.size(
                                Dimensions.PaddingMedium + Dimensions.PaddingNormal,
                                Dimensions.PaddingMedium + Dimensions.PaddingNormal
                            )
                                .margin(start = Dimensions.PaddingSmall)
                                .colors(
                                    ColorTheme.UI.BackgroundAccent,
                                    if (isHovered.use()) Color.WHITE else ColorTheme.UI.WhiteReplacement
                                )
                                .alignY(AlignmentY.Center)
                        }
                    }
                }

                Box {

                    val isHovered = remember { mutableStateOf(false) }
                    modifier
                        .margin(start = Dimensions.PaddingMedium)
                        .padding(Dimensions.PaddingNormal)
                        .background(
                            RoundRectBackground(
                                animateColorAsState(
                                    if (isHovered.use()) ColorTheme.UI.BackgroundDarker.mix(
                                        Color.WHITE,
                                        0.1f
                                    ) else ColorTheme.UI.BackgroundDarker
                                ).use(),
                                Dimensions.PaddingMedium
                            )
                        )
                        .border(
                            RoundRectBorder(
                                animateColorAsState(if (isHovered.use()) ColorTheme.Accents.Main else ColorTheme.UI.BackgroundAccent).use(),
                                Dimensions.PaddingMedium,
                                Dimensions.PaddingSmall * 0.5f
                            )
                        )
                        .alignY(AlignmentY.Center)
                        .onEnter { isHovered.set(true) }
                        .onExit { isHovered.set(false) }
                        .onClick {
                            animation?.apply {
                                weight = 1f - weight
                                wrapMode = WrapMode.Loop
                                //blendTime(0.6f, 0.6f)
                            }
                        }

//                    if (animation != null) Image(if (animation.weight == 1f) icons.PAUSE else icons.START) {
//                        modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
//                            .tint(animateColorAsState(if (animation.weight == 1f) Color.WHITE else ColorTheme.UI.WhiteReplacement).use())
//                    }
                }
            }

            if (animation != null && animation.duration > 0f && animation.weight > 0f) {
                val progress = (animation.time / animation.duration).coerceAtMost(1f)
                Box(Grow(progress), Dimensions.PaddingNormal) {
                    modifier.margin(top = Dimensions.PaddingMedium)
                        .background(
                            RoundRectBackground(
                                ColorTheme.CodeWindow.Selection,
                                Dimensions.PaddingSmall
                            )
                        )
                }
            }
        }
    }

    private fun buildAnimationMenu(menu: ItemPopupMenu<Unit>): SubMenuItem<Unit> =
        SubMenuItem("hollowengine.gui.model_controller.animations".lang) {
            if (animations.isEmpty()) {
                item("hollowengine.gui.model_controller.no_animations_available".lang, null) {}
            } else {
                animations.forEachIndexed { index, anim ->
                    item(anim.name) {
                        animationId.set(index)
                        menu.hide()
                    }
                }
            }
        }

    fun UiScope.EditorButtons() {
        Row {
            modifier.align(AlignmentX.Start, AlignmentY.Top)
                .zLayer(1000)

//            Toggle(
//                icons.AUTOCOMPLETE_CLASS,
//                isBoundingBoxVisible,
//                "hollowengine.gui.model_controller.bounding_box".lang
//            )
//            Toggle(icons.LAYERS, isWireframeVisible, "hollowengine.gui.model_controller.wireframe".lang)
//            Toggle(icons.RECIPES, isGridVisible, "hollowengine.gui.model_controller.grid".lang)
//            Toggle(icons.RELOAD, isAutoRotateEnabled, "hollowengine.gui.model_controller.auto_rotate".lang)
        }
    }

    fun UiScope.EditorInfo() {
        Row {
            modifier.align(AlignmentX.End, AlignmentY.Top)
                .margin(Dimensions.PaddingMedium)
                .padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundAccent,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall * 0.5f
                    )
                )
                .zLayer(1000)

            Column {
                Text("hollowengine.gui.model_controller.polygons".lang) { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
                Text("hollowengine.gui.model_controller.animations_count".lang) { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
                Text("hollowengine.gui.model_controller.shape_keys".lang) { modifier.textColor(ColorTheme.UI.WhiteReplacement) }
            }
            Column {
                Text("${attachment.triangles}") { modifier.textColor(ColorTheme.UI.BackgroundAccent) }
                Text("${attachment.animations.size}") { modifier.textColor(ColorTheme.UI.BackgroundAccent) }
                Text("${attachment.shapekeys}") { modifier.textColor(ColorTheme.UI.BackgroundAccent) }
            }
        }
    }

    fun UiScope.Toggle(icon: ResourceLocation, selected: MutableStateValue<Boolean>, tooltip: String? = null) {
        Box {
            modifier.margin(Dimensions.PaddingMedium)
                .padding(Dimensions.PaddingMedium)
            val tooltipState = remember { TooltipState(0.5) }
            modifier.hoverListener(tooltipState)

            modifier.onClick {
                selected.set(!selected.value)
                tooltipState.set(false)
            }

            val isHovered by modifier.hoverable()

            val borderColor by animateColorAsState(
                if (selected.use()) ColorTheme.Accents.Main.mulRgb(if (isHovered) 1.2f else 1f)
                else ColorTheme.UI.BackgroundAccent
            )
            val backgroundColor by animateColorAsState(
                if (selected.use() || isHovered) ColorTheme.UI.BackgroundElements
                    .mix(ColorTheme.Accents.Main, 0.5f)
                    .mulRgb(if (isHovered) 1.2f else 1f)
                else ColorTheme.UI.BackgroundElements
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

            tooltip?.let { text ->
                Tooltip(tooltipState) {
                    modifier.layout(CellLayout)
                        .zLayer(3000)
                        .margin(
                            start = modifier.marginStart + Dimensions.PaddingMedium,
                            top = modifier.marginTop + Dimensions.PaddingMedium
                        )
                        .background(UiRenderer { node ->
                            node.apply {
                                val backgroundColor = ColorTheme.UI.BackgroundElements
                                val border = ColorTheme.UI.WhiteReplacement

                                getUiPrimitives(UiSurface.LAYER_BACKGROUND).apply {
                                    localRoundRect(
                                        0f,
                                        0f,
                                        widthPx,
                                        heightPx,
                                        Dimensions.PaddingNormal.px,
                                        backgroundColor
                                    )
                                    localRoundRectBorder(
                                        0f,
                                        0f,
                                        widthPx,
                                        heightPx,
                                        Dimensions.PaddingNormal.px,
                                        Dimensions.PaddingSmall.px * 0.5f,
                                        border
                                    )
                                }
                            }
                        })

                    Text(text.lang) {
                        modifier.padding(sizes.smallGap)
                    }
                }
            }
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
    var showBoundingBox by property(false)
    var showWireframe by property(false)
}

fun ModelModifier.scale(factor: Float) = apply { this.scale = factor }
fun ModelModifier.yaw(yaw: Float) = apply { this.yaw = yaw }
fun ModelModifier.pitch(yaw: Float) = apply { this.pitch = yaw }
fun ModelModifier.offsetX(offsetX: Float) = apply { this.offsetX = offsetX }
fun ModelModifier.offsetY(offsetY: Float) = apply { this.offsetY = offsetY }
fun ModelModifier.showGrid(showGrid: Boolean) = apply { this.showGrid = showGrid }
fun ModelModifier.showBoundingBox(showBoundingBox: Boolean) = apply { this.showBoundingBox = showBoundingBox }
fun ModelModifier.showWireframe(showWireframe: Boolean) = apply { this.showWireframe = showWireframe }

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

        val xPos = centerX + modelConfig.offsetX * modelConfig.scale
        val yPos = centerY + modelConfig.offsetY * modelConfig.scale

        stack.translate(xPos, yPos, 0f)

        val baseSize = min(width, height)
        val newScale = baseSize * modelConfig.scale

        stack.scale(newScale, -newScale, newScale)
        stack.mulPose(Quaternionf().rotateX(modelConfig.pitch * Mth.DEG_TO_RAD))
        stack.mulPose(Quaternionf().rotateY(modelConfig.yaw * Mth.DEG_TO_RAD))

        if (modifier.showGrid && false) {
            OpenGLUtils.renderGrid(stack, ColorTheme.UI.WhiteReplacement)
        }

        val bounds = if (modelConfig.showBoundingBox) {
            attachment.calculateBounds()
        } else {
            null
        }

        if (modelConfig.showWireframe) {
            GL33.glPolygonMode(GL33.GL_FRONT_AND_BACK, GL33.GL_LINE)
        }
        val blends = attachment.materials.map { material ->
            val old = material.texture
            if (modelConfig.showWireframe) material.texture = "${HollowCore.MODID}:default_color_map".rl
            old
        }

        RenderSystem.setShaderLights(
            CUSTOM_IMGUI_LIGHT_0,
            CUSTOM_IMGUI_LIGHT_1
        )

        val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
        attachment.pipeline.render(
            RenderContext(
                stack, bufferSource,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY
            )
        )
        bufferSource.endBatch()

        if (modelConfig.showWireframe) {
            GL33.glPolygonMode(GL33.GL_FRONT_AND_BACK, GL33.GL_FILL)
            attachment.materials.forEachIndexed { i, material ->
                material.texture = blends.getOrNull(i) ?: return@forEachIndexed
            }
        }

        bounds?.let { (min, max) ->
            OpenGLUtils.renderBoundingBox(stack, min, max, ColorTheme.UI.WhiteReplacement.withAlpha(0.75f))
        }

        Lighting.setupFor3DItems()
    }
    modelNode.block()
}

fun ModelAttachment.calculateBounds(): Pair<Vec3f, Vec3f>? {
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var minZ = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var maxZ = Float.NEGATIVE_INFINITY
    var hasBounds = false
    val transformed = MutableVec3f()

    nodes.forEach { node ->
        node.walk().forEach { runtimeNode ->
            val matrix = runtimeNode.globalMatrix
            runtimeNode.definition.mesh?.primitives?.forEach { primitive ->
                val localBounds = primitive.localBounds ?: return@forEach
                val min = localBounds.first
                val max = localBounds.second

                fun update(x: Float, y: Float, z: Float) {
                    matrix.transform(Vec3f(x, y, z), 1f, transformed)
                    minX = kotlin.math.min(minX, transformed.x)
                    minY = kotlin.math.min(minY, transformed.y)
                    minZ = kotlin.math.min(minZ, transformed.z)
                    maxX = kotlin.math.max(maxX, transformed.x)
                    maxY = kotlin.math.max(maxY, transformed.y)
                    maxZ = kotlin.math.max(maxZ, transformed.z)
                }

                update(min.x, min.y, min.z)
                update(min.x, min.y, max.z)
                update(min.x, max.y, min.z)
                update(min.x, max.y, max.z)
                update(max.x, min.y, min.z)
                update(max.x, min.y, max.z)
                update(max.x, max.y, min.z)
                update(max.x, max.y, max.z)
                hasBounds = true
            }
        }
    }

    if (!hasBounds) return null
    return Vec3f(minX, minY, minZ) to Vec3f(maxX, maxY, maxZ)
}


