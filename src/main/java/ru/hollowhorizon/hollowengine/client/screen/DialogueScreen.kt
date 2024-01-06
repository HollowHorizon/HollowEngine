package ru.hollowhorizon.hollowengine.client.screen

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.network.NetworkDirection
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.util.Alignment
import ru.hollowhorizon.hc.client.screens.util.WidgetPlacement
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hc.common.network.Packet
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.client.screen.widget.dialogue.DialogueTextBox
import ru.hollowhorizon.hollowengine.common.network.Container
import ru.hollowhorizon.hollowengine.common.network.MouseButton
import ru.hollowhorizon.hollowengine.common.network.MouseClickedPacket
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues.ApplyChoiceEvent
import kotlin.math.atan
import kotlin.math.pow

@HollowPacketV2(HollowPacketV2.Direction.TO_SERVER)
@Serializable
class OnChoicePerform(private val choice: Int) : HollowPacketV3<OnChoicePerform> {
    override fun handle(player: Player, data: OnChoicePerform) {
        MinecraftForge.EVENT_BUS.post(ApplyChoiceEvent(player, data.choice))
    }

}

@OnlyIn(Dist.CLIENT)
object DialogueScreen : HollowScreen("".mcText) {
    var background: String? = null
    var textBox: DialogueTextBox? = null
    var currentName = "".mcText
    val crystalAnimator by GuiAnimator.Reversed(0, 20, 1.5F) { x ->
        if (x < 0.5F) 4F * x * x * x
        else 1F - (-2 * x + 2.0).pow(3.0).toFloat() / 2F
    }
    var color: Int = 805000
    var STATUS_ICON = "hollowengine:gui/dialogues/status.png"
    var OVERLAY = "hollowengine:gui/dialogues/overlay.png"
    var NAME_OVERLAY = "hollowengine:gui/dialogues/name_overlay.png"
    var CHOICE_BUTTON = "hollowengine:textures/gui/dialogues/choice_button.png"
    val characters = LinkedHashSet<LivingEntity>()
    val choices = ArrayList<Component>()


    override fun init() {
        val text = textBox?.text
        this.children().clear()
        this.renderables.clear()

        this.textBox = addRenderableWidget(
            WidgetPlacement.configureWidget(
                ::DialogueTextBox, Alignment.BOTTOM_CENTER, 0, 0, this.width, this.height, 300, 50
            )
        )
        text?.let {
            textBox?.text = it
            textBox?.complete = true
        }

        choices.forEachIndexed { i, choice ->
            addRenderableWidget(
                WidgetPlacement.configureWidget(
                    { x, y, w, h ->
                        BaseButton(x, y, w, h, choice, {
                            this@DialogueScreen.init()
                            OnChoicePerform(i).send()
                        }, CHOICE_BUTTON.rl, textColor = 0xFFFFFF, textColorHovered = 0xEDC213)
                    }, Alignment.CENTER, 0, this.height / 3 - 25 * i, this.width, this.height, 320, 20
                )
            )
        }
        choices.clear()
    }

    override fun render(stack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        val col = color.toRGBA()

        renderBackground(stack)
        if (background != null) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F)
            bind(background!!.rl.namespace, background!!.rl.path)
            blit(stack, 0, 0, 0F, 0F, this.width, this.height, this.width, this.height)
        }
        drawCharacters(mouseX, mouseY)
        drawStatus(stack, col)

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(col.r, col.g, col.b, col.a)
        bind(OVERLAY.rl.namespace, OVERLAY.rl.path)
        blit(stack, 0, this.height - 55, 0F, 0F, this.width, 55, this.width, 55)
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F)

        super.render(stack, mouseX, mouseY, partialTick)

        if (this.currentName.string.isNotEmpty()) drawNameBox(stack, col)
    }

    private fun drawNameBox(stack: PoseStack, col: RGBA) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(col.r, col.g, col.b, col.a)
        stack.pushPose()
        stack.translate(0.0, 0.0, 700.0)
        bind(NAME_OVERLAY.rl.namespace, NAME_OVERLAY.rl.path)
        val size = this.font.width(this.currentName) + 10
        blit(stack, 5, this.height - 73, 0F, 0F, size, 15, size, 15)
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F)

        this.font.drawShadow(stack, this.currentName, 10F, this.height - 60F - font.lineHeight, 0xFFFFFF)
        stack.popPose()
    }

    private fun drawStatus(stack: PoseStack, col: RGBA) {
        if (this.textBox?.complete == true) {
            RenderSystem.setShaderColor(col.r, col.g, col.b, col.a)
            bind(STATUS_ICON.rl.namespace, STATUS_ICON.rl.path)
            blit(stack, this.width - 60 + crystalAnimator, this.height - 47, 0F, 0F, 40, 40, 40, 40)
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F)
        }
    }


    private fun drawCharacters(mouseX: Int, mouseY: Int) {
        val w = this.width / (characters.size + 1).toFloat()
        characters.forEachIndexed { i, entity ->
            val x = (i + 1) * w
            val y = this.height * 0.85F

            drawEntity(
                x, y, 70f,
                x - mouseX,
                y - this.height * 0.35f - mouseY,
                entity, 1.0F
            )
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, mouseCode: Int): Boolean {
        notifyClick()
        return super.mouseClicked(mouseX, mouseY, mouseCode)
    }

    override fun keyPressed(pKeyCode: Int, pScanCode: Int, pModifiers: Int): Boolean {
        when (pKeyCode) {
            GLFW.GLFW_KEY_ESCAPE -> onClose()
            GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER -> notifyClick()
        }
        return super.keyPressed(pKeyCode, pScanCode, pModifiers)
    }

    fun cleanup() {
        background = null
        textBox = null
        currentName = "".mcText
        color = 0xFFFFFFFF.toInt()
        STATUS_ICON = "hollowengine:gui/dialogues/status.png"
        OVERLAY = "hollowengine:gui/dialogues/overlay.png"
        NAME_OVERLAY = "hollowengine:gui/dialogues/name_overlay.png"
        CHOICE_BUTTON = "hollowengine:textures/gui/dialogues/choice_button.png"
        characters.clear()
        choices.clear()
    }

    fun notifyClick() {
        if (this.textBox?.complete == true) MouseClickedPacket(MouseButton.LEFT).send()
        else this.textBox?.complete = true
    }

    @Suppress("DEPRECATION")
    private fun drawEntity(
        x: Float, y: Float, scale: Float, xRot: Float, yRot: Float, entity: LivingEntity, brightness: Float = 1.0F,
    ) {
        val f = atan(xRot / 40.0).toFloat()
        val f1 = atan(yRot / 40.0).toFloat()

        val modelView = RenderSystem.getModelViewStack()
        modelView.pushPose()
        modelView.translate(x.toDouble(), y.toDouble(), 1050.0)
        modelView.scale(1f, 1f, -1f)
        RenderSystem.applyModelViewMatrix()

        val stack = PoseStack()
        stack.translate(0.0, 0.0, 1000.0)
        stack.scale(scale, scale, scale)
        val quaternion = Vector3f.ZP.rotationDegrees(180.0f)
        val quaternion1 = Vector3f.XP.rotationDegrees(f1 * 20.0f)
        quaternion.mul(quaternion1)
        stack.mulPose(quaternion)
        val f2: Float = entity.yBodyRot
        val f3: Float = entity.yRot
        val f4: Float = entity.xRot
        val f5: Float = entity.yHeadRotO
        val f6: Float = entity.yHeadRot
        entity.yBodyRot = 180.0f + f * 20.0f
        entity.yRot = 180.0f + f * 40.0f
        entity.xRot = -f1 * 20.0f
        entity.yHeadRot = entity.yRot
        entity.yHeadRotO = entity.yRot
        Lighting.setupForEntityInInventory()

        val customName = entity.isCustomNameVisible
        entity.isCustomNameVisible = true

        val entityrenderermanager = Minecraft.getInstance().entityRenderDispatcher
        quaternion1.conj()
        entityrenderermanager.overrideCameraOrientation(quaternion1)
        entityrenderermanager.setRenderShadow(false)
        val renderBuffer = Minecraft.getInstance().renderBuffers().bufferSource()

        RenderSystem.runAsFancy {
            entityrenderermanager.render(
                entity,
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

        entityrenderermanager.setRenderShadow(true)
        entity.isCustomNameVisible = customName
        entity.yBodyRot = f2
        entity.yRot = f3
        entity.xRot = f4
        entity.yHeadRotO = f5
        entity.yHeadRot = f6
        modelView.popPose()

        RenderSystem.applyModelViewMatrix()
        Lighting.setupFor3DItems()
    }

    fun updateChoices(choices: Collection<Component>) {
        this.choices.addAll(choices)
        init()
    }

    fun updateText(text: String) {
        this.textBox?.text = text
    }

    fun updateName(name: Component) {
        this.currentName = name
    }

    fun addEntity(entity: Int) {
        if (characters.any { it.id == entity }) return
        Minecraft.getInstance().level?.getEntity(entity)?.let { characters += it as LivingEntity }
    }

    fun removeEntity(entity: Int) {
        characters.removeIf { it.id == entity }
    }

    override fun shouldCloseOnEsc() = false
    override fun isPauseScreen() = false
}
