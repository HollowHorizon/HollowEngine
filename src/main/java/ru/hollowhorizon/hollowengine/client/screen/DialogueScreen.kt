package ru.hollowhorizon.hollowengine.client.screen

import com.mojang.blaze3d.matrix.MatrixStack
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.IVertexBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.audio.SimpleSound
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.RenderType
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.vector.Vector3f
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.StringTextComponent
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.util.Alignment
import ru.hollowhorizon.hc.client.screens.util.WidgetPlacement
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hollowengine.client.screen.widget.dialogue.DialogueTextBox
import ru.hollowhorizon.hollowengine.client.sound.HSSounds
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.DialogueScene
import java.util.*
import kotlin.math.atan
import kotlin.math.pow

class DialogueScreen : HollowScreen(StringTextComponent("")) {
    var background: String? = null
    private val clickWaiter = Object()
    var textBox: DialogueTextBox? = null

    var currentName: ITextComponent = StringTextComponent("")
    var crystalAnimator = GuiAnimator.Reversed(0, 20, 1.5F) { x ->
        if (x < 0.5F) 4F * x * x * x
        else 1F - (-2 * x + 2.0).pow(3.0).toFloat() / 2F
    }
    private var hasChoice = false
    private var hasChoiceTicker = 0
    private var lastCount = 0
    private var delayTicks = -1
    var shouldClose = false
    var color: Int = 0xFFFFFFFF.toInt()
    var STATUS_ICON = "hollowengine:textures/gui/dialogues/status.png"
    var OVERLAY = "hollowengine:textures/gui/dialogues/overlay.png"
    var NAME_OVERLAY = "hollowengine:textures/gui/dialogues/name_overlay.png"
    var CHOICE_BUTTON = "hollowengine:textures/gui/dialogues/choice_button.png"
    val characters = ArrayList<LivingEntity>()
    val choices = ArrayList<String>()
    var currentChoice = 0
    private val choiceWaiter = Object()


    override fun init() {
        this.children.clear()
        this.buttons.clear()

        this.textBox = this.addButton(
            WidgetPlacement.configureWidget(
                ::DialogueTextBox, Alignment.BOTTOM_CENTER, 0, 0, this.width, this.height, 300, 50
            )
        )

        for ((i, choice) in choices.withIndex()) {

            this.addButton(
                WidgetPlacement.configureWidget(
                    { x, y, w, h ->
                        BaseButton(x, y, w, h, StringTextComponent(choice), { button ->

                            synchronized(this.choiceWaiter) {
                                this.choiceWaiter.notifyAll()
                            }

                            currentChoice = i

                            init()

                        }, CHOICE_BUTTON.toRL(), textColor = 0xFFFFFF, textColorHovered = 0xEDC213).apply {

                        }
                    }, Alignment.CENTER, 0, this.height / 3 - 25 * i, this.width, this.height, 320, 20
                )
            )
        }
        choices.clear()
    }

    @Suppress("DEPRECATION")
    override fun render(stack: MatrixStack, mouseX: Int, mouseY: Int, partialTick: Float) {

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
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F)
            Minecraft.getInstance().textureManager.bind(background!!.toRL())
            blit(stack, 0, 0, 0F, 0F, this.width, this.height, this.width, this.height)
        }
        drawCharacters(mouseX, mouseY)
        //drawImages(stack)

        RenderSystem.color4f(col.r, col.g, col.b, col.a)
        drawStatus(stack, partialTick)
        RenderSystem.color4f(1f, 1f, 1f, 1f)

        RenderSystem.enableAlphaTest()
        RenderSystem.enableBlend()
        RenderSystem.defaultAlphaFunc()
        RenderSystem.defaultBlendFunc()
        RenderSystem.color4f(col.r, col.g, col.b, col.a)
        Minecraft.getInstance().textureManager.bind(OVERLAY.toRL())
        blit(stack, 0, this.height - 55, 0F, 0F, this.width, 55, this.width, 55)
        RenderSystem.color4f(1F, 1F, 1F, 1F)

        super.render(stack, mouseX, mouseY, partialTick)

        if (!this.currentName.string.isEmpty()) drawNameBox(stack, col)

        if (shouldClose) onClose()

        if (delayTicks > 0) delayTicks--
        else if (delayTicks == 0) {
            delayTicks = -1
        }
    }

    private fun drawNameBox(stack: MatrixStack, col: RGBA) {
        RenderSystem.enableAlphaTest()
        RenderSystem.enableBlend()
        RenderSystem.defaultAlphaFunc()
        RenderSystem.defaultBlendFunc()
        RenderSystem.color4f(col.r, col.g, col.b, col.a)
        stack.pushPose()
        stack.translate(0.0, 0.0, 700.0)
        Minecraft.getInstance().textureManager.bind(NAME_OVERLAY.toRL())
        blit(
            stack,
            5,
            this.height - 73,
            0F,
            0F,
            this.font.width(this.currentName) + 10,
            15,
            this.font.width(this.currentName) + 10,
            15
        )
        RenderSystem.color4f(1F, 1F, 1F, 1F)

        this.font.drawShadow(
            stack, this.currentName, 10F, this.height - 60F - font.lineHeight, 0xFFFFFF
        )
        stack.popPose()
    }

    private fun drawStatus(stack: MatrixStack, partialTick: Float) {
        if (this.textBox?.complete == true) {
            Minecraft.getInstance().textureManager.bind(STATUS_ICON.toRL())
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
        if (this.textBox?.complete == true) notifyClick()
        else this.textBox?.complete = true
        return super.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_)
    }

    fun notifyClick() {
        mc.soundManager.play(SimpleSound.forUI(HSSounds.SLIDER_BUTTON, 1F, 1F))

        synchronized(this.clickWaiter) {
            this.clickWaiter.notifyAll()
        }
    }

    fun waitClick() {
        synchronized(this.clickWaiter) {
            this.clickWaiter.wait()
        }
    }

    @Suppress("DEPRECATION")
    private fun drawEntity(
        x: Float, y: Float, scale: Float, xRot: Float, yRot: Float, entity: LivingEntity, brightness: Float = 1.0F,
    ) {
        val f = atan((xRot / 40.0)).toFloat()
        val f1 = atan((yRot / 40.0)).toFloat()

        RenderSystem.pushMatrix()

        RenderHelper.turnBackOn()

        RenderSystem.translatef(x, y, 100.0f)

        RenderSystem.scalef(1.0f, 1.0f, -1.0f)

        val matrixstack = MatrixStack()

        matrixstack.scale(scale, scale, scale)
        val quaternion = Vector3f.ZP.rotationDegrees(180.0f)
        val quaternion1 = Vector3f.XP.rotationDegrees(f1 * 20.0f)
        quaternion.mul(quaternion1)
        matrixstack.mulPose(quaternion)
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
        val customName = entity.isCustomNameVisible
        entity.isCustomNameVisible = true
        val entityrenderermanager = Minecraft.getInstance().entityRenderDispatcher
        quaternion1.conj()
        entityrenderermanager.overrideCameraOrientation(quaternion1)
        entityrenderermanager.setRenderShadow(false)
        val renderBuffer = Minecraft.getInstance().renderBuffers().bufferSource()

        RenderSystem.runAsFancy {
            RenderSystem.enableAlphaTest()
            RenderSystem.enableBlend()
            RenderSystem.defaultAlphaFunc()
            RenderSystem.defaultBlendFunc()
            entityrenderermanager.render(
                entity, 0.0, 0.0, 0.0, 0.0f, 1.0f, matrixstack, { renderType ->
                    val builder = if (renderType is RenderType.Type) {
                        val rs: Optional<ResourceLocation> = renderType.state.textureState.texture
                        if (rs.isPresent) {
                            val newType: RenderType = RenderType.entityTranslucent(rs.get())
                            if (!newType.format().equals(renderType.format())) renderBuffer.getBuffer(renderType)
                            else renderBuffer.getBuffer(newType)
                        } else {
                            //HollowCore.LOGGER.info("If you see this, then there is an error with rendering that you should report as a bug.")
                            renderBuffer.getBuffer(renderType)
                        }
                    } else {
                        HollowCore.LOGGER.info("If you see this, then there is an error with rendering that you should report as a bug.")
                        renderBuffer.getBuffer(renderType)
                    }

                    return@render object : IVertexBuilder {
                        override fun vertex(x: Double, y: Double, z: Double): IVertexBuilder {
                            return builder.vertex(x, y, z)
                        }

                        override fun color(r: Int, g: Int, b: Int, a: Int): IVertexBuilder {
                            return builder.color((r * brightness).toInt(), (g * brightness).toInt(), (b * brightness).toInt(), a)
                        }

                        override fun uv(u: Float, v: Float): IVertexBuilder {
                            return builder.uv(u, v)
                        }

                        override fun overlayCoords(u: Int, v: Int): IVertexBuilder {
                            return builder.overlayCoords(u, v)
                        }

                        override fun uv2(u: Int, v: Int): IVertexBuilder {
                            return builder.uv2(u, v)
                        }

                        override fun normal(x: Float, y: Float, z: Float): IVertexBuilder {
                            return builder.normal(x, y, z)
                        }

                        override fun endVertex() {
                            builder.endVertex()
                        }

                    }
                }, 15728880
            )
        }

        RenderHelper.turnOff()
        RenderSystem.disableLighting()
        RenderSystem.enableDepthTest()
        RenderSystem.disableColorMaterial()
        renderBuffer.endBatch()
        entityrenderermanager.setRenderShadow(true)

        entity.isCustomNameVisible = customName
        entity.yBodyRot = f2
        entity.yRot = f3
        entity.xRot = f4
        entity.yHeadRotO = f5
        entity.yHeadRot = f6

        Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer()
        RenderSystem.disableRescaleNormal()
        RenderSystem.popMatrix()
    }

    fun update(scene: ru.hollowhorizon.hollowengine.common.scripting.dialogues.DialogueScene) {
        background = scene.background
        characters.clear()
        characters.addAll(scene.characters.map {
            val entity = EntityType.loadEntityRecursive(it.entityType, Minecraft.getInstance().level!!) { e -> e }!!

            if(it.isNPC) entity.deserializeNBT(it.entityType)

            return@map entity as LivingEntity
        })
        scene.actions.removeIf {
            it.call(this)
            true
        }
    }

    fun applyChoices(choices: Collection<String>): Int {
        this.choices.addAll(choices)
        init()
        synchronized(this.choiceWaiter) {
            this.choiceWaiter.wait()
        }
        return currentChoice
    }
}