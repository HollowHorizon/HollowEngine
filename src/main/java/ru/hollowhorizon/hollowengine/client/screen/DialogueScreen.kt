package ru.hollowhorizon.hollowengine.client.screen

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.util.Alignment
import ru.hollowhorizon.hc.client.screens.util.WidgetPlacement
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hollowengine.client.screen.widget.dialogue.DialogueTextBox
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.DialogueScene
import kotlin.math.atan
import kotlin.math.pow

class DialogueScreen : HollowScreen("".mcText) {
    private val clickWaiter = Object()
    var textBox: DialogueTextBox? = null

    var currentName = "".mcText
    var crystalAnimator = GuiAnimator.Reversed(0, 20, 1.5F) { x ->
        if (x < 0.5F) 4F * x * x * x
        else 1F - (-2 * x + 2.0).pow(3.0).toFloat() / 2F
    }
    val exampleEntity = NPCEntity(mc.level!!)
    private var hasChoice = false
    private var hasChoiceTicker = 0
    private var lastCount = 0
    private var delayTicks = -1
    var shouldClose = false
    var color: Int = 0xFFFFFFFF.toInt()
    var STATUS_ICON = "hollowengine:gui/dialogues/status.png"
    var OVERLAY = "hollowengine:gui/dialogues/overlay.png"
    var NAME_OVERLAY = "hollowengine:gui/dialogues/name_overlay.png"
    var CHOICE_BUTTON = "hollowengine:textures/gui/dialogues/choice_button.png"
    val characters = ArrayList<LivingEntity>()
    val choices = ArrayList<String>()
    var currentChoice = 0
    private val choiceWaiter = Object()

    companion object {
        @JvmField
        var background: String? = null
        @JvmField
        var textBox: DialogueTextBox? = null
        @JvmField
        var currentName = "".mcText
        @JvmField
        var crystalAnimator = GuiAnimator.Reversed(0, 20, 1.5F) { x ->
            if (x < 0.5F) 4F * x * x * x
            else 1F - (-2 * x + 2.0).pow(3.0).toFloat() / 2F
        }
        @JvmField
        var shouldClose = false
        @JvmField
        var color: Int = 0xFFFFFFFF.toInt()
        @JvmField
        var STATUS_ICON = "hollowengine:gui/dialogues/status.png"
        @JvmField
        var OVERLAY = "hollowengine:gui/dialogues/overlay.png"
        @JvmField
        var NAME_OVERLAY = "hollowengine:gui/dialogues/name_overlay.png"
        @JvmField
        var CHOICE_BUTTON = "hollowengine:textures/gui/dialogues/choice_button.png"
        @JvmField
        val characters = ArrayList<LivingEntity>()
        @JvmField
        val choices = ArrayList<String>()
        @JvmField
        var currentChoice = 0

        private var hasChoice = false
        private var hasChoiceTicker = 0
        private var lastCount = 0
        private var delayTicks = -1
    }

    override fun init() {
        this.children().clear()
        this.renderables.clear()

       textBox = this.addRenderableWidget(
            WidgetPlacement.configureWidget(
                ::DialogueTextBox, Alignment.BOTTOM_CENTER, 0, 0, this.width, this.height, 300, 50
            )
        )

        for ((i, choice) in choices.withIndex()) {

            this.addRenderableWidget(
                WidgetPlacement.configureWidget(
                    { x, y, w, h ->
                        BaseButton(x, y, w, h, choice.mcText, {
                            this@DialogueScreen.init()

                            currentChoice = i

                            synchronized(choiceWaiter) {
                                choiceWaiter.notifyAll()
                            }

                        }, CHOICE_BUTTON.rl, textColor = 0xFFFFFF, textColorHovered = 0xEDC213).apply {

                        }
                    }, Alignment.CENTER, 0, this.height / 3 - 25 * i, this.width, this.height, 320, 20
                )
            )
        }
        choices.clear()
    }

    @Suppress("DEPRECATION")
    override fun render(stack: PoseStack, mouseX: Int, mouseY: Int, partialTick: Float) {

        //Я не знаю почему при выборе варианта ответа скипается одно сообщение, но если в этот момент запустить ожидание клика, а после кликнуть, то все работает как надо
        //Очень странный костыль... Когда-нибудь я разберусь?
        if (hasChoice) {
            if (hasChoiceTicker > 10) {
                notifyClick()
                hasChoiceTicker = 0
            } else {
                hasChoiceTicker++
            }
        }

        val col = color.toRGBA()

        renderBackground(stack)
        if (background != null) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F)
            bind(background!!.rl.namespace, background!!.rl.path)
            blit(stack, 0, 0, 0F, 0F, this.width, this.height, this.width, this.height)
        }
        drawCharacters(mouseX, mouseY)
        //drawImages(stack)

        RenderSystem.setShaderColor(col.r, col.g, col.b, col.a)
        drawStatus(stack, partialTick)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(col.r, col.g, col.b, col.a)
        bind(OVERLAY.rl.namespace, OVERLAY.rl.path)
        blit(stack, 0, this.height - 55, 0F, 0F, this.width, 55, this.width, 55)
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F)

        super.render(stack, mouseX, mouseY, partialTick)

        if (currentName.string.isNotEmpty()) drawNameBox(stack, col)

        if (shouldClose) onClose()

        if (delayTicks > 0) delayTicks--
        else if (delayTicks == 0) {
            delayTicks = -1
        }

        //drawEntity(100f, 100f, 70f, 100f - mouseX, 100f - this.height * 0.35f - mouseY, exampleEntity, 1.0F)
    }

    private fun drawNameBox(stack: PoseStack, col: RGBA) {
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShaderColor(col.r, col.g, col.b, col.a)
        stack.pushPose()
        stack.translate(0.0, 0.0, 700.0)
        bind(NAME_OVERLAY.rl.namespace, NAME_OVERLAY.rl.path)
        blit(
            stack,
            5,
            this.height - 73,
            0F,
            0F,
            this.font.width(currentName) + 10,
            15,
            this.font.width(currentName) + 10,
            15
        )
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F)

        this.font.drawShadow(
            stack, currentName, 10F, this.height - 60F - font.lineHeight, 0xFFFFFF
        )
        stack.popPose()
    }

    private fun drawStatus(stack: PoseStack, partialTick: Float) {
        if (textBox?.complete == true) {
            bind(STATUS_ICON.rl.namespace, STATUS_ICON.rl.path)
            blit(stack, this.width - 60 + crystalAnimator.value, this.height - 47, 0F, 0F, 40, 40, 40, 40)
            crystalAnimator.update(partialTick)
        }
    }


    private fun drawCharacters(mouseX: Int, mouseY: Int) {
        val count = characters.size
        val w = this.width / (count + 1)

        for (i in 0 until count) {
            val character = characters[i]

            var x = (i + 1F) * w
            var y = this.height * 0.85F

            //var scale = if (scene.currentCharacter == character) 72F else 70F
            //scale *= character.scale
            //x += character.translate.x
            //y += character.translate.y
            //val brightness = if (scene.currentCharacter == character) 1F else 0.5F
            //character.type.customName = character.mcName
            drawEntity(
                x,
                y,
                70f,
                x - mouseX,
                y - this.height * 0.35f - mouseY,
                character,
                1.0F
            )
        }

        lastCount = count
    }

    override fun mouseClicked(p_231044_1_: Double, p_231044_3_: Double, p_231044_5_: Int): Boolean {
        if (textBox?.complete == true) notifyClick()
        else textBox?.complete = true
        return super.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_)
    }

    fun notifyClick() {
        //mc.soundManager.play(SimpleSoundInstance.forUI(HSSounds.SLIDER_BUTTON, 1F, 1F))

        synchronized(clickWaiter) {
            clickWaiter.notifyAll()
        }
    }

    fun waitClick() {
        synchronized( clickWaiter) {
            clickWaiter.wait()
        }
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

    fun update(scene: DialogueScene) {
        background = scene.background
        characters.clear()
        characters.addAll(scene.characters.map {
            return@map it.entityType
        })
        scene.actions.removeIf {
            it.call(this)
            true
        }
    }

    fun applyChoices(choices: MutableCollection<String>): Int {
        choices.addAll(choices)
        init()
        synchronized(choiceWaiter) {
            choiceWaiter.wait()
        }
        return currentChoice
    }

    override fun isPauseScreen(): Boolean {
        return false
    }
}