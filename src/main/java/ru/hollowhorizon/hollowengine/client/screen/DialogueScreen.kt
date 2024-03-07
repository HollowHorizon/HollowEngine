package ru.hollowhorizon.hollowengine.client.screen

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.common.MinecraftForge
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.api.IAutoScaled
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.util.WidgetPlacement
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.client.utils.nbt.ForEntity
import ru.hollowhorizon.hc.client.utils.nbt.ForTextComponent
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hc.common.ui.Alignment
import ru.hollowhorizon.hollowengine.client.screen.widget.dialogue.DialogueTextBox
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.network.MouseButton
import ru.hollowhorizon.hollowengine.common.network.MouseClickedPacket
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.NpcIcon
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

var CLIENT_OPTIONS = DialogueOptions()
    set(value) {
        field = value
        DialogueScreen.doInit()
    }

@OnlyIn(Dist.CLIENT)
object DialogueScreen : HollowScreen("".mcText), IAutoScaled {
    var canClose: Boolean = false
    private var textBox: DialogueTextBox? = null
    private val crystalAnimator by GuiAnimator.Reversed(0, 20, 30, Interpolation.BACK_OUT.function)

    fun doInit() = init()


    override fun init() {
        val lastText = textBox?.text ?: "".mcText
        this.children().clear()
        this.renderables.clear()

        this.textBox = addRenderableWidget(
            WidgetPlacement.configureWidget(
                ::DialogueTextBox, Alignment.BOTTOM_CENTER, 0, 0, this.width, this.height,
                (this.width * 0.8f).toInt(), 50
            )
        )
        textBox?.text = CLIENT_OPTIONS.text
        if (lastText == CLIENT_OPTIONS.text) textBox?.complete = true

        CLIENT_OPTIONS.choices.forEachIndexed { i, choice ->
            addRenderableWidget(
                WidgetPlacement.configureWidget(
                    { x, y, w, h ->
                        BaseButton(x, y, w, h, choice.mcTranslate, {
                            this@DialogueScreen.init()
                            OnChoicePerform(i).send()
                        }, CLIENT_OPTIONS.choiceButton.rl, textColor = 0xFFFFFF, textColorHovered = 0xEDC213)
                    }, Alignment.CENTER, 0, this.height / 3 - 25 * i, this.width, this.height, 320, 20
                )
            )
        }
        CLIENT_OPTIONS.choices.clear()

        Minecraft.getInstance().options.hideGui = true
    }

    override fun render(stack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (CLIENT_OPTIONS.background != null) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F)
            bind(CLIENT_OPTIONS.background!!.rl.namespace, CLIENT_OPTIONS.background!!.rl.path)
            blit(stack, 0, 0, 0F, 0F, this.width, this.height, this.width, this.height)
        }
        drawCharacters(mouseX, mouseY)

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        bind(CLIENT_OPTIONS.overlay.rl.namespace, CLIENT_OPTIONS.overlay.rl.path)
        blit(stack, 0, this.height - 55, 0F, 0F, this.width, 55, this.width, 55)

        super.render(stack, mouseX, mouseY, partialTick)

        drawStatus(stack)

        if (CLIENT_OPTIONS.name.string.isNotEmpty()) drawNameBox(stack)
    }

    private fun drawNameBox(stack: PoseStack) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        stack.pushPose()
        stack.translate(0.0, 0.0, 700.0)
        bind(CLIENT_OPTIONS.nameOverlay.rl.namespace, CLIENT_OPTIONS.nameOverlay.rl.path)
        val size = this.font.width(CLIENT_OPTIONS.name) + 10
        blit(stack, 5, this.height - 73, 0F, 0F, size, 15, size, 15)

        this.font.drawShadow(stack, CLIENT_OPTIONS.name, 10F, this.height - 60F - font.lineHeight, 0xFFFFFF)
        stack.popPose()
    }

    private fun drawStatus(stack: PoseStack) {
        if (this.textBox?.complete == true) {
            bind(CLIENT_OPTIONS.statusIcon.rl.namespace, CLIENT_OPTIONS.statusIcon.rl.path)
            blit(stack, this.width - 60 + crystalAnimator, this.height - 47, 0F, 0F, 40, 40, 40, 40)
        }
    }


    private fun drawCharacters(mouseX: Int, mouseY: Int) {
        val w = this.width / (CLIENT_OPTIONS.characters.size + 1f)
        CLIENT_OPTIONS.characters.filterIsInstance<LivingEntity>().forEachIndexed { i, entity ->
            val x = (i + 1) * w
            val y = this.height * 0.95F

            drawEntity(
                x, y, 100f,
                x - mouseX,
                y - this.height * 0.35f - mouseY,
                entity
            )
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, mouseCode: Int): Boolean {
        notifyClick()
        return super.mouseClicked(mouseX, mouseY, mouseCode)
    }

    override fun keyPressed(pKeyCode: Int, pScanCode: Int, pModifiers: Int): Boolean {
        when (pKeyCode) {
            GLFW.GLFW_KEY_ESCAPE -> if (shouldCloseOnEsc()) onClose()
            GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER -> notifyClick()
        }
        return super.keyPressed(pKeyCode, pScanCode, pModifiers)
    }

    override fun onClose() {
        super.onClose()
        cleanup()
        Minecraft.getInstance().options.hideGui = false
    }

    fun cleanup() {
        textBox = null
    }

    fun notifyClick() {
        if (this.textBox?.complete == true) MouseClickedPacket(MouseButton.LEFT).send()
        else this.textBox?.complete = true
    }

    @Suppress("DEPRECATION")
    private fun drawEntity(
        x: Float, y: Float, scale: Float, xRot: Float, yRot: Float, entity: LivingEntity,
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
        val isNpc = entity is NPCEntity
        val oldIcon = if (isNpc) entity[NPCCapability::class].icon else NpcIcon.EMPTY
        if (isNpc) entity[NPCCapability::class].icon = NpcIcon.EMPTY
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
        if (isNpc) entity[NPCCapability::class].icon = oldIcon
        modelView.popPose()

        RenderSystem.applyModelViewMatrix()
        Lighting.setupFor3DItems()
    }

    override fun shouldCloseOnEsc() = canClose
    override fun isPauseScreen() = false
}

@Serializable
data class DialogueOptions(
    var name: @Serializable(ForTextComponent::class) Component = "".mcText,
    var text: @Serializable(ForTextComponent::class) Component = "".mcText,
    var characters: ArrayList<@Serializable(ForEntity::class) Entity> = arrayListOf(),
    var choices: ArrayList<String> = arrayListOf(),
    var background: String? = null,
    var statusIcon: String = "hollowengine:gui/dialogues/status.png",
    var overlay: String = "hollowengine:gui/dialogues/overlay.png",
    var nameOverlay: String = "hollowengine:gui/dialogues/name_overlay.png",
    var choiceButton: String = "hollowengine:textures/gui/dialogues/choice_button.png"
)