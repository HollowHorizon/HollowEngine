package ru.hollowhorizon.hollowengine.client.screen.overlays

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hc.client.handlers.ClientTickHandler
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.screens.util.Anchor
import ru.hollowhorizon.hc.client.utils.drawScaled
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.rl
import kotlin.math.atan

object RecordingDriver {
    val texture = "hollowengine:textures/gui/icons/recording.png".rl
    private var startTime = 0
    private var pausedTime = 0
    var enable = false
        set(value) {
            field = value
            pausedTime += ClientTickHandler.ticks - startTime
        }

    fun draw(stack: PoseStack, x: Int, y: Int, partialTick: Float) {
        if (!enable) return

        val window = Minecraft.getInstance().window
        val progress = (pausedTime + ClientTickHandler.ticks - startTime + partialTick) / 20f

        RenderSystem.setShaderTexture(0, texture)
        Screen.blit(stack, x, y, 0f, 0f, 16, 16, 16, 16)

        val seconds = progress % 60f
        val minutes = ((progress / 60f) % 60f).toInt()
        val hours = ((progress / 3600f) % 60f).toInt()

        Minecraft.getInstance().font.drawScaled(
            stack, Anchor.START, Component.translatable("hollowengine.recoring_tooltip", hours, minutes, String.format("%.3f", seconds)), x + 20, y + 3, 0x0CA7f5, 1.2f
        )
        //hollowengine:models/entity/player_model.gltf
        renderEntityInInventory(
            window.guiScaledWidth - 20,
            60,
            30,
            window.guiScaledWidth / 2f,
            window.guiScaledHeight / 2f,
            Minecraft.getInstance().player!!
        )
    }

    fun resetTime() {
        pausedTime = 0
        startTime = ClientTickHandler.ticks
    }
}

fun renderEntityInInventory(
    pPosX: Int,
    pPosY: Int,
    pScale: Int,
    pMouseX: Float,
    pMouseY: Float,
    pLivingEntity: LivingEntity
) {
    val posestack = RenderSystem.getModelViewStack()
    posestack.pushPose()
    posestack.translate(pPosX.toDouble(), pPosY.toDouble(), 1050.0)
    posestack.scale(1.0f, 1.0f, -1.0f)
    RenderSystem.applyModelViewMatrix()
    val posestack1 = PoseStack()
    posestack1.translate(0.0, 0.0, 1000.0)
    posestack1.scale(pScale.toFloat(), pScale.toFloat(), pScale.toFloat())
    val quaternion = Vector3f.ZP.rotationDegrees(180.0f)
    posestack1.mulPose(quaternion)
    val f2 = pLivingEntity.yBodyRot
    val f3 = pLivingEntity.yRot
    val f4 = pLivingEntity.xRot
    val f5 = pLivingEntity.yHeadRotO
    val f6 = pLivingEntity.yHeadRot
    pLivingEntity.yBodyRot = 180.0f + 23.5f
    pLivingEntity.yRot = 180.0f + 45f
    //pLivingEntity.xRot = -angleYComponent * 20.0f
    pLivingEntity.yHeadRot = pLivingEntity.yRot
    pLivingEntity.yHeadRotO = pLivingEntity.yRot
    Lighting.setupForEntityInInventory()
    val entityrenderdispatcher = Minecraft.getInstance().entityRenderDispatcher
    entityrenderdispatcher.setRenderShadow(false)
    val pBuffer = Minecraft.getInstance().renderBuffers().bufferSource()
    RenderSystem.runAsFancy {
        entityrenderdispatcher.render(
            pLivingEntity,
            0.0,
            0.0,
            0.0,
            0.0f,
            1.0f,
            posestack1,
            pBuffer,
            15728880
        )
    }
    pBuffer.endBatch()
    entityrenderdispatcher.setRenderShadow(true)
    pLivingEntity.yBodyRot = f2
    pLivingEntity.yRot = f3
    pLivingEntity.xRot = f4
    pLivingEntity.yHeadRotO = f5
    pLivingEntity.yHeadRot = f6
    posestack.popPose()
    RenderSystem.applyModelViewMatrix()
    Lighting.setupFor3DItems()
}