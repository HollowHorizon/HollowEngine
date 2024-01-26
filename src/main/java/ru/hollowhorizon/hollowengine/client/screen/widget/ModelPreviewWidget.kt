package ru.hollowhorizon.hollowengine.client.screen.widget

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderDispatcher
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.sounds.SoundManager
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.Blocks
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget
import ru.hollowhorizon.hc.client.utils.ScissorUtil.pop
import ru.hollowhorizon.hc.client.utils.ScissorUtil.push
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import kotlin.math.max
import kotlin.math.min


class ModelPreviewWidget(
    val npc: NPCEntity,
    private val originalX: Int,
    private val originalY: Int,
    private val originalWidth: Int,
    private val originalHeight: Int,
    private val parentWidth: Int,
    private val parentHeight: Int
) : HollowWidget(
    originalX,
    originalY,
    originalWidth,
    originalHeight,
    Component.translatable("hollowengine.model_preview")
) {
    private val buttons: MutableList<IconButton> = ArrayList()
    private val resetButton: IconButton
    private val fullscreenButton: IconButton
    private val bottomButtons: Array<IconButton>
    private val title: LabelWidget
    private var previewScale: Float = 0f
    private var previewYaw: Float = 0f
    private var previewPitch: Float = 0f
    private var previewX: Float = 0f
    private var previewY: Float = 0f
    var fullscreenness: Float = 0f
    private var fullscreen: Boolean = false
    private var transitioning: Boolean = false

    init {
        val tooltip = Button.OnTooltip { _, _, _, _ -> }
        resetButton =
            IconButton(
                0, 0,
                BUTTON_SIZE, BUTTON_SIZE,
                ResourceLocation("hollowengine:textures/gui/reload.png"),
                0,
                0,
                BUTTON_SIZE * 2,
                BUTTON_SIZE,
                BUTTON_SIZE,
                { this.resetPreview() },
                tooltip,
                Component.translatable("hollowengine.model_preview.reset")
            )
        buttons.add(resetButton)
        fullscreenButton =
            this.makeButton(
                1,
                {
                    transitioning = true
                    fullscreen = !fullscreen
                },
                tooltip,
                Component.translatable("hollowengine.model_preview.full_screen")
            )
        bottomButtons =
            arrayOf(
                this.makeButton(
                    4,
                    { doTurntable = !doTurntable },
                    tooltip,
                    Component.translatable("hollowengine.model_preview.rotate_model")
                ),
                this.makeButton(
                    3,
                    { renderFloor = !renderFloor },
                    tooltip,
                    Component.translatable("hollowengine.model_preview.display.plant")
                ),
                this.addButton(
                    PlayerIconButton(
                        0,
                        0,
                        MC.user.gameProfile,
                        { showPlayer = !showPlayer },
                        tooltip,
                        Component.translatable("hollowengine.model_preview.display.player")
                    )
                ),
                this.makeButton(
                    2,
                    { renderBoundingBoxes = !renderBoundingBoxes },
                    tooltip,
                    Component.translatable("hollowengine.model_preview.display.hitboxes")
                )
            )
        title =
            LabelWidget(
                0,
                0,
                MC.font,
                LabelWidget.AnchorX.CENTER,
                LabelWidget.AnchorY.CENTER,
                this.message
            )
        this.resetPreview()
        this.resetWidgetPositions()
    }

    protected fun makeButton(
        index: Int, press: Button.OnPress?, tooltip: Button.OnTooltip?, title: Component?
    ): IconButton {
        // x and y are controlled by resetWidgetPositions
        val button =
            IconButton(
                0,
                0,
                BUTTON_SIZE,
                BUTTON_SIZE,
                BUTTONS_TEXTURE,
                BUTTON_SIZE * index,
                0,
                BUTTONS_TEXTURE_HEIGHT,
                BUTTONS_TEXTURE_WIDTH,
                BUTTON_SIZE,
                press,
                tooltip,
                title
            )
        return this.addButton(button)
    }

    protected fun addButton(button: IconButton): IconButton {
        buttons.add(button)
        return button
    }

    fun resetPreview() {
        previewScale = 1.0f
        previewYaw = 135.0f
        previewPitch = -25.0f
        previewX = 0.0f
        previewY = 0.0f
    }

    protected fun resetWidgetPositions() {
        val top = y + BORDER_WIDTH
        val left = x + BORDER_WIDTH
        val right = x + width - BUTTON_SIZE - BORDER_WIDTH
        val bottom = y + height - BORDER_WIDTH - BUTTON_SIZE

        resetButton.x = left
        resetButton.y = top
        title.x = x + width / 2
        title.y = y + 12
        fullscreenButton.x = right
        fullscreenButton.y = top

        val length = bottomButtons.size
        val startX = x + width / 2 - length * BUTTON_SIZE / 2
        for (i in 0 until length) {
            val button = bottomButtons[i]
            button.x = startX + BUTTON_SIZE * i
            button.y = bottom
        }
    }

    override fun isValidClickButton(button: Int): Boolean {
        return button == 0 || button == 1
    }

    override fun playDownSound(p_230988_1_: SoundManager) {}

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (!this.isHovered) return false

        if (button == 0) {
            previewYaw += dragX.toFloat()
            previewPitch -= dragY.toFloat()
            if (previewPitch > 90.0f) {
                previewPitch = 90.0f
            } else if (previewPitch < -90.0f) {
                previewPitch = -90.0f
            }
            return true
        } else if (button == 1) {
            previewX += dragX.toFloat()
            previewY += dragY.toFloat()
            return true
        } else {
            return false
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, delta: Double): Boolean {
        previewScale = max((delta * 0.15f).toFloat() + previewScale, Float.MIN_VALUE)
        return true
    }

    @Suppress("deprecation")
    override fun renderButton(stack: PoseStack, mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (transitioning) {
            val speed = 0.03f
            if (fullscreen) {
                if (fullscreenness < 1.0f) {
                    fullscreenness += speed
                    if (fullscreenness > 1.0f) {
                        fullscreenness = 1.0f
                        transitioning = false
                    }
                } else {
                    transitioning = false
                }
            } else {
                if (fullscreenness > 0.0f) {
                    fullscreenness -= speed
                    if (fullscreenness < 0.0f) {
                        fullscreenness = 0.0f
                        transitioning = false
                    }
                } else {
                    transitioning = false
                }
            }
            x = Mth.lerp(fullscreenness, originalX.toFloat(), 0.0f).toInt()
            y = Mth.lerp(fullscreenness, originalY.toFloat(), 0.0f).toInt()
            width = Mth.ceil(Mth.lerp(fullscreenness, originalWidth.toFloat(), parentWidth.toFloat()))
            height = Mth.ceil(Mth.lerp(fullscreenness, originalHeight.toFloat(), parentHeight.toFloat()))
            this.resetWidgetPositions()
        }
        if (doTurntable) {
            previewYaw += 0.4f
        }

        val buffers = MC.renderBuffers().bufferSource()
        val bounds = npc.boundingBox
        val window = MC.window
        val guiScale = window.guiScale.toFloat()
        fill(stack, x, y, x + width, y + height, 0x66FFFFFF)
        push(
            x + BORDER_WIDTH,
            y + BORDER_WIDTH,
            width - BORDER_WIDTH * 2,
            height - BORDER_WIDTH * 2
        )
        fillGradient(stack, x, y, x + width, y + height, 0x66000000, -0x34000000)

        val renderStack = PoseStack()
        renderStack.translate(x.toDouble() + width / 2 + previewX, y.toDouble() + height / 2 + previewY, 0.0)
        val dm = min(width.toDouble(), height.toDouble()).toInt()
        val boundsW =
            if (bounds.minX > bounds.maxX) bounds.minX - bounds.maxX else bounds.maxX - bounds.minX
        val boundsH =
            if (bounds.minY > bounds.maxY) bounds.minY - bounds.maxY else bounds.maxY - bounds.minY
        val bm = max(boundsW, boundsH)
        val scale = (dm / (bm * guiScale)).toFloat() * previewScale

        if (renderFloor) renderFloor(scale)
        npc.renderEntity(
            x + width / 2 + previewX,
            y + height / 2 + previewY,
            scale, previewPitch, previewYaw
        )

        pop()

        stack.pushPose()
        stack.translate(0.0, 0.0, 1000.0)
        for (button in buttons) {
            button.render(stack, mouseX, mouseY, partialTicks)
        }
        title.render(stack, mouseX, mouseY, partialTicks)
        stack.popPose()
    }

    private fun LivingEntity.renderEntity(
        x: Float, y: Float, scale: Float, pitch: Float, yaw: Float,
    ) {

        val modelView = RenderSystem.getModelViewStack()
        modelView.pushPose()
        modelView.translate(x.toDouble(), y.toDouble(), 0.0)
        modelView.scale(1f, 1f, -1f)
        modelView.scale(scale, scale, scale)
        RenderSystem.applyModelViewMatrix()

        val stack = PoseStack()
        val quaternion = Vector3f.ZP.rotationDegrees(180.0f)
        val quaternion1 = Vector3f.XP.rotationDegrees(pitch)
        val quaternion2 = Vector3f.YP.rotationDegrees(yaw)
        quaternion1.mul(quaternion2)
        quaternion.mul(quaternion1)
        stack.mulPose(quaternion)
        quaternion1.conj()

        pushTransform()
        clearTransform()
        Lighting.setupForEntityInInventory()

        val hasName = isCustomNameVisible
        isCustomNameVisible = true

        val entityrenderermanager = Minecraft.getInstance().entityRenderDispatcher
        entityrenderermanager.overrideCameraOrientation(quaternion1)
        entityrenderermanager.setRenderShadow(false)
        entityrenderermanager.setRenderHitBoxes(renderBoundingBoxes)
        val renderBuffer = Minecraft.getInstance().renderBuffers().bufferSource()

        RenderSystem.runAsFancy {
            entityrenderermanager.render(
                this,
                0.0,
                0.0,
                0.0,
                0.0f,
                1.0f,
                stack,
                renderBuffer,
                15728880
            )
        }
        renderBuffer.endBatch()
        popTransform()

        entityrenderermanager.setRenderShadow(true)
        isCustomNameVisible = hasName
        modelView.popPose()

        RenderSystem.applyModelViewMatrix()
        Lighting.setupFor3DItems()
    }

    private fun renderFloor(scale: Float) {
        val renderStack = PoseStack()
        renderStack.translate(x.toDouble() + width / 2 + previewX, y.toDouble() + height / 2 + previewY, 0.0)

        renderStack.scale(1f, 1f, -1f)
        renderStack.scale(scale, scale, scale)

        val quaternion = Vector3f.ZP.rotationDegrees(180.0f)
        val quaternion1 = Vector3f.XP.rotationDegrees(previewPitch)
        val quaternion2 = Vector3f.YP.rotationDegrees(previewYaw)
        quaternion1.mul(quaternion2)
        quaternion.mul(quaternion1)
        renderStack.mulPose(quaternion)
        quaternion1.conj()

        if (renderFloor) {
            Lighting.setupForFlatItems()
            renderStack.pushPose();
            renderStack.translate(-0.5, -1.0, -0.5)
            MC.blockRenderer
                .renderSingleBlock(
                    Blocks.GRASS_BLOCK.defaultBlockState(),
                    renderStack,
                    MC.renderBuffers().bufferSource(),
                    0xF000F0,
                    OverlayTexture.NO_OVERLAY
                )
            MC.renderBuffers().bufferSource().endBatch()
            renderStack.popPose()
            Lighting.setupFor3DItems()
        }
    }

    override fun mouseClicked(p_231044_1_: Double, p_231044_3_: Double, p_231044_5_: Int): Boolean {
        for (button in this.buttons) {
            if (button.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_)) return true
        }
        return super.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_)
    }

    protected fun renderFire(
        entityPS: PoseStack,
        buffers: MultiBufferSource.BufferSource?,
        manager: EntityRenderDispatcher,
        yOff: Double
    ) {
        // The preview copy will never be on fire otherwise, so doing this directly is fine
        if (false) {
            entityPS.pushPose()
            entityPS.translate(0.0, yOff, 0.0)
            entityPS.mulPose(Vector3f.YP.rotationDegrees(manager.camera.yRot - previewYaw))
            //manager.renderFlame(entityPS, buffers, previewPuppet);
            entityPS.popPose()
        }
    }

    companion object {
        const val BORDER_WIDTH: Int = 2
        protected const val BUTTON_SIZE: Int = 20
        protected val BUTTONS_TEXTURE: ResourceLocation =
            ResourceLocation(HollowEngine.MODID, "textures/gui/preview_buttons.png")
        protected const val BUTTONS_TEXTURE_HEIGHT: Int = 64
        protected const val BUTTONS_TEXTURE_WIDTH: Int = 128
        protected val MC: Minecraft = Minecraft.getInstance()
        protected var renderBoundingBoxes: Boolean = false
        protected var renderFloor: Boolean = true
        protected var showPlayer: Boolean = false
        protected var doTurntable: Boolean = false
    }
}

class LivingAttributes(
    val prevRenderYawOffset: Float,
    val renderYawOffset: Float,
    val prevRotationYaw: Float,
    val rotationYaw: Float,
    val prevRotationPitch: Float,
    val rotationPitch: Float,
    val prevRotationYawHead: Float,
    val rotationYawHead: Float
)

val ATTRIBUTES = HashMap<LivingEntity, LivingAttributes>()

fun LivingEntity.pushTransform() {
    ATTRIBUTES[this] = LivingAttributes(
        yBodyRotO, yBodyRot, yRotO, yRot,
        xRotO, xRot, yHeadRotO, yHeadRot
    )
}

fun LivingEntity.clearTransform() {
    yBodyRotO = 0f
    yBodyRot = 0f
    yRotO = 0f
    yRot = 0f
    xRotO = 0f
    xRot = 0f
    yHeadRotO = 0f
    yHeadRot = 0f
}

fun LivingEntity.popTransform() {
    ATTRIBUTES[this]?.let {
        yBodyRotO = it.prevRenderYawOffset
        yBodyRot = it.renderYawOffset
        yRotO = it.prevRotationYaw
        yRot = it.rotationYaw
        xRotO = it.prevRotationPitch
        xRot = it.rotationPitch
        yHeadRotO = it.prevRotationYawHead
        yHeadRot = it.rotationYawHead
    }
    ATTRIBUTES.remove(this)
}